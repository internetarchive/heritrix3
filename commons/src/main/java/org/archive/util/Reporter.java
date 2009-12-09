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
import java.util.Map;

public interface Reporter {
    /**
     * Make a default report to the passed-in Writer. Should
     * be equivalent to reportTo(null, writer)
     * 
     * @param writer to receive report
     */
    public void reportTo(PrintWriter writer) throws IOException;
    
    /**
     * Write a short single-line summary report 
     * 
     * @param writer to receive report
     */
    public void shortReportLineTo(PrintWriter pw) throws IOException;
    

    /**
     * @return Same data that's in the single line report, as key-value pairs
     */
    public Map<String,Object> shortReportMap();

    
    /**
     * Return a legend for the single-line summary report as a String.
     * 
     * @return String single-line summary legend
     */
    public String shortReportLegend();
}
