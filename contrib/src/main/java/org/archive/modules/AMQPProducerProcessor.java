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

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.rabbitmq.client.AMQP.BasicProperties;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

/**
 * @contributor nlevitt
 */
public abstract class AMQPProducerProcessor extends Processor {

    protected final Logger logger = Logger.getLogger(getClass().getName());

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

    protected String exchange;
    public String getExchange() {
        return exchange;
    }
    public void setExchange(String exchange) {
        this.exchange = exchange;
    }

    protected String routingKey;
    public String getRoutingKey() {
        return routingKey;
    }
    public void setRoutingKey(String routingKey) {
        this.routingKey = routingKey;
    }

    protected synchronized Channel channel() {
        if (threadChannel.get() != null && !threadChannel.get().isOpen()) {
            threadChannel.set(null);
        }

        if (threadChannel.get() == null) {
            if (connection == null || !connection.isOpen()) {
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

    private AtomicBoolean serverLooksDown = new AtomicBoolean(false);

    private synchronized void connect() {
        ConnectionFactory factory = new ConnectionFactory();
        try {
            factory.setUri(getAmqpUri());
            connection =  factory.newConnection();
            boolean wasDown = serverLooksDown.getAndSet(false);
            if (wasDown) {
                logger.info(getAmqpUri() + " is back up, connected successfully!");
            }
        } catch (Exception e) {
            connection = null;
            boolean wasAlreadyDown = serverLooksDown.getAndSet(true);
            if (!wasAlreadyDown) {
                logger.log(Level.SEVERE, "Attempting to connect to AMQP server failed!", e);
            }
        }
    }

    synchronized public void stop() {
        if (!isRunning) {
            return;
        }

        super.stop();

        try {
            if (connection != null && connection.isOpen()) {
                connection.close();
            }
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Attempting to close AMQP connection failed!", e);
        }
    }

    @Override
    protected ProcessResult innerProcessResult(CrawlURI curi)
            throws InterruptedException {
        byte[] message = null;
        BasicProperties props = null;

        try {
            Channel channel = channel();

            if (channel != null) {
                message = buildMessage(curi);
                props = amqpMessageProperties();

                channel.exchangeDeclare(getExchange(), "direct", true);

                channel.basicPublish(getExchange(), getRoutingKey(), props, 
                        message);

                success(curi, message, props);
            }
        } catch (Exception e) {
            fail(curi, message, props, e);
        }

        return ProcessResult.PROCEED;
    }

    protected BasicProperties amqpMessageProperties() {
        return null;
    }

    @Override
    protected void innerProcess(CrawlURI uri) throws InterruptedException {
        throw new RuntimeException("should never be called");
    }

    abstract protected byte[] buildMessage(CrawlURI curi);

    protected void success(CrawlURI curi, byte[] message, BasicProperties props) {
        if (logger.isLoggable(Level.FINE)) {
            try {
                logger.fine("sent to amqp exchange=" + getExchange()
                        + " routingKey=" + routingKey + ": " + new String(message, "UTF-8"));
            } catch (UnsupportedEncodingException e) {
                logger.fine("sent to amqp exchange=" + getExchange()
                        + " routingKey=" + routingKey + ": " + message + " (" + message.length + " bytes)");
            }
        }
    }

    protected void fail(CrawlURI curi, byte[] message, BasicProperties props, Throwable e) {
        logger.log(Level.SEVERE, "failed to send message to amqp for URI " + curi, e);
    }
}
