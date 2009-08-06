/* ClassKeyMatchesRegExpDecideRule
*
* $Id$
*
* Created on Apr 4, 2005
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
package org.archive.crawler.deciderules;


import org.archive.crawler.framework.CrawlController;
import org.archive.modules.CrawlURI;
import org.archive.modules.ProcessorURI;
import org.archive.modules.deciderules.MatchesRegExpDecideRule;


/**
 * Rule applies configured decision to any CrawlURI class key -- i.e.
 * {@link CrawlURI#getClassKey()} -- matches matches supplied regexp.
 *
 * @author gojomo
 */
public class ClassKeyMatchesRegExpDecideRule extends MatchesRegExpDecideRule {

    private static final long serialVersionUID = 3L;


    final private CrawlController controller;
    
    /**
     * Usual constructor. 
     */
    public ClassKeyMatchesRegExpDecideRule(CrawlController controller) {
        this.controller = controller;
    }

    
    @Override
    protected String getString(ProcessorURI uri) {
        CrawlURI curi = (CrawlURI)uri;
        return controller.getFrontier().getClassKey(curi);
    }

}