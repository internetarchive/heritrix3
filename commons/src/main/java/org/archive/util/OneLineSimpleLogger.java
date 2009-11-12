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
package org.archive.util;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.FieldPosition;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;



/**
 * Logger that writes entry on one line with less verbose date.
 * 
 * @author stack
 * @version $Revision$, $Date$
 */
public class OneLineSimpleLogger extends SimpleFormatter {
    
    /**
     * Date instance.
     * 
     * Keep around instance of date.
     */
    private Date date = new Date();
    
    /**
     * Field position instance.
     * 
     * Keep around this instance.
     */
    private FieldPosition position = new FieldPosition(0);
    
    /**
     * MessageFormatter for date.
     */
    private SimpleDateFormat formatter = 
        new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
    
    /**
     * Persistent buffer in which we conjure the log.
     */
    private StringBuffer buffer = new StringBuffer();
    

    public OneLineSimpleLogger() {
        super();
    }
    
    public synchronized String format(LogRecord record) {
        this.buffer.setLength(0);
        this.date.setTime(record.getMillis());
        this.position.setBeginIndex(0);
        this.formatter.format(this.date, buffer, this.position);
        buffer.append(' ');
        buffer.append(record.getLevel().getLocalizedName());
        buffer.append(" thread-");
        buffer.append(record.getThreadID());
        buffer.append(' ');
        if (record.getSourceClassName() != null) {
            buffer.append(record.getSourceClassName());
        } else {
            buffer.append(record.getLoggerName());
        }
        buffer.append('.');
        String methodName = record.getSourceMethodName();
        methodName = (methodName == null || methodName.length() <= 0)?
            "-": methodName;
        buffer.append(methodName);
        buffer.append("() ");
        buffer.append(formatMessage(record));
        buffer.append(System.getProperty("line.separator"));
        if (record.getThrown() != null) {
    	    try {
    	        StringWriter writer = new StringWriter();
    	        PrintWriter printer = new PrintWriter(writer);
    	        record.getThrown().printStackTrace(printer);
    	        writer.close();
    	        buffer.append(writer.toString());
    	    } catch (Exception e) {
    	        buffer.append("Failed to get stack trace: " + e.getMessage());
    	    }
        }
        return buffer.toString();
    }
    
    public static Logger setConsoleHandler() {
        Logger logger = Logger.getLogger("");
        Handler [] hs = logger.getHandlers();
        for (int i = 0; i < hs.length; i++) {
            Handler h = hs[0];
            if (h instanceof ConsoleHandler) {
                h.setFormatter(new OneLineSimpleLogger());
            }
        }
        return logger;
    }

    /**
     * Test this logger.
     */
    public static void main(String[] args) {
        Logger logger = setConsoleHandler();
        logger = Logger.getLogger("Test");
        logger.severe("Does this come out?");
        logger.severe("Does this come out?");
        logger.severe("Does this come out?");
        logger.log(Level.SEVERE, "hello", new RuntimeException("test"));
    }
}
