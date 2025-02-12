package org.archive.crawler.frontier;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeoutException;
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

public class AMQPUrlReceiver implements Lifecycle, ApplicationContextAware, ApplicationListener<CrawlStateEvent> {

    private static final Logger logger = Logger.getLogger(AMQPUrlReceiver.class.getName());
    private static final String DEFAULT_QUEUE_NAME = "requests";
    private static final String DEFAULT_EXCHANGE = "umbra";
    private static final String A_RECEIVED_FROM_AMQP = "receivedFromAMQP";
    
    protected ApplicationContext appCtx;
    protected CandidatesProcessor candidates;
    protected String amqpUri = "amqp://guest:guest@localhost:5672/%2f";
    protected String exchange = DEFAULT_EXCHANGE;
    protected String queueName = DEFAULT_QUEUE_NAME;
    protected boolean isRunning = false;
    private boolean durable = false;
    private boolean autoDelete = true;
    private boolean forceFetch = false;
    private Integer prefetchCount = 1000;
    private transient Lock lock = new ReentrantLock(true);
    private transient boolean pauseConsumer = false;
    private transient String consumerTag = null;
    private transient StarterRestarter starterRestarter;
    transient protected Connection connection = null;
    transient protected Channel channel = null;
    
    @Override
    public void setApplicationContext(ApplicationContext appCtx) throws BeansException {
        this.appCtx = appCtx;
    }
    
    public String getAmqpUri() {
        return this.amqpUri;
    }
    public void setAmqpUri(String uri) {
        this.amqpUri = uri;
    }
    public boolean isRunning() {
        return isRunning;
    }
    
    @Override
    public void start() {
        lock.lock();
        try {
            if (!isRunning) {
                starterRestarter = new StarterRestarter("Starter-Restarter");
                try {
                    starterRestarter.startConsumer();
                } catch (IOException | TimeoutException e) {
                    logger.log(Level.SEVERE, "Problem starting AMQP consumer", e);
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
            if (starterRestarter != null && starterRestarter.isAlive()) {
                starterRestarter.interrupt();
                try {
                    starterRestarter.join();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    logger.warning("Interrupted while stopping StarterRestarter");
                }
            }
            starterRestarter = null;
            if (connection != null && connection.isOpen()) {
                try {
                    connection.close();
                } catch (IOException e) {
                    logger.log(Level.SEVERE, "Problem closing AMQP connection", e);
                }
            }
            connection = null;
            channel = null;
            isRunning = false;
        } finally {
            lock.unlock();
        }
    }
    
    protected Connection connection() throws IOException, TimeoutException {
        lock.lock();
        try {
            if (connection == null || !connection.isOpen()) {
                ConnectionFactory factory = new ConnectionFactory();
                factory.setUri(getAmqpUri());
                connection = factory.newConnection();
            }
            return connection;
        } finally {
            lock.unlock();
        }
    }
    
    protected Channel channel() throws IOException, TimeoutException {
        lock.lock();
        try {
            if (channel == null || !channel.isOpen()) {
                channel = connection().createChannel();
            }
            return channel;
        } finally {
            lock.unlock();
        }
    }
    
    private class StarterRestarter extends Thread {
        public StarterRestarter(String name) {
            super(name);
        }

        @Override
        public void run() {
            while (!Thread.interrupted()) {
                try {
                    lock.lockInterruptibly();
                    if (consumerTag == null && !pauseConsumer) {
                        try {
                            startConsumer();
                        } catch (IOException | TimeoutException e) {
                            logger.log(Level.SEVERE, "Problem starting AMQP consumer", e);
                        }
                    }
                } catch (InterruptedException e) {
                    return;
                } finally {
                    lock.unlock();
                }
                try {
                    Thread.sleep(10000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }

        public void startConsumer() throws IOException, TimeoutException {
            Consumer consumer = new UrlConsumer(channel());
            channel().exchangeDeclare(getExchange(), "direct", true);
            channel().queueDeclare(getQueueName(), durable, false, autoDelete, null);
            channel().queueBind(getQueueName(), getExchange(), getQueueName());
            if (prefetchCount != null) {
                channel().basicQos(prefetchCount);
            }
            consumerTag = channel().basicConsume(getQueueName(), false, consumer);
            logger.info("Started AMQP consumer queueName=" + getQueueName());
        }
    }
}
