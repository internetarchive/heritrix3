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
 
package org.archive.crawler.frontier.precedence;

import org.archive.modules.CrawlURI;
import org.archive.spring.HasKeyedProperties;
import org.archive.spring.KeyedProperties;

/**
 * UriPrecedencePolicy which assigns URIs a set value (perhaps a overridden
 * for different URIs). 
 */
public class BaseUriPrecedencePolicy extends UriPrecedencePolicy 
implements HasKeyedProperties {
    private static final long serialVersionUID = -8247330811715982746L;
    
    protected KeyedProperties kp = new KeyedProperties();
    public KeyedProperties getKeyedProperties() {
        return kp;
    }
    
    /** constant precedence to assign; default is 1 */
    {
        setBasePrecedence(1);
    }
    public int getBasePrecedence() {
        return (Integer) kp.get("basePrecedence");
    }
    public void setBasePrecedence(int precedence) {
        kp.put("basePrecedence", precedence);
    }
    
    /* (non-Javadoc)
     * @see org.archive.crawler.frontier.precedence.UriPrecedencePolicy#uriScheduled(org.archive.crawler.datamodel.CrawlURI)
     */
    @Override
    public void uriScheduled(CrawlURI curi) {
        curi.setPrecedence(calculatePrecedence(curi)); 
    }

    /**
     * Calculate the precedence value for the given URI. 
     * @param curi CrawlURI to evaluate
     * @return int precedence for URI
     */
    protected int calculatePrecedence(CrawlURI curi) {
        return getBasePrecedence();
    }
}
