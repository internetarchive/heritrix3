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
import java.util.logging.Level;
import java.util.logging.Logger;

import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Required;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

/**

 * @author eldondev
 * @version $Date$, $Revision$
 */
public class AMQPPublishProcessor extends Processor {

	@SuppressWarnings("unused")
	private static final long serialVersionUID = 1L;

	private static final Logger logger =
			Logger.getLogger(AMQPPublishProcessor.class.getName());


	protected String amqpUri = null;
	protected Connection connection = null;

	public String getAmqpUri() {
		return this.amqpUri;
	}
	
	@Required
	public void setAmqpUri(String uri) {
		this.amqpUri = uri; 
	}

	transient protected ThreadLocal<Channel> threadChannel = 
			new ThreadLocal<Channel>();

	/**
	 * Constructor.
	 */
	public AMQPPublishProcessor() {
		super();
	}

	protected boolean shouldProcess(CrawlURI curi) {
		return true;
	}

	protected void innerProcess(CrawlURI uri) throws InterruptedException {
		try {
			Channel channel = getChannel();
			if(channel != null) {
				JSONObject message = new JSONObject();
				message.put("url", uri.toString());
				channel.basicPublish("umbra", "url", null, message.toString().getBytes());
			}
		} catch (Exception e) {
			logger.log(Level.SEVERE, "Attempting to send URI to umbra failed!", e);
		}
	};

	protected synchronized Channel getChannel() {
		if (threadChannel.get() == null) {
			if(connection == null) {
				connect();
			}
			try {
				if(connection != null) {
						threadChannel.set(connection.createChannel());
				}
			} catch (IOException e) {
				logger.log(Level.SEVERE, "Attempting to create channel for AMQP connection to umbra failed!", e);
			}
		}
		return threadChannel.get();
	}

	private synchronized void connect() {
		ConnectionFactory factory = new ConnectionFactory();
		try {
			factory.setUri(amqpUri);
			connection =  factory.newConnection();
		} catch (Exception e) {
			logger.log(Level.SEVERE, "Attempting to connect to AMQP for umbra failed!", e);
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
