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


import java.io.Serializable;

import org.archive.modules.CrawlURI;
import org.archive.spring.HasKeyedProperties;
import org.archive.spring.KeyedProperties;

@SuppressWarnings("serial")
public abstract class DecideRule implements Serializable, HasKeyedProperties {
    protected KeyedProperties kp = new KeyedProperties();
    public KeyedProperties getKeyedProperties() {
        return kp;
    }
    
    {
        setEnabled(true);
    }
    public boolean getEnabled() {
        return (Boolean) kp.get("enabled");
    }
    public void setEnabled(boolean enabled) {
        kp.put("enabled",enabled);
    }

    protected String comment = "";
    public String getComment() {
        return comment;
    }
    public void setComment(String comment) {
        this.comment = comment;
    }
    
    public DecideRule() {

    }
    
    public DecideResult decisionFor(CrawlURI uri) {
        if (!getEnabled()) {
            return DecideResult.NONE;
        }
        DecideResult result = innerDecide(uri);
        if (result == DecideResult.NONE) {
            return result;
        }

        return result;
    }
    
    
    protected abstract DecideResult innerDecide(CrawlURI uri);
    
    
    public DecideResult onlyDecision(CrawlURI uri) {
        return null;
    }

    public boolean accepts(CrawlURI uri) {
        return DecideResult.ACCEPT == decisionFor(uri);
    }

}
