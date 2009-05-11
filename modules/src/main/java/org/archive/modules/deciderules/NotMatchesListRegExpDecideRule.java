/* NotMatchesListRegExpDecideRule
 * 
 * $Id: NotMatchesListRegExpDecideRule.java 4721 2006-11-14 20:03:18Z stack-sf $
 * 
 * Created on 30.5.2005
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
package org.archive.modules.deciderules;

import org.archive.modules.ProcessorURI;


/**
 * Rule applies configured decision to any URIs which do *not*
 * match the supplied regexp.
 *
 * @author Kristinn Sigurdsson
 */
public class NotMatchesListRegExpDecideRule extends MatchesListRegExpDecideRule {

    private static final long serialVersionUID = 8691360087063555583L;

    //private static final Logger logger =
    //    Logger.getLogger(NotMatchesListRegExpDecideRule.class.getName());


    /**
     * Usual constructor. 
     * @param name
     */
    public NotMatchesListRegExpDecideRule() {
    }

    /**
     * Evaluate whether given object's string version does not match 
     * configured regexps (by reversing the superclass's answer).
     * 
     * @param object Object to make decision about.
     * @return true if the regexps are not matched
     */
    @Override
    protected boolean evaluate(ProcessorURI object) {
        return ! super.evaluate(object);
    }
}
