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
package org.archive.modules.deciderules;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.archive.modules.AMQPProducer;
import org.archive.modules.CrawlURI;
import org.archive.modules.net.CrawlHost;
import org.archive.modules.net.ServerCache;
import org.archive.util.ArchiveUtils;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.AMQP.BasicProperties;

public class DecideRuleSequenceWithAMQPFeed extends DecideRuleSequence {
    private static final long serialVersionUID = 1L;

    private static final Logger logger =
            Logger.getLogger(DecideRuleSequenceWithAMQPFeed.class.getName());

    protected String amqpUri = "amqp://guest:guest@localhost:5672/%2f";
    public String getAmqpUri() {
        return this.amqpUri;
    }
    public void setAmqpUri(String uri) {
        this.amqpUri = uri;
    }

    protected String exchange = "heritrix.realTimeFeed";
    public String getExchange() {
        return exchange;
    }
    public void setExchange(String exchange) {
        this.exchange = exchange;
    }

    protected String routingKey = "scopeLog";
    public String getRoutingKey() {
        return routingKey;
    }
    public void setRoutingKey(String routingKey) {
        this.routingKey = routingKey;
    }

    protected ServerCache serverCache;
    public ServerCache getServerCache() {
        return this.serverCache;
    }
    @Autowired
    public void setServerCache(ServerCache serverCache) {
        this.serverCache = serverCache;
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
    protected void decisionMade(CrawlURI curi, DecideRule decisiveRule,
            int decisiveRuleNumber, DecideResult result) {
        super.decisionMade(curi, decisiveRule, decisiveRuleNumber, result);

        JSONObject jo = buildJson(curi, decisiveRuleNumber, decisiveRule,
                result);

        byte[] message;
        try {
            message = jo.toString().getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }

        try {
            amqpProducer().publishMessage(message, props);
            if (logger.isLoggable(Level.FINEST)) {
                logger.log(Level.FINEST, "sent message to amqp: " + jo);
            }
        } catch (IOException e) {
            logger.log(Level.WARNING, "failed to send message to amqp: " + jo, e);
        }
    }

    protected BasicProperties props = new AMQP.BasicProperties.Builder().
            contentType("application/json").build();

    protected JSONObject buildJson(CrawlURI curi, int decisiveRuleNumber,
            DecideRule decisiveRule, DecideResult result) {
        JSONObject jo = new JSONObject();

        jo.put("timestamp", ArchiveUtils.getLog17Date(System.currentTimeMillis()));

        jo.put("decisiveRuleNo", decisiveRuleNumber);
        jo.put("decisiveRule", decisiveRule.getClass().getSimpleName());
        jo.put("result", result.toString());

        jo.put("url", curi.toString());

        CrawlHost host = getServerCache().getHostFor(curi.getUURI());
        if (host != null) {
            jo.put("host", host.fixUpName());
        } else {
            jo.put("host", JSONObject.NULL);
        }

        jo.put("sourceSeed", curi.getSourceTag());
        jo.put("via", curi.flattenVia());
        return jo;
    }
}
