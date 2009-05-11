/* OneLineSimpleLogger
 * 
 * $Id$
 * 
 * Created on Jul 22, 2004
 *
 * Copyright (C) 2004 Internet Archive.
 * 
 * This file is part of the Heritrix web crawler (crawler.archive.org).
 * 
 * Heritrix is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser Public License as published by
 * the Free Software Foundation; either version 2.1 of the License, or
 * any later version.
 * 
 * Heritrix is distributed in the hope that it will be useful, 
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser Public License
 * along with Heritrix; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
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
        new SimpleDateFormat("MM/dd/yyyy HH:mm:ss Z");
    
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
        buffer.append(' ');
        if (record.getSourceClassName() != null) {
            buffer.append(record.getSourceClassName());
        } else {
            buffer.append(record.getLoggerName());
        }
        buffer.append(' ');
        String methodName = record.getSourceMethodName();
        methodName = (methodName == null || methodName.length() <= 0)?
            "-": methodName;
        buffer.append(methodName);
        buffer.append(' ');
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
