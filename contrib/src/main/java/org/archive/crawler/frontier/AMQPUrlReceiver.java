/*
 *  This file is part of the Heritrix web crawler (crawler.archive.org).
 *
 *  Licensed to the Internet Archive (IA) by one or more individual
 *  contributors.
 *
 *  The IA licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.archive.crawler.frontier;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.httpclient.URIException;
import org.archive.crawler.event.AMQPUrlReceivedEvent;
import org.archive.crawler.event.CrawlStateEvent;
import org.archive.crawler.postprocessor.CandidatesProcessor;
import org.archive.modules.CrawlURI;
import org.archive.modules.SchedulingConstants;
import org.archive.modules.extractor.Hop;
import org.archive.modules.extractor.LinkContext;
import org.archive.net.UURI;
import org.archive.net.UURIFactory;
import org.archive.spring.KeyedProperties;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ApplicationListener;
import org.springframework.context.Lifecycle;

import com.rabbitmq.client.AMQP.BasicProperties;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.Consumer;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;
import com.rabbitmq.client.ShutdownSignalException;

/**
 * @contributor nlevitt
 */
public class AMQPUrlReceiver
	implements Lifecycle, ApplicationContextAware, ApplicationListener<CrawlStateEvent> {

    @SuppressWarnings("unused")
    private static final long serialVersionUID = 2L;

    private static final Logger logger = 
            Logger.getLogger(AMQPUrlReceiver.class.getName());

    public static final String A_RECEIVED_FROM_AMQP = "receivedFromAMQP";

    protected ApplicationContext appCtx;
    public void setApplicationContext(ApplicationContext appCtx) throws BeansException {
        this.appCtx = appCtx;
    }

    protected CandidatesProcessor candidates;
    public CandidatesProcessor getCandidates() {
        return candidates;
    }
    /**
     * Received urls are run through the supplied CandidatesProcessor, which
     * checks scope and schedules the urls. By default the crawl job's normal
     * candidates processor is autowired in, but a different one can be
     * configured if special scoping rules are desired.
     */
    @Autowired
    public void setCandidates(CandidatesProcessor candidates) {
        this.candidates = candidates;
    }

    protected String amqpUri = "amqp://guest:guest@localhost:5672/%2f";
    public String getAmqpUri() {
        return this.amqpUri;
    }
    public void setAmqpUri(String uri) {
        this.amqpUri = uri;
    }

    protected String exchange = "umbra";
    public String getExchange() {
        return exchange;
    }
    public void setExchange(String exchange) {
        this.exchange = exchange;
    }

    protected String queueName = "requests";
    public String getQueueName() {
        return queueName;
    }
    public void setQueueName(String queueName) {
        this.queueName = queueName;
    }

    protected boolean isRunning = false; 
    @Override
    public boolean isRunning() {
        return isRunning;
    }

    private boolean durable = false;
    public boolean isDurable() {
        return durable;
    }
    /** Should be queues be marked as durable? */
    public void setDurable(boolean durable) {
        this.durable = durable;
    }

    private boolean autoDelete = true;
    public boolean isAutoDelete() {
        return autoDelete;
    }
    /** Should be queues be marked as auto-delete? */
    public void setAutoDelete(boolean autoDelete) {
        this.autoDelete = autoDelete;
    }
    
    private boolean forceFetch = false;
    public boolean isForceFetch() {
        return forceFetch;
    }
    public void setForceFetch(boolean forceFetch) {
        this.forceFetch = forceFetch;
    }

    /**
     * The maximum prefetch count to use, meaning the maximum number of messages
     * to be consumed without being acknowledged. Using 'null' would specify
     * there should be no upper limit (the default).
     */
    private Integer prefetchCount = 1000;

    private transient Lock lock = new ReentrantLock(true);

    private transient boolean pauseConsumer = false;
    private transient String consumerTag = null;

    private class StarterRestarter extends Thread {

        public StarterRestarter(String name) {
            super(name);
        }

        @Override
        public void run() {
            while (!Thread.interrupted()) {
                try {
                    lock.lockInterruptibly();
                    logger.finest("Checking consumerTag=" + consumerTag + " and pauseConsumer=" + pauseConsumer);
                    try {
                        if (consumerTag == null && !pauseConsumer) {
                            // start up again
                            try {
                                startConsumer();
                            } catch (IOException e) {
                                logger.log(Level.SEVERE, "problem starting AMQP consumer (will try again after 10 seconds)", e);
                            }
                        }

                        if (consumerTag != null && pauseConsumer) {
                            try {
                                if (consumerTag != null) {
                                    logger.info("Attempting to cancel URLConsumer with consumerTag=" + consumerTag);
                                    channel().basicCancel(consumerTag);
                                    consumerTag = null;
                                    logger.info("Cancelled URLConsumer.");
                                }
                            } catch (IOException e) {
                                logger.log(Level.SEVERE, "problem cancelling AMQP consumer (will try again after 10 seconds)", e);
                            }
                        }

                    } finally {
                        lock.unlock();
                    }

                    Thread.sleep(10 * 1000);
                } catch (InterruptedException e) {
                    return;
                }
            }
        }

        public void startConsumer() throws IOException {
            Consumer consumer = new UrlConsumer(channel());
            channel().exchangeDeclare(getExchange(), "direct", true);
            channel().queueDeclare(getQueueName(), durable,
                    false, autoDelete, null);
            channel().queueBind(getQueueName(), getExchange(), getQueueName());
            if (prefetchCount != null)
                channel().basicQos(prefetchCount);
            consumerTag = channel().basicConsume(getQueueName(), false, consumer);
            logger.info("started AMQP consumer uri=" + getAmqpUri() + " exchange=" + getExchange() + " queueName=" + getQueueName() + " consumerTag=" + consumerTag);
        }
    }

    transient private StarterRestarter starterRestarter;

    @Override
    public void start() {
        lock.lock();
        try {
            // spawn off a thread to start up the amqp consumer, and try to restart it if it dies 
            if (!isRunning) {
                starterRestarter = new StarterRestarter(AMQPUrlReceiver.class.getSimpleName() + "-starter-restarter");
                try {
                    // try to synchronously start the consumer right now, so
                    // that the queue is bound before crawling starts
                    starterRestarter.startConsumer();
                } catch (IOException e) {
                    logger.log(Level.SEVERE, "problem starting AMQP consumer (will try again soon)", e);
                }
                starterRestarter.start();
            }
            isRunning = true;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void stop() {
        lock.lock();
        try {
            logger.info("shutting down");
            if (starterRestarter != null && starterRestarter.isAlive()) {
                starterRestarter.interrupt();
                try {
                    starterRestarter.join();
                } catch (InterruptedException e) {
                }
            }
            starterRestarter = null;

            if (connection != null && connection.isOpen()) {
                try {
                    connection.close();
                } catch (IOException e) {
                    logger.log(Level.SEVERE, "problem closing AMQP connection", e);
                }
            }
            connection = null;
            channel = null;
            isRunning = false;
        } finally {
            lock.unlock();
        }
    }

    transient protected Connection connection = null;
    transient protected Channel channel = null;

    protected Connection connection() throws IOException {
        lock.lock();
        try {
            if (connection != null && !connection.isOpen()) {
                logger.warning("connection is closed, creating a new one");
                connection = null;
            }

            if (connection == null) {
                ConnectionFactory factory = new ConnectionFactory();
                try {
                    factory.setUri(getAmqpUri());
                } catch (Exception e) {
                    throw new IOException("problem with AMQP uri " + getAmqpUri(), e);
                }
                connection = factory.newConnection();
            }

            return connection;
        } finally {
            lock.unlock();
        }
    }

    protected Channel channel() throws IOException {
        lock.lock();
        try {
            if (channel != null && !channel.isOpen()) {
                logger.warning("channel is not open, creating a new one");
                channel = null;
            }

            if (channel == null) {
                channel = connection().createChannel();
            }

            return channel;
        } finally {
            lock.unlock();
        }
    }
    
    protected static final Set<String> REQUEST_HEADER_BLACKLIST = new HashSet<String>(Arrays.asList(
            "accept-encoding", "upgrade-insecure-requests", "host", "connection"));

    // XXX should we be using QueueingConsumer because of possible blocking in
    // frontier.schedule()?
    // "Note: all methods of this interface are invoked inside the Connection's
    // thread. This means they a) should be non-blocking and generally do little
    // work, b) must not call Channel or Connection methods, or a deadlock will
    // ensue. One way of ensuring this is to use/subclass QueueingConsumer."
    protected class UrlConsumer extends DefaultConsumer {
        public UrlConsumer(Channel channel) {
            super(channel);
        }

        @Override
        public void handleDelivery(String consumerTag, Envelope envelope,
                BasicProperties properties, byte[] body) throws IOException {
            String decodedBody;
            try {
                decodedBody = new String(body, "UTF-8");
            } catch (UnsupportedEncodingException e) {
                throw new RuntimeException(e); // can't happen
            }
            JSONObject jo = new JSONObject(decodedBody);

            if ("GET".equals(jo.getString("method"))) {
                try {
                    CrawlURI curi = makeCrawlUri(jo);
                    KeyedProperties.clearAllOverrideContexts();
                    candidates.runCandidateChain(curi, null);
                    appCtx.publishEvent(new AMQPUrlReceivedEvent(AMQPUrlReceiver.this, curi));
                } catch (URIException e) {
                    logger.log(Level.WARNING,
                            "problem creating CrawlURI from json received via AMQP "
                                    + decodedBody, e);
                } catch (JSONException e) {
                    logger.log(Level.SEVERE,
                            "problem creating CrawlURI from json received via AMQP "
                                    + decodedBody, e);
                } catch (Exception e) {
                    logger.log(Level.SEVERE,
                            "Unanticipated problem creating CrawlURI from json received via AMQP "
                                    + decodedBody, e);
                }
            } else {
                logger.info("ignoring url with method other than GET - "
                        + decodedBody);
            }

            logger.finest("Now ACKing: " + decodedBody);
            this.getChannel().basicAck(envelope.getDeliveryTag(), false);
        }

        @Override
        public void handleShutdownSignal(String consumerTag,
                ShutdownSignalException sig) {
            if (!sig.isInitiatedByApplication()) {
                logger.log(Level.SEVERE, "amqp channel/connection unexpectedly shut down consumerTag=" + consumerTag, sig);
            } else {
                logger.info("amqp channel/connection shut down consumerTag=" + consumerTag);
            }
            AMQPUrlReceiver.this.consumerTag = null;
        }

        // {
        //  "headers": {
        //   "Referer": "https://archive.org/",
        //   "User-Agent": "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Ubuntu Chromium/32.0.1700.102 Chrome/32.0.1700.102 Safari/537.36",
        //   "Accept": "image/webp,*/*;q=0.8"
        //  },
        //  "url": "https://analytics.archive.org/0.gif?server_ms=256&server_name=www19.us.archive.org&service=ao&loadtime=358&timediff=-8&locale=en-US&referrer=-&version=2&count=9",
        //  "method": "GET"
        // }
        protected CrawlURI makeCrawlUri(JSONObject jo) throws URIException,
                JSONException {
            JSONObject joHeaders = jo.getJSONObject("headers");

            UURI uuri = UURIFactory.getInstance(jo.getString("url"));
            UURI via = UURIFactory.getInstance(jo.getString("parentUrl"));

            JSONObject parentUrlMetadata = jo.getJSONObject("parentUrlMetadata");
            String parentHopPath = parentUrlMetadata.getString("pathFromSeed");
            String hop = jo.optString("hop", Hop.INFERRED.getHopString());
            String hopPath = parentHopPath + hop;

            CrawlURI curi = new CrawlURI(uuri, hopPath, via, LinkContext.INFERRED_MISC);

            populateHeritableMetadata(curi, parentUrlMetadata);

            // set the http headers from the amqp message
            Map<String, String> customHttpRequestHeaders = new HashMap<String, String>();
            for (Object key: joHeaders.keySet()) {
                String k = key.toString();
                if (!k.startsWith(":") && !REQUEST_HEADER_BLACKLIST.contains(k)) {
                    customHttpRequestHeaders.put(k, joHeaders.getString(key.toString()));
                }
            }
            curi.getData().put("customHttpRequestHeaders", customHttpRequestHeaders);

            /*
             * Crawl job must be configured to use
             * HighestUriQueuePrecedencePolicy to ensure these high priority
             * urls really get crawled ahead of others. See
             * https://webarchive.jira.com/wiki/display/Heritrix/Precedence+
             * Feature+Notes
             */
            if (Hop.INFERRED.getHopString().equals(curi.getLastHop())) {
                curi.setSchedulingDirective(SchedulingConstants.HIGH);
                curi.setPrecedence(1);
            }

            curi.setForceFetch(forceFetch || jo.optBoolean("forceFetch"));
            curi.setSeed(jo.optBoolean("isSeed"));

            curi.getAnnotations().add(A_RECEIVED_FROM_AMQP);

            return curi;
        }

        // set the heritable data from the parent url, passed back to us via amqp
        // XXX brittle, only goes one level deep, and only handles strings and arrays, the latter of which it converts to a Set.
        // 'heritableData': {'source': 'https://facebook.com/whitehouse/', 'heritable': ['source', 'heritable']}
        @SuppressWarnings("unchecked")
        protected void populateHeritableMetadata(CrawlURI curi, JSONObject parentUrlMetadata) {
            JSONObject heritableData = parentUrlMetadata.getJSONObject("heritableData");
            for (String key: (Set<String>) heritableData.keySet()) {
                Object value = heritableData.get(key);
                if (value instanceof JSONArray) {
                    Set<String> valueSet = new HashSet<String>();
                    JSONArray arr = ((JSONArray) value);
                    for (int i = 0; i < arr.length(); i++) {
                        valueSet.add(arr.getString(i));
                    }
                    curi.getData().put(key, valueSet);
                } else {
                    curi.getData().put(key, heritableData.get(key));
                }
            }
        }
    }

    @Override
    public void onApplicationEvent(CrawlStateEvent event) {
        switch(event.getState()) {
        case PAUSING: case PAUSED:
            if (!this.pauseConsumer) {
                logger.info("Requesting a pause of the URLConsumer...");
                this.pauseConsumer = true;
            }
            break;

        case RUNNING:
            if (this.pauseConsumer) {
                logger.info("Requesting unpause of the URLConsumer...");
                this.pauseConsumer = false;
            }
            break;

        default:
        }
    }
}
