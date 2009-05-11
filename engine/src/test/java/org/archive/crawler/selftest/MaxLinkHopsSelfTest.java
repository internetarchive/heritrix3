/* MaxLinkHopsSelfTest
 *
 * Created on Feb 17, 2004
 *
 * Copyright (C) 2004 Internet Archive.
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
package org.archive.crawler.selftest;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;



/**
 * Test the max-link-hops setting.
 *
 * @author stack
 * @version $Id$
 */
public class MaxLinkHopsSelfTest
    extends SelfTestBase
{
    final private static Set<String> EXPECTED = Collections.unmodifiableSet(
            new HashSet<String>(Arrays.asList(new String[] {
            "index.html", "1.html", "2.html", "3.html", "robots.txt"
    })));
    
    @Override
    protected void verify() throws Exception {
        Set<String> files = filesInArcs();
        assertEquals("ARC contents not as expected",EXPECTED,files);
    }
    
    @Override
    protected String changeGlobalConfig(String config) {
        String replacement = 
            "<bean class=\"org.archive.modules.deciderules.TooManyHopsDecideRule\">\n" + 
            "     <property name=\"maxHops\" value=\"3\"/>\n" + 
            "    </bean>";
        String retVal = config.replaceFirst(
                "(?s)<bean class=\"org.archive.modules.deciderules.TooManyHopsDecideRule\".*?</bean>", 
                replacement);
        return super.changeGlobalConfig(retVal);
    }
}

