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
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.httpclient.URIException;
import org.apache.commons.lang.StringUtils;
import org.archive.crawler.framework.Frontier;
import org.archive.modules.CrawlURI;
import org.archive.modules.SchedulingConstants;
import org.archive.modules.extractor.LinkContext;
import org.archive.net.UURI;
import org.archive.net.UURIFactory;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.Lifecycle;

import com.rabbitmq.client.AMQP.BasicProperties;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.Consumer;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;

public class AMQPUrlReceiver implements Lifecycle, Runnable {

    @SuppressWarnings("unused")
    private static final long serialVersionUID = 1L;

    private static final Logger logger = 
            Logger.getLogger(AMQPUrlReceiver.class.getName());

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

    protected String queueName = "requests";
    public String getQueueName() {
        return queueName;
    }
    public void setQueueName(String queueName) {
        this.queueName = queueName;
    }

    transient protected Thread fred = null;

    @Override
    public void start() {
        if (isRunning()) {
            return;
        }

        fred = new Thread(this, getClass().getSimpleName());
        fred.start();
    }

    @Override
    public void stop() {
        logger.info("shutting down");
        boolean joined = false;
        while (!joined) {
            fred.interrupt();
            try {
                fred.join();
                joined = true;
            } catch (InterruptedException e) {
            }
        }
    }

    @Override
    public boolean isRunning() {
        return fred != null && fred.isAlive();
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

    protected class UrlConsumer extends DefaultConsumer {
        public UrlConsumer(Channel channel) {
            super(channel);
        }

        @Override
        public void handleDelivery(String consumerTag, Envelope envelope,
                BasicProperties properties, byte[] body) {
            // logger.info("consumerTag=" + consumerTag
            // + " envelope=" + envelope
            // + " properties=" + properties
            // + " body=" + body);
            String decodedBody;
            try {
                decodedBody = new String(body, "UTF-8");
            } catch (UnsupportedEncodingException e) {
                throw new RuntimeException(e); // can't happen
            }
            JSONObject jo = new JSONObject(decodedBody);
            CrawlURI curi;
            try {
                curi = makeCrawlUri(jo);
                // bypasses scoping (unless rechecking is configured)
                getFrontier().schedule(curi);
                logger.info("scheduled " + curi);
            } catch (URIException e) {
                logger.log(Level.SEVERE,
                        "problem creating CrawlURI from json received via AMQP "
                                + decodedBody, e);
            } catch (JSONException e) {
                logger.log(Level.SEVERE,
                        "problem creating CrawlURI from json received via AMQP "
                                + decodedBody, e);
            }
        }

        // {
        //  "headers": {
        //  "Referer": "https://archive.org/",
        //  "User-Agent": "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Ubuntu Chromium/32.0.1700.102 Chrome/32.0.1700.102 Safari/537.36",
        //  "Accept": "image/webp,*/*;q=0.8"
        // },
        // "url": "https://analytics.archive.org/0.gif?server_ms=256&server_name=www19.us.archive.org&service=ao&loadtime=358&timediff=-8&locale=en-US&referrer=-&version=2&count=9",
        // "method": "GET"
        // }
        protected CrawlURI makeCrawlUri(JSONObject jo) throws URIException,
                JSONException {
            JSONObject joHeaders = jo.getJSONObject("headers");

            UURI uuri = UURIFactory.getInstance(jo.getString("url"));
            UURI via = null;
            if (joHeaders.has("Referer")) {
                String referer = joHeaders.getString("Referer");
                if (StringUtils.isNotEmpty(referer)) {
                    via = UURIFactory.getInstance(referer);
                }
            }
            // XXX pathFromSeed? viaContext?
            CrawlURI curi = new CrawlURI(uuri, "?", via, LinkContext.INFERRED_MISC);

            HashMap<String, String> customHttpRequestHeaders = new HashMap<String, String>();
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

            curi.getAnnotations().add("receivedViaAMQP");

            return curi;
        }
    }

    @Override
    public void run() {
        logger.info(Thread.currentThread() + " starting");
        while (true) {
            try {
                if (Thread.interrupted()) {
                    throw new InterruptedException();
                }

                try {
                    Consumer consumer = new UrlConsumer(channel());
                    channel().basicConsume(getQueueName(), false, consumer);
                } catch (IOException e) {
                    logger.log(Level.SEVERE, "problem consuming AMQP (will try again after 10 seconds)", e);
                    Thread.sleep(10000);
                }

            } catch (InterruptedException e) {
                logger.info(Thread.currentThread() + " interrupted, shutting down");
                shutdown();
                return;
            }
        }
    }

    protected void shutdown() {
        if (connection != null && connection.isOpen()) {
            try {
                connection.close();
            } catch (IOException e) {
                logger.log(Level.SEVERE, "problem closing AMQP connection", e);
            }
        }
    }

    public static void main(String[] args) throws InterruptedException {
        AMQPUrlReceiver x = new AMQPUrlReceiver();
        // x.setAmqpUri("amqp://guest:guest@desktop-nlevitt.sf.archive.org:5672/%2f");
        x.start();
        Thread.sleep(90000);
        x.stop();
    }
}
