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

/**
 * @contributor stack
 */
public interface MultiReporter  extends Reporter {
    /**
     * Get an array of report names offered by this Reporter. 
     * A name in brackets indicates a free-form String, 
     * in accordance with the informal description inside
     * the brackets, may yield a useful report.
     * 
     * @return String array of report names, empty if there is only
     * one report type
     */
    public String[] getReports();
    
    /**
     * Make a report of the given name to the passed-in Writer,
     * If null, give the default report. 
     * 
     * @param writer to receive report
     */
    public void reportTo(String name, PrintWriter writer);
}
