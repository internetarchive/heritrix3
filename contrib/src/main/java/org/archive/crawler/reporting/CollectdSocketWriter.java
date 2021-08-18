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

package org.archive.crawler.reporting;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
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

/**
 * @author adam coco
 */
public class CollectdSocketWriter
	implements Lifecycle, ApplicationContextAware, ApplicationListener<CrawlStateEvent> {

    @SuppressWarnings("unused")
    private static final long serialVersionUID = 2L;

    private static final Logger logger =
            Logger.getLogger(CollectdSocketWriter.class.getName());

    protected ApplicationContext appCtx;
    public void setApplicationContext(ApplicationContext appCtx) throws BeansException {
        this.appCtx = appCtx;
    }


    protected String socketPath = "/run/collectd.socket";
    public String getSocketPath() {
        return this.socketPath;
    }
    public void setSocketPath(String path) {
        this.socketPath = path;
    }

    protected int writeInterval = 60;  // in seconds
    public int getWriteInterval() {
        return this.writeInterval;
    }
    public void setWriteInterval(String writeInterval) {
        this.writeInterval = writeInterval;
    }

    protected boolean isRunning = false;
    @Override
    public boolean isRunning() {
        return isRunning;
    }

    private transient Lock lock = new ReentrantLock(true);

    private class CollectdSocketWriterThread extends Thread {

        public CollectdSocketWriterThread(String name) {
            super(name);
        }

        @Override
        public void run() {
            while (!Thread.interrupted()) {
                try {
                    // TODO open AF_UNIX_SOCK

                    Thread.sleep(10 * 1000);
                } catch (InterruptedException e) {
                    return;
                }
            }
        }
    }

    transient private CollectdSocketWriterThread writerThread;

    @Override
    public void start() {
        lock.lock();
        try {
            // spawn off a thread to start up the amqp consumer, and try to restart it if it dies
            if (!isRunning) {
                writerThread = new CollectdSocketWriterThread(CollectdSocketWriter.class.getSimpleName() + "-thread");
                writerThread.start();
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
            logger.info("shutting down");
            if (writerThread != null && writerThread.isAlive()) {
                writerThread.interrupt();
                try {
                    writerThread.join();
                } catch (InterruptedException e) {
                }
            }
            writerThread = null;

            // TODO this is probably where we want to close the UNIX SOCKET
            // if (connection != null && connection.isOpen()) {
            //     try {
            //         connection.close();
            //     } catch (IOException e) {
            //         logger.log(Level.SEVERE, "problem closing AMQP connection", e);
            //     }
            // }
            // connection = null;
            isRunning = false;
        } finally {
            lock.unlock();
        }
    }

    // TODO replace this with unix socket connection object????
    // transient protected Connection connection = null;

    // protected Connection connection() throws IOException {
    //     lock.lock();
    //     try {
    //         if (connection != null && !connection.isOpen()) {
    //             logger.warning("connection is closed, creating a new one");
    //             connection = null;
    //         }

    //         if (connection == null) {
    //             ConnectionFactory factory = new ConnectionFactory();
    //             try {
    //                 factory.setUri(getAmqpUri());
    //             } catch (Exception e) {
    //                 throw new IOException("problem with AMQP uri " + getAmqpUri(), e);
    //             }
    //             connection = factory.newConnection();
    //         }

    //         return connection;
    //     } finally {
    //         lock.unlock();
    //     }
    // }

    @Override
    public void onApplicationEvent(CrawlStateEvent event) {
        switch(event.getState()) {
        case PAUSING: case PAUSED:
            // TODO do we need this?
            break;
        case RUNNING:
            // TODO do we need this?
            break;

        default:
        }
    }
}
