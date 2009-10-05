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

import java.util.logging.Level;
import java.util.logging.Logger;

import org.archive.modules.CrawlURI;
import org.archive.net.PublicSuffixes;
import org.archive.net.UURI;

/**
 * Applies its decision if the current URI differs in that portion of
 * its hostname/domain that is assigned/sold by registrars, its
 * 'assignment-level-domain' (ALD) (AKA 'public suffix' or in previous 
 * Heritrix versions, 'topmost assigned SURT')
 * 
 * @author Olaf Freyer
 */
public class HopCrossesAssignmentLevelDomainDecideRule extends PredicatedDecideRule {
    private static final long serialVersionUID = 1L;

    private static final Logger LOGGER = Logger
            .getLogger(HopCrossesAssignmentLevelDomainDecideRule.class.getName());
    
    public HopCrossesAssignmentLevelDomainDecideRule() {
    }

    protected boolean evaluate(CrawlURI uri) {
        UURI via = uri.getVia();
        if (via == null) {
            return false;
        }
        try {
            // determine if this hop crosses assignment-level-domain borders
            String ald = getAssignmentLevelSurt(uri.getUURI());
            String viaAld = getAssignmentLevelSurt(via);
            if (ald != null && !ald.equals(viaAld)) {
                if(LOGGER.isLoggable(Level.FINE)) {
                    LOGGER.fine("rule matched for \"" + ald+"\" vs. \""+viaAld+"\"");
                }
                return true;
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING,"uri="+uri+" via="+via, e);
            // Return false since we could not get hostname or something else
            // went wrong
        }
        return false;
    }
    
    private String getAssignmentLevelSurt(UURI uuri){
        String surt = uuri.getSurtForm().replaceFirst(".*://\\((.*?)\\).*", "$1");
        return PublicSuffixes.reduceSurtToAssignmentLevel(surt);
        
    }
}
