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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.httpclient.URIException;
import org.archive.crawler.event.CrawlStateEvent;
import org.archive.crawler.framework.Frontier;
import org.archive.modules.CrawlURI;
import org.archive.modules.SchedulingConstants;
import org.archive.modules.extractor.Hop;
import org.archive.modules.extractor.LinkContext;
import org.archive.net.UURI;
import org.archive.net.UURIFactory;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationListener;
import org.springframework.context.Lifecycle;

import com.rabbitmq.client.AMQP.BasicProperties;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.Consumer;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;

/**
 * @contributor nlevitt
 */
public class AMQPUrlReceiver implements Lifecycle, ApplicationListener<CrawlStateEvent> {

    @SuppressWarnings("unused")
    private static final long serialVersionUID = 1L;

    private static final Logger logger = 
            Logger.getLogger(AMQPUrlReceiver.class.getName());

    public static final String A_RECEIVED_FROM_AMQP = "receivedFromAMQP";

    protected Frontier frontier;
    public Frontier getFrontier() {
        return this.frontier;
    }
    @Autowired
    public void setFrontier(Frontier frontier) {
        this.frontier = frontier;
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

    @Override
    synchronized public void start() {
        while (!isRunning) {
            try {
                Consumer consumer = new UrlConsumer(channel());
                channel.queueDeclare(getQueueName(), false, false, true, null);
                channel().queueBind(getQueueName(), exchange, getQueueName());
                channel().basicConsume(getQueueName(), false, consumer);
                isRunning = true;
            } catch (IOException e) {
                logger.log(Level.SEVERE, "problem starting AMQP consumer (will try again after 30 seconds)", e);
                try {
                    Thread.sleep(30000);
                } catch (InterruptedException e1) {
                }
            }
        }
    }

    @Override
    synchronized public void stop() {
        logger.info("shutting down");
        if (connection != null && connection.isOpen()) {
            try {
                connection.close();
            } catch (IOException e) {
                logger.log(Level.SEVERE, "problem closing AMQP connection", e);
            }
        }
    }

    transient protected Connection connection = null;
    transient protected Channel channel = null;

    synchronized protected Connection connection() throws IOException {
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
    }

    synchronized protected Channel channel() throws IOException {
        if (channel != null && !channel.isOpen()) {
            logger.warning("channel is not open, creating a new one");
            channel = null;
        }

        if (channel == null) {
            channel = connection().createChannel();
        }

        return channel;
    }

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
                CrawlURI curi;
                try {
                    curi = makeCrawlUri(jo);
                    // bypasses scoping (unless rechecking is configured)
                    getFrontier().schedule(curi);
                    if (logger.isLoggable(Level.FINE)) {
                        logger.fine("scheduled " + curi);
                    }
                } catch (URIException e) {
                    logger.log(Level.WARNING,
                            "problem creating CrawlURI from json received via AMQP "
                                    + decodedBody, e);
                } catch (JSONException e) {
                    logger.log(Level.SEVERE,
                            "problem creating CrawlURI from json received via AMQP "
                                    + decodedBody, e);
                }
            } else {
                logger.warning("ignoring url with method other than GET - " + decodedBody);
            }

            this.getChannel().basicAck(envelope.getDeliveryTag(), false);
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
        @SuppressWarnings("unchecked")
        protected CrawlURI makeCrawlUri(JSONObject jo) throws URIException,
                JSONException {
            JSONObject joHeaders = jo.getJSONObject("headers");

            UURI uuri = UURIFactory.getInstance(jo.getString("url"));
            UURI via = UURIFactory.getInstance(jo.getString("parentUrl"));

            JSONObject parentUrlMetadata = jo.getJSONObject("parentUrlMetadata");
            String parentHopPath = parentUrlMetadata.getString("pathFromSeed");
            String hopPath = parentHopPath + Hop.INFERRED.getHopString();

            CrawlURI curi = new CrawlURI(uuri, hopPath, via, LinkContext.INFERRED_MISC);
            
            // set the heritable data from the parent url, passed back to us via amqp
            // XXX brittle, only goes one level deep, and only handles strings and arrays, the latter of which it converts to a Set.
            // 'heritableData': {'source': 'https://facebook.com/whitehouse/', 'heritable': ['source', 'heritable']}
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

            // set the http headers from the amqp message
            Map<String, String> customHttpRequestHeaders = new HashMap<String, String>();
            for (Object key : joHeaders.keySet()) {
                customHttpRequestHeaders.put(key.toString(),
                        joHeaders.getString(key.toString()));
            }
            curi.getData().put("customHttpRequestHeaders", customHttpRequestHeaders);

            /* Use HighestUriQueuePrecedencePolicy to ensure these high priority
             * urls really get crawled ahead of others. 
             * See https://webarchive.jira.com/wiki/display/Heritrix/Precedence+Feature+Notes
             */
            curi.setSchedulingDirective(SchedulingConstants.HIGH);
            curi.setPrecedence(1);
            
            // XXX? curi.setForceFetch(true);

            curi.getAnnotations().add(A_RECEIVED_FROM_AMQP);

            return curi;
        }
    }

    @Override
    public void onApplicationEvent(CrawlStateEvent event) {
        switch(event.getState()) {
        case PAUSING: case PAUSED:
            try {
                channel().flow(false);
            } catch (IOException e) {
                logger.log(Level.WARNING, "failed to pause flow on amqp channel", e);
            }
            break;
            
        case RUNNING: case EMPTY: case PREPARING:
            try {
                channel().flow(true);
            } catch (IOException e) {
                logger.log(Level.SEVERE, "failed to resume flow on amqp channel", e);
            }
            break;

        default:
        }
    }
}
