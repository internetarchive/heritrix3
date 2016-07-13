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

import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.apache.commons.httpclient.URIException;
import org.archive.crawler.event.AMQPUrlPublishedEvent;
import org.archive.crawler.frontier.AMQPUrlReceiver;
import org.archive.modules.fetcher.FetchHTTP;
import org.json.JSONObject;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ApplicationEvent;
import org.springframework.beans.BeansException;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.AMQP.BasicProperties;

/**
 * @author eldondev
 * @contributor nlevitt
 */
public class AMQPPublishProcessor extends AMQPProducerProcessor implements Serializable, ApplicationContextAware {

    private static final long serialVersionUID = 2L;

    public static final String A_SENT_TO_AMQP = "sentToAMQP"; // annotation

    protected ApplicationContext appCtx;
    public void setApplicationContext(ApplicationContext appCtx) throws BeansException {
        this.appCtx = appCtx;
    }

    public AMQPPublishProcessor() {
        // set default values
        setExchange("umbra");
        setRoutingKey("urls");
    }

    {
        setClientId("requests");
    }
    public String getClientId() {
        return (String) kp.get("clientId");
    }
    /**
     * Client id to include in the json payload. AMQPUrlReceiver queueName
     * should have the same value, since umbra will route request urls based on
     * this key.
     */
    public void setClientId(String clientId) {
        kp.put("clientId", clientId);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> getExtraInfo() {
        return (Map<String, Object>) kp.get("extraInfo");
    }
    /**
     * Arbitrary additional information to include in the json payload.
     */
    public void setExtraInfo(Map<String, Object> extraInfo) {
        kp.put("extraInfo", extraInfo);
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

        if (getExtraInfo() != null) {
            for (String k: getExtraInfo().keySet()) {
                message.put(k, getExtraInfo().get(k));
            }
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
    protected byte[] buildMessage(CrawlURI curi) {
        try {
            return buildJsonMessage(curi).toString().getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected void success(CrawlURI curi, byte[] message, BasicProperties props) {
        super.success(curi, message, props);
        curi.getAnnotations().add(A_SENT_TO_AMQP);
        appCtx.publishEvent(new AMQPUrlPublishedEvent(AMQPPublishProcessor.this, curi));
    }

    protected BasicProperties props = new AMQP.BasicProperties.Builder().
            contentType("application/json").build();

    @Override
    protected BasicProperties amqpMessageProperties() {
        return props;
    }
}
