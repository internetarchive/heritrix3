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
package org.archive.modules.deciderules;

import org.archive.modules.CrawlURI;
import org.archive.modules.extractor.Hop;


/**
 * Rule which ACCEPTs all 'prerequisite' URIs (those with a 'P' in
 * the last hopsPath position). Good in a late position to ensure
 * other scope settings don't lock out necessary prerequisites.
 *
 * @author gojomo
 */
public class PrerequisiteAcceptDecideRule extends DecideRule {

    private static final long serialVersionUID = 3L;

    public PrerequisiteAcceptDecideRule() {
    }


    public DecideResult innerDecide(CrawlURI uri) {        
        String hopsPath = uri.getPathFromSeed();
            if (hopsPath != null && hopsPath.length() > 0 &&
                    hopsPath.charAt(hopsPath.length()-1) == Hop.PREREQ.getHopChar()) {
                return DecideResult.ACCEPT;
            }
        return DecideResult.NONE;
    }


}
