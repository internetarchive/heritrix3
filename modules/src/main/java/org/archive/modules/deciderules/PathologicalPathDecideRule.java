/* PathologicalPathDecideRule
*
* $Id$
*
* Created on Apr 1, 2005
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

import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

import org.archive.modules.ProcessorURI;


/**
 * Rule REJECTs any URI which contains an excessive number of identical, 
 * consecutive path-segments (eg http://example.com/a/a/a/boo.html == 3 '/a' 
 * segments)
 *
 * @author gojomo
 */
public class PathologicalPathDecideRule extends DecideRule {

    private static final long serialVersionUID = 3L;

    /**
     * Number of times the pattern should be allowed to occur. This rule returns
     * its decision (usually REJECT) if a path-segment is repeated more than
     * number of times.
     */
    {
        setMaxRepetitions(2);
    }
    public int getMaxRepetitions() {
        return (Integer) kp.get("maxRepetitions");
    }
    public void setMaxRepetitions(int maxRepetitions) {
        kp.put("maxRepetitions", maxRepetitions);
    }

    private AtomicReference<Pattern> pattern = new AtomicReference<Pattern>();
    
    /** Constructs a new PathologicalPathFilter.
     *
     * @param name the name of the filter.
     */
    public PathologicalPathDecideRule() {
    }


    @Override
    protected DecideResult innerDecide(ProcessorURI uri) {
        int maxRep = getMaxRepetitions();
        Pattern p = getPattern(maxRep);
        if (p.matcher(uri.getUURI().toString()).matches()) {
            return DecideResult.REJECT;
        } else {
            return DecideResult.PASS;
        }
    }

    /** 
     * Construct the regexp string to be matched against the URI.
     * @param o an object to extract a URI from.
     * @return the regexp pattern.
     */
    private Pattern getPattern(int maxRep) {
        // race no concern: assignment is atomic, happy with any last value
        Pattern p = pattern.get();
        if (p != null) {
            return p;
        }
        String regex = constructRegexp(maxRep);
        p = Pattern.compile(regex);
        pattern.set(p);
        return p;
    }
    
    protected String constructRegexp(int rep) {
        return (rep == 0) ? null : ".*?/(.*?/)\\1{" + rep + ",}.*";
    }
}
