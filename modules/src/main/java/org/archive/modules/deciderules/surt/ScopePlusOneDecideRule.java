/* ScopePlusOneDecideRule
*
* Created on Aug 22, 2005
*
* Copyright 2005 Regents of the University of California, All rights reserved
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
package org.archive.modules.deciderules.surt;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.archive.modules.ProcessorURI;
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
    protected DecideResult innerDecide(ProcessorURI uri) {
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
