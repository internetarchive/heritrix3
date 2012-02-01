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

import org.archive.modules.CrawlURI;

/**
 * Rule which applies the configured decision only if a 
 * test evaluates to true. Subclasses override evaluate()
 * to establish the test. 
 *
 * @author gojomo
 */
public abstract class PredicatedDecideRule extends DecideRule {

    private static final long serialVersionUID = 1L;

    {
        setDecision(DecideResult.ACCEPT);
    }
    public DecideResult getDecision() {
        return (DecideResult) kp.get("decision");
    }
    public void setDecision(DecideResult decision) {
        kp.put("decision",decision);
    }
    
    public PredicatedDecideRule() {
    }

    @Override
    protected DecideResult innerDecide(CrawlURI uri) {
        if (evaluate(uri)) {
            return getDecision();
        }
        return DecideResult.NONE;
    }

    protected abstract boolean evaluate(CrawlURI object);
}
