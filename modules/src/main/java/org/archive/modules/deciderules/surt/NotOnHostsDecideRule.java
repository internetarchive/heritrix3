/* NotOnHostsDecideRule
*
* $Id: NotOnHostsDecideRule.java 4649 2006-09-25 17:16:55Z paul_jack $
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
 * Rule applies configured decision to any URIs that
 * are *not* on one of the hosts in the configured set of
 * hosts, filled from the seed set. 
 *
 * @author gojomo
 */
public class NotOnHostsDecideRule extends OnHostsDecideRule {

    private static final long serialVersionUID = 1512825197255050412L;


    /**
     * Usual constructor. 
     */
    public NotOnHostsDecideRule() {
    }

    /**
     * Evaluate whether given object's URI is NOT in the set of
     * hosts -- simply reverse superclass's determination
     * 
     * @param object Object to evaluate
     * @return true if URI not in set
     */
    @Override
    protected boolean evaluate(ProcessorURI object) {
        boolean superDecision = super.evaluate(object);
        return !superDecision;
    }
}
