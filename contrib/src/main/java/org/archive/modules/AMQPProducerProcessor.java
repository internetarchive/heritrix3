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
import java.util.logging.Level;
import java.util.logging.Logger;

import com.rabbitmq.client.AMQP.BasicProperties;

/**
 * @contributor nlevitt
 */
public abstract class AMQPProducerProcessor extends Processor {

    protected final Logger logger = Logger.getLogger(getClass().getName());

    {
        setAmqpUri("amqp://guest:guest@localhost:5672/%2f");
    }
    public String getAmqpUri() {
        return (String) kp.get("amqpUri");
    }
    public void setAmqpUri(String uri) {
        kp.put("amqpUri", uri);
    }

    public String getExchange() {
        return (String) kp.get("exchange");
    }
    public void setExchange(String exchange) {
        kp.put("exchange", exchange);
    }

    public String getRoutingKey() {
        return (String) kp.get("routingKey");
    }
    public void setRoutingKey(String routingKey) {
        kp.put("routingKey", routingKey);
    }

    transient protected AMQPProducer amqpProducer;

    protected AMQPProducer amqpProducer() {
        if (amqpProducer == null) {
            amqpProducer = new AMQPProducer(getAmqpUri(), getExchange(), getRoutingKey());
        }
        return amqpProducer;
    }

    @Override
    synchronized public void stop() {
        if (!isRunning) {
            return;
        }

        super.stop();

        if (amqpProducer != null) {
            amqpProducer.stop();
        }
    }

    @Override
    protected ProcessResult innerProcessResult(CrawlURI curi)
            throws InterruptedException {
        byte[] message = null;
        BasicProperties props = null;

        message = buildMessage(curi);
        props = amqpMessageProperties();
        try {
            amqpProducer().publishMessage(message, props);
            success(curi, message, props);
        } catch (IOException e) {
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
                        + " routingKey=" + getRoutingKey() + ": " + new String(message, "UTF-8"));
            } catch (UnsupportedEncodingException e) {
                logger.fine("sent to amqp exchange=" + getExchange()
                        + " routingKey=" + getRoutingKey() + ": " + message + " (" + message.length + " bytes)");
            }
        }
    }

    protected void fail(CrawlURI curi, byte[] message, BasicProperties props, Throwable e) {
        logger.log(Level.SEVERE, "failed to send message to amqp for URI " + curi, e);
    }
}
