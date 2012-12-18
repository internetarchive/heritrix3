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

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;

public class ReportUtils {
    /**
     * Utility method to get a String shortReportLine from Reporter
     * @param rep  Reporter to get shortReportLine from
     * @return String of report
     */
    public static String shortReportLine(Reporter rep) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        try {
            rep.shortReportLineTo(pw);
        } catch (IOException e) {
            // not really possible
            e.printStackTrace();
        }
        pw.flush();
        return sw.toString();
    }
    
}
