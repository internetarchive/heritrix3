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

import java.util.logging.Formatter;
import java.util.logging.LogRecord;

import org.archive.modules.CoreAttributeConstants;
import org.archive.net.UURI;
import org.archive.util.ArchiveUtils;

/**
 * Formatter for 'uri-errors.log', of URIs so malformed they could
 * not be instantiated.
 * 
 * @author gojomo
 *
 */
public class UriErrorFormatter extends Formatter implements CoreAttributeConstants {

    /* (non-Javadoc)
     * @see java.util.logging.Formatter#format(java.util.logging.LogRecord)
     */
    public String format(LogRecord lr) {
        UURI uuri = (UURI) lr.getParameters()[0];
        String problem = (String) lr.getParameters()[1];

        return ArchiveUtils.getLog17Date()
        + " "
        + ( (uuri ==null) ? "n/a" : uuri.toString() )
        + " \""
        + lr.getMessage()
        + "\" "
        + problem
        + "\n";
    }
}

