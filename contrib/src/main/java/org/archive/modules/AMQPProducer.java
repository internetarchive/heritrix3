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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.rabbitmq.client.AMQP.BasicProperties;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

public class AMQPProducer {
    static protected final Logger logger = Logger.getLogger(AMQPProducer.class.getName());

    protected String amqpUri;
    protected String exchange;
    protected String routingKey;

    public AMQPProducer(String amqpUri, String exchange, String routingKey) {
        this.amqpUri = amqpUri;
        this.exchange = exchange;
        this.routingKey = routingKey;
    }

    transient protected Connection connection = null;
    transient protected ThreadLocal<Channel> threadChannel =
            new ThreadLocal<Channel>();

    protected synchronized Channel channel() throws IOException {
        Channel existing = threadChannel.get();
        if (existing != null && !existing.isOpen()) {
            try {
                // Abort, don't just drop: an unaborted channel stays registered
                // on the connection and recovery keeps restoring it.
                existing.abort();
            } catch (IOException e) {
                logger.log(Level.FINE, "ignoring error aborting dead channel", e);
            }
            threadChannel.remove();
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
                throw new IOException("Attempting to create channel for AMQP connection failed!", e);
            }
        }

        return threadChannel.get();
    }

    private AtomicBoolean serverLooksDown = new AtomicBoolean(false);
    private synchronized void connect() throws IOException {
        if (connection != null) {
            // Overwriting the field would leave the old connection open and
            // recovering, consuming resources this producer can no longer see.
            connection.abort();
            connection = null;
        }

        ConnectionFactory factory = new ConnectionFactory();
        try {
            factory.setUri(amqpUri);
            connection =  factory.newConnection();
            boolean wasDown = serverLooksDown.getAndSet(false);
            if (wasDown) {
                logger.info(amqpUri + " is back up, connected successfully!");
            }
        } catch (Exception e) {
            connection = null;
            serverLooksDown.getAndSet(true);
            throw new IOException("Attempting to connect to AMQP server failed! " + amqpUri, e);
        }
    }

    synchronized public void stop() {
        if (connection == null) {
            return;
        }
        try {
            if (connection.isOpen()) {
                connection.close();
            }
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Attempting to close AMQP connection failed!", e);
        } finally {
            // Unconditionally, including when it was already closed: a
            // connection left unaborted goes on recovering after the crawl has
            // stopped. A no-op once close() has succeeded.
            connection.abort();
            connection = null;
        }
    }

    /**
     * Publish the message with the supplied properties. If this method returns
     * without throwing an exception, the message was published successfully.
     *
     * @param message
     * @param props
     * @throws IOException
     *             if message is not published successfully for any reason
     */
    public void publishMessage(byte[] message, BasicProperties props)
            throws IOException {
        Channel channel = channel();
        channel.exchangeDeclare(exchange, "direct", true);
        channel.basicPublish(exchange, routingKey, props, message);
    }
}
