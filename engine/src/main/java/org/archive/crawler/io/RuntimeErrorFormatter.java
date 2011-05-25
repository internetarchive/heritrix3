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
import org.archive.modules.CrawlURI;

/**
 * Runtime exception log formatter.
 *
 * Used to format unexpected runtime exceptions such as 
 * OOMEs.
 * 
 * @author gojomo
 */
public class RuntimeErrorFormatter extends UriProcessingFormatter
implements CoreAttributeConstants {

    public RuntimeErrorFormatter(boolean logExtraInfo) {
        super(logExtraInfo);
    }

    public String format(LogRecord lr) {
        Object [] parameters = lr.getParameters();
        String stackTrace = "None retrieved";
        if (parameters != null) {
            // CrawlURI is always first parameter.
            CrawlURI curi = (CrawlURI)parameters[0];
            if (curi != null) {
                Throwable t = (Throwable)curi.getData().get(A_RUNTIME_EXCEPTION);
                assert t != null : "Null throwable";
                StringWriter sw = new StringWriter();
                if (t == null) {
                    sw.write("No exception to report.");
                } else {
                    t.printStackTrace(new PrintWriter(sw));
                }
                stackTrace = sw.toString();
            }
        }
        return super.format(lr) + " " + stackTrace;
    }
}
