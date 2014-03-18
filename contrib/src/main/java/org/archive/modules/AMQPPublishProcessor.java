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

package org.archive.modules;

import static org.archive.modules.CoreAttributeConstants.A_HERITABLE_KEYS;

import java.io.IOException;
import java.util.HashMap;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.httpclient.URIException;
import org.archive.crawler.frontier.AMQPUrlReceiver;
import org.archive.modules.fetcher.FetchHTTP;
import org.json.JSONObject;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.AMQP.BasicProperties;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

/**
 * @author eldondev
 * @contributor nlevitt
 */
public class AMQPPublishProcessor extends Processor {

    @SuppressWarnings("unused")
    private static final long serialVersionUID = 1L;

    private static final Logger logger =
            Logger.getLogger(AMQPPublishProcessor.class.getName());

    public static final int S_SENT_TO_AMQP = 10001; // artificial fetch status
    public static final String A_SENT_TO_AMQP = "sentToAMQP"; // annotation

    protected String amqpUri = "amqp://guest:guest@localhost:5672/%2f";
    public String getAmqpUri() {
        return this.amqpUri;
    }
    public void setAmqpUri(String uri) {
        this.amqpUri = uri; 
    }

    transient protected Connection connection = null;
    transient protected ThreadLocal<Channel> threadChannel = 
            new ThreadLocal<Channel>();

    protected String exchange = "umbra";
    public String getExchange() {
        return exchange;
    }
    public void setExchange(String exchange) {
        this.exchange = exchange;
    }

    protected String routingKey = "url";
    public String getRoutingKey() {
        return routingKey;
    }
    public void setRoutingKey(String routingKey) {
        this.routingKey = routingKey;
    }
    
    protected String clientId = null;
    public String getClientId() {
        return clientId;
    }
    /**
     * Client id to include in the json payload. AMQPUrlReceiver queueName
     * should have the same value, since umbra will route request urls based on
     * this key.
     */
    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    /**
     * @return true iff url is http or https, is not robots.txt, was not
     *         received via AMQP
     */
    protected boolean shouldProcess(CrawlURI curi) {
        try {
            return !curi.getAnnotations().contains(AMQPUrlReceiver.A_RECEIVED_FROM_AMQP)
                    && !"/robots.txt".equals(curi.getUURI().getPath())
                    && (curi.getUURI().getScheme().equals(FetchHTTP.HTTP_SCHEME) 
                            || curi.getUURI().getScheme().equals(FetchHTTP.HTTPS_SCHEME));
        } catch (URIException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected ProcessResult innerProcessResult(CrawlURI curi)
            throws InterruptedException {
        try {
            Channel channel = channel();
            if (channel != null) {
                JSONObject message = buildJsonMessage(curi);

                BasicProperties props = new AMQP.BasicProperties.Builder().
                        contentType("application/json").build();
                channel.exchangeDeclare(getExchange(), "direct", true);
                channel.basicPublish(getExchange(), getRoutingKey(), props, 
                        message.toString().getBytes("UTF-8"));
                if (logger.isLoggable(Level.FINE)) {
                    logger.fine("sent to amqp exchange=" + getExchange()
                            + " routingKey=" + routingKey + ": " + message);
                }

                curi.getAnnotations().add(A_SENT_TO_AMQP);
            }
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Attempting to send URI to AMQP server failed! " + curi, e);
        }

        return ProcessResult.PROCEED;
    }

    /**
     * Constructs the json to send via AMQP. This includes the url, and some
     * metadata from the CrawlURI. The metadata should be passed back to
     * heritrix with each url discovered from this url. (XXX need context in
     * class javadoc)
     * 
     * @return the message to send via AMQP
     * @see CrawlURI#inheritFrom(CrawlURI)
     */
    protected JSONObject buildJsonMessage(CrawlURI curi) {
        JSONObject message = new JSONObject().put("url", curi.toString());

        if (getClientId() != null) {
            message.put("clientId", getClientId());
        }

        HashMap<String, Object> metadata = new HashMap<String,Object>();
        metadata.put("pathFromSeed", curi.getPathFromSeed());

        @SuppressWarnings("unchecked")
        Set<String> heritableKeys = (Set<String>) curi.getData().get(A_HERITABLE_KEYS);
        HashMap<String, Object> heritableData = new HashMap<String,Object>();
        if (heritableKeys != null) {
            for (String key: heritableKeys) {
                heritableData.put(key, curi.getData().get(key));
            }
        }
        metadata.put("heritableData", heritableData);

        message.put("metadata", metadata);
        
        return message;
    }

    @Override
    protected void innerProcess(CrawlURI uri) throws InterruptedException {
        throw new RuntimeException("should never be called");
    }

    protected synchronized Channel channel() {
        if (threadChannel.get() == null) {
            if (connection == null) {
                connect();
            }
            try {
                if (connection != null) {
                    threadChannel.set(connection.createChannel());
                }
            } catch (IOException e) {
                logger.log(Level.SEVERE, "Attempting to create channel for AMQP connection failed!", e);
            }
        }
        return threadChannel.get();
    }

    private synchronized void connect() {
        ConnectionFactory factory = new ConnectionFactory();
        try {
            factory.setUri(getAmqpUri());
            connection =  factory.newConnection();
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Attempting to connect to AMQP server failed!", e);
        }
    }

    synchronized public void stop() {
        try {
            if(connection != null && connection.isOpen()) {
                connection.close();
            }
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Attempting to close AMQP connection failed!", e);
        }
    }
}
