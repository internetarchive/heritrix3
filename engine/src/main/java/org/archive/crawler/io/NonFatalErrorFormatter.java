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
package org.archive.crawler.io;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.logging.LogRecord;

import org.archive.modules.CoreAttributeConstants;

/**
 * @author gojomo
 *
 */
public class NonFatalErrorFormatter extends UriProcessingFormatter implements CoreAttributeConstants {

    public NonFatalErrorFormatter(boolean logExtraInfo) {
        super(logExtraInfo);
    }

    /* (non-Javadoc)
     * @see java.util.logging.Formatter#format(java.util.logging.LogRecord)
     */
    public String format(LogRecord lr) {
//        Throwable ex = lr.getThrown();
        Throwable ex = (Throwable)lr.getParameters()[1];
//        LocalizedError err = (LocalizedError) lr.getParameters()[1];
//        Throwable ex = (Throwable)err.exception;
        StringWriter sw = new StringWriter();
        ex.printStackTrace(new PrintWriter(sw));

        return super.format(lr) + " " + sw.toString();
    }
}


