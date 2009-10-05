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

import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

import org.archive.modules.CrawlURI;


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
    protected DecideResult innerDecide(CrawlURI uri) {
        int maxRep = getMaxRepetitions();
        Pattern p = getPattern(maxRep);
        if (p.matcher(uri.getUURI().toString()).matches()) {
            return DecideResult.REJECT;
        } else {
            return DecideResult.NONE;
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
