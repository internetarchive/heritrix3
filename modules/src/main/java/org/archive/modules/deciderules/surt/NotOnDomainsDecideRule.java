/* NotOnDomainsDecideRule
*
* $Id: NotOnDomainsDecideRule.java 4649 2006-09-25 17:16:55Z paul_jack $
*
* Created on Apr 5, 2005
*
* Copyright (C) 2005 Internet Archive.
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

import org.archive.modules.ProcessorURI;


/**
 * Rule applies configured decision to any URIs that are
 * *not* in one of the domains in the configured set of
 * domains, filled from the seed set. 
 *
 * @author gojomo
 */
public class NotOnDomainsDecideRule extends OnDomainsDecideRule {

    private static final long serialVersionUID = -1634035244888724934L;
    
    //private static final Logger logger =
    //    Logger.getLogger(NotOnDomainsDecideRule.class.getName());
    /**
     * Usual constructor. 
     * @param name
     */
    public NotOnDomainsDecideRule() {
    }

    /**
     * Evaluate whether given object's URI is NOT in the set of
     * domains -- simply reverse superclass's determination
     * 
     * @param object to evaluate
     * @return true if URI is not in domain set
     */
    protected boolean evaluate(ProcessorURI object) {
        boolean superDecision = super.evaluate(object);
        return !superDecision;
    }
}
