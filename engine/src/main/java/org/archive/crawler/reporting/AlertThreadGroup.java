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

import java.util.LinkedList;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import org.archive.io.SinkHandlerLogThread;

/**
 * Parent thread group which lets all child threads find the right 
 * 'alert' error handler. Helpful for collecting all log events
 * of a certain severity (WARNING, SEVERE) from a group of related
 * threads. 
 * 
 * @contributor pjack
 * @contributor gojomo
 */
public class AlertThreadGroup extends ThreadGroup {
    protected int count;
    protected LinkedList<Logger> loggers = new LinkedList<Logger>(); 
    static protected ThreadLocal<Logger> threadLogger = new ThreadLocal<Logger>();
    
    public AlertThreadGroup(String name) {
        super(name);
    }

    public int getAlertCount() {
        return count;
    }

    public void resetAlertCount() {
        count = 0;
    }
    
    public void addLogger(Logger logger) {
        loggers.add(logger);
    }
    
    /** set alternate temporary alert logger */
    public static void setThreadLogger(Logger logger) {
        threadLogger.set(logger); 
    }

    public static AlertThreadGroup current() {
        Thread t = Thread.currentThread();
        ThreadGroup th = t.getThreadGroup();
        while ((th != null) && !(th instanceof AlertThreadGroup)) {
            th = th.getParent();
        }
        return (AlertThreadGroup)th;
    }

    public static void publishCurrent(LogRecord record) {
        AlertThreadGroup atg = AlertThreadGroup.current();
        if (atg == null) {
            Logger tlog = threadLogger.get(); 
            if(tlog!=null) {
                // send to temp-registered logger
                boolean usePar = tlog.getUseParentHandlers();
                tlog.setUseParentHandlers(false);
                tlog.log(record);
                tlog.setUseParentHandlers(usePar);
            }
            return;
        }
        atg.publish(record);
    }

    /**
     * Pass a record to all loggers registered with the 
     * AlertThreadGroup. Adds thread info to the message, 
     * if available.
     * 
     * @param record
     */
    public void publish(LogRecord record) {
        String orig = record.getMessage();
        StringBuilder newMessage = new StringBuilder(256);
        Thread current = Thread.currentThread();
        newMessage.append(orig).append(" (in thread '");
        newMessage.append(current.getName()).append("'");
        if (current instanceof SinkHandlerLogThread) {
            SinkHandlerLogThread tt = (SinkHandlerLogThread)current;
            if(tt.getCurrentProcessorName().length()>0) {
                newMessage.append("; in processor '");
                newMessage.append(tt.getCurrentProcessorName());
                newMessage.append("'");
            }
        }
        newMessage.append(")");
        record.setMessage(newMessage.toString());
        count++;
        for(Logger logger : loggers) {
            // for the relay, suppress use of parent handlers
            // (otherwise endless loop a risk if any target
            // loggers relay through parents to topmost logger)
            synchronized(logger) {
                boolean usePar = logger.getUseParentHandlers();
                logger.setUseParentHandlers(false);
                logger.log(record);
                logger.setUseParentHandlers(usePar); 
            }
        }
    }
}
