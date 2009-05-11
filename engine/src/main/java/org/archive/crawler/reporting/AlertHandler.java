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

import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;


/**
 * Stub Handler, catching and relaying WARNING/SEVERE events to 
 * AlertThreadGroup.
 * 
 * @contributor pjack
 * @contributor gojomo
 */
public class AlertHandler extends Handler {
    // install global AlertHandler
    static {
        AlertHandler h = new AlertHandler();
        h.setLevel(Level.WARNING);
        Logger.getLogger("").addHandler(h);
    }


    @Override
    public void close() throws SecurityException {
       // Do nothing
    }


    @Override
    public void flush() {
        // Do nothing
    }

    
    /** 
     * Pass record to AlertThreadGroup. 
     * 
     * @see java.util.logging.Handler#publish(java.util.logging.LogRecord)
     */
    @Override
    public void publish(LogRecord record) {
        if (!isLoggable(record)) {
            return;
        }
        AlertThreadGroup.publishCurrent(record); 
    }


    /**
     * Simply to ensure static initialization (installing catchall
     * handler on topmost logger) is run. 
     */
    public static void ensureStaticInitialization() {
        // Do nothing
    }

}
