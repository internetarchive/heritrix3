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
package org.archive.modules.deciderules.surt;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.archive.modules.CrawlURI;
import org.archive.modules.deciderules.DecideResult;
import org.archive.net.UURI;
import org.archive.util.SurtPrefixSet;

/**
 * Rule allows one level of discovery beyond configured scope
 * (e.g. Domain, plus the first otherwise out-of-scope link from an
 * in-scope page, but not further hops from that first page)
 *
 * @author Shifra Raffel
 * @version $Date$ $Revision$
 */
public class ScopePlusOneDecideRule extends SurtPrefixedDecideRule {

    private static final long serialVersionUID = 3L;

    {
        setUseDomain(true);
    }
    public boolean getUseDomain() {
        return (Boolean)kp.get("useDomain");
    }
    public void setUseDomain(boolean useDomain) {
        kp.put("useDomain", useDomain);
    }
    
    private static final Logger logger =
        Logger.getLogger(ScopePlusOneDecideRule.class.getName());
    
    /**
     * Constructor.
     * @param name
     */
    public ScopePlusOneDecideRule() {
        super();
    }

    /**
     * Evaluate whether given object comes from a URI which is in scope
     *
     * @param object to evaluate
     * @return true if URI is either in scope or its via is
     */
    @Override
    protected DecideResult innerDecide(CrawlURI uri) {
        SurtPrefixSet set = getPrefixes();
        UURI u = uri.getUURI();
        // First, is the URI itself in scope?
        boolean firstResult = isInScope(u, set);
        if (logger.isLoggable(Level.FINE)) {
            logger.fine("Tested scope of UURI itself '" + u +
                        " and result was " + firstResult);
        }                        
        if (firstResult == true) {
            return DecideResult.ACCEPT;
        } else {
            // This object is not itself within scope, but
            // see whether its via might be
            UURI via = uri.getVia();
            if (via == null) {
                // If there is no via and the URL doesn't match scope,reject it
                return DecideResult.NONE;
            }
            // If the via is within scope, accept it
            boolean result = isInScope (via, set);
            if (logger.isLoggable(Level.FINE)) {
                logger.fine("Tested via UURI '" + via +
                        " and result was " + result);
            }
            if (result) {
                return DecideResult.ACCEPT;
            }
        }
        return DecideResult.NONE;
    }
    
    /**
     * Synchronized get of prefix set to use.
     * @param o Context object.
     * 
     * @return SurtPrefixSet to use for check
     * @see org.archive.modules.deciderules.surt.SurtPrefixedDecideRule#getPrefixes()
     */
    protected synchronized SurtPrefixSet getPrefixes(/*StateProvider o*/) {
        if (surtPrefixes == null) {
            readPrefixes();
        }
        return surtPrefixes;
    }    
    
    /**
     * Patch the SURT prefix set so that it only includes the appropriate
     * prefixes.
     * @param o Context object.
     * @see org.archive.modules.deciderules.surt.SurtPrefixedDecideRule#readPrefixes()
     */
    @Override
    protected void readPrefixes() {
        buildSurtPrefixSet();
        // See whether Host or Domain was chosen
        if (getUseDomain()) {
            surtPrefixes.convertAllPrefixesToDomains();
        } else {
            surtPrefixes.convertAllPrefixesToHosts();            
        }
        dumpSurtPrefixSet();
    }        


    //check that the URI is in scope
    private boolean isInScope (Object o, SurtPrefixSet set) {
        boolean iResult = false;
        UURI u = (UURI)o;
        if (u == null) {
            return false;
        }
        String candidateSurt = u.getSurtForm();
        // also want to treat https as http
        if (candidateSurt.startsWith("https:")) {
            candidateSurt = "http:" + candidateSurt.substring(6);
        }
        if (set.containsPrefixOf(candidateSurt)){
            iResult = true;          
        }
        return iResult;
    }
}
