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
 
package org.archive.crawler.spring;

import org.archive.modules.deciderules.DecideRule;
import org.archive.spring.Sheet;
import org.springframework.beans.factory.annotation.Required;
import org.springframework.core.Ordered;

/**
 * Sheet applied on the basis of DecideRules. 
 * 
 * @contributor gojomo
 */
public class SheetForDecideRuled extends Sheet implements Ordered, Comparable<SheetForDecideRuled> {
    DecideRule rules;
    int order = 0; 
    
    public DecideRule getRules() {
        return rules;
    }
    @Required
    public void setRules(DecideRule rules) {
        this.rules = rules;
    }

    public int getOrder() {
        return order;
    }
    public void setOrder(int order) {
        this.order = order;
    }

    // compare on the basis of Ordered value
    public int compareTo(SheetForDecideRuled o) {
        return order - ((Ordered)o).getOrder();
    }
}
