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
package org.archive.modules.net;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import org.archive.spring.HasKeyedProperties;
import org.archive.spring.KeyedProperties;

/**
 * RobotsHonoringPolicy represent the strategy used by the crawler 
 * for determining how robots.txt files will be honored. 
 *
 * Five kinds of policies exist:
 * <dl>
 * <dt>classic:</dt>
 *   <dd>obey the first set of robots.txt directives that apply to your 
 *   current user-agent</dd>
 * <dt>ignore:</dt>
 *   <dd>ignore robots.txt directives entirely</dd>
 * <dt>custom:</dt>
 *   <dd>obey a specific operator-entered set of robots.txt directives 
 *   for a given host</dd>
 * <dt>most-favored:</dt>
 *   <dd>obey the most liberal restrictions offered (if *any* crawler is 
 *   allowed to get a page, get it)</dd>
 * <dt>most-favored-set:</dt>
 *   <dd>given some set of user-agent patterns, obey the most liberal 
 *   restriction offered to any</dd>
 * </dl>
 *
 * The two last ones has the opportunity of adopting a different user-agent 
 * to reflect the restrictions we've opted to use.
 *
 * @author John Erik Halse
 *
 */
public class RobotsHonoringPolicy implements Serializable, HasKeyedProperties {
    private static final long serialVersionUID = 3L;

    KeyedProperties kp = new KeyedProperties();
    public KeyedProperties getKeyedProperties() {
        return kp;
    }
    
    /**
     * Policy type.
     */
    public static enum Type { 
        
        /** Obeys all robts.txt rules for the configured user-agent. */
        CLASSIC, 
        
        /** Ignores all robots rules. */
        IGNORE, 
        
        /** Defers to custom-robots setting. */
        CUSTOM, 
        
        /** Crawls URIs if the robots.txt allows any user-agent to crawl it. */
        MOST_FAVORED, 
        
        /**
         * Requires you to supply an list of alternate user-agents, and for
         * every page, if any agent of the set is allowed, the page will be
         * crawled.
         */
        MOST_FAVORED_SET 
    }

    /* set default values of overridable properties */
    {
    /**
     * Policy type. The 'classic' policy simply obeys all robots.txt rules for
     * the configured user-agent. The 'ignore' policy ignores all robots rules.
     * The 'custom' policy allows you to specify a policy, in robots.txt format,
     * as a setting. The 'most-favored' policy will crawl an URL if the
     * robots.txt allows any user-agent to crawl it. The 'most-favored-set'
     * policy requires you to supply an list of alternate user-agents, and for
     * every page, if any agent of the set is allowed, the page will be crawled.
     */
//    public final static Key<Type> TYPE = Key.make(Type.CLASSIC);
        setType(Type.CLASSIC); 
    
    /**
     * Should we masquerade as another user agent when obeying the rules
     * declared for it. Only relevant if the policy type is 'most-favored' or
     * 'most-favored-set'.
     */
//    public final static Key<Boolean> MASQUERADE = Key.make(false);
        setMasquerade(false);

    /**
     * Custom robots to use if policy type is 'custom'. Compose as if an actual
     * robots.txt file.
     */
//    public final static Key<String> CUSTOM_ROBOTS = Key.make("");
        setCustomRobots("");
    
    /**
     * Alternate user-agent values to consider using for the 'most-favored-set'
     * policy.
     */
//    public final static Key<List<String>> USER_AGENTS = Key.makeList(String.class);
        setUserAgents(new ArrayList<String>(0));
    }
    
    /**
     * Creates a new instance of RobotsHonoringPolicy.
     */
    public RobotsHonoringPolicy() {
    }
    
    /**
     * Check if policy is of a certain type.
     *
     * @param context   An object that can be resolved into a settings object.
     * @param type      the type to check against.
     * @return true     if the policy is of the submitted type
     */
    public boolean isType(Type type) {
        return type == getType();
    }

    /**
     * Get the supplied custom robots.txt
     *
     * @return String with content of alternate robots.txt
     */
    public String getCustomRobots() {
        return (String) kp.get("customRobots");
    }
    public void setCustomRobots(String customRobots) {
        kp.put("customRobots",customRobots);
    }

    /**
     * This method returns true if the crawler should masquerade as the user agent
     * which restrictions it opted to use.
     *
     * (Only relevant for  policy-types: most-favored and most-favored-set).
     *
     * @return true if we should masquerade
     */
    public boolean shouldMasquerade() {
        return (Boolean) kp.get("masquerade");
    }
    public void setMasquerade(boolean masquerade) {
        kp.put("masquerade",masquerade);
    }


    public Type getType() {
        return (Type) kp.get("type");
    }
    public void setType(Type type) {
        kp.put("type",type);
    }


    @SuppressWarnings("unchecked")
    public List<String> getUserAgents() {
        return (List<String>) kp.get("userAgents");
    }
    public void setUserAgents(List<String> userAgents) {
        kp.put("userAgents",userAgents);
    }
}
