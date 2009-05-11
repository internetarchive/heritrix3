/* $Id: FetchStatusNotMatchesRegExpDecideRule.java 4649 2006-09-25 17:16:55Z paul_jack $
*
* Created on Sep 4, 2006
*
* Copyright (C) 2006 Olaf Freyer.
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
package org.archive.modules.deciderules;

import org.archive.modules.ProcessorURI;


public class FetchStatusNotMatchesRegExpDecideRule
extends FetchStatusMatchesRegExpDecideRule {

    private static final long serialVersionUID = -2220182698344063577L;
//    private  final Logger logger = Logger.getLogger(this.getClass().getName());
    
    /**
     * Usual constructor. 
     * @param name
     */
    public FetchStatusNotMatchesRegExpDecideRule() {
    }

    /**
     * Evaluate whether given object's FetchStatus does not match 
     * configured regexp (by reversing the superclass's answer).
     * 
     * @param object Object to make decision about.
     * @return true if the regexp is not matched
     */
    @Override
    protected boolean evaluate(ProcessorURI object) {
        return ! super.evaluate(object);
    }
}