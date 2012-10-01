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

package org.archive.modules;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;

import org.archive.modules.fetcher.UserAgentProvider;
import org.archive.modules.net.RobotsPolicy;
import org.archive.spring.BeanFieldsPatternValidator;
import org.archive.spring.HasKeyedProperties;
import org.archive.spring.HasValidator;
import org.archive.spring.KeyedProperties;
import org.archive.util.ArchiveUtils;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.Validator;

/**
 * Basic crawl metadata, as consulted by functional modules and
 * recorded in ARCs/WARCs.
 * 
 * @contributor pjack
 */
public class CrawlMetadata 
implements UserAgentProvider, 
           Serializable,
           HasKeyedProperties,
           HasValidator,
           InitializingBean {
    private static final long serialVersionUID = 1L;

    protected KeyedProperties kp = new KeyedProperties();
    public KeyedProperties getKeyedProperties() {
        return kp;
    }
    
    /**
     * Robots policy name
     */
    {
        setRobotsPolicyName("obey");
    }
    public String getRobotsPolicyName() {
        return (String) kp.get("robotsPolicyName");
    }
    @Autowired(required=false)
    public void setRobotsPolicyName(String policy) {
        kp.put("robotsPolicyName",policy);
    }
    
    /** Map of all available RobotsPolicies, by name, to choose from. 
     * assembled from declared instances in configuration plus the standard
     * 'obey' (aka 'classic') and 'ignore' policies. */
    protected Map<String,RobotsPolicy> availableRobotsPolicies = new HashMap<String,RobotsPolicy>();
    public Map<String,RobotsPolicy> getAvailableRobotsPolicies() {
        return availableRobotsPolicies;
    }
    @Autowired(required=false)
    public void setAvailableRobotsPolicies(Map<String,RobotsPolicy> policies) {
        availableRobotsPolicies = policies;
        ensureStandardPoliciesAvailable();
    }
    protected void ensureStandardPoliciesAvailable() {
        availableRobotsPolicies.putAll(RobotsPolicy.STANDARD_POLICIES);
    }
    
    /**
     * Get the currently-effective RobotsPolicy, as specified by the
     * string name and chosen from the full available map. (Setting 
     * a different policy for some sites/URL patterns is best acheived
     * by establishing a setting overlay for the robotsPolicyName 
     * property.)
     */ 
    public RobotsPolicy getRobotsPolicy() {
        return availableRobotsPolicies.get(getRobotsPolicyName());
    } 

    protected String operator = "";
    public String getOperator() {
        return operator;
    }
    public void setOperator(String operatorName) {
        this.operator = operatorName;
    }

    protected String description = ""; 
    public String getDescription() {
        return description;
    }
    public void setDescription(String description) {
        this.description = description;
    }
    
    {
        setUserAgentTemplate("Mozilla/5.0 (compatible; heritrix/@VERSION@ +@OPERATOR_CONTACT_URL@)");
    }
    public String getUserAgentTemplate() {
        return (String) kp.get("userAgentTemplate");
    }
    public void setUserAgentTemplate(String template) {
        // TODO compile pattern outside method
//        if(!template.matches("^.*\\+@OPERATOR_CONTACT_URL@.*$")) {
//            throw new IllegalArgumentException("bad user-agent: "+template);
//        }
        kp.put("userAgentTemplate",template);
    }
    
    {
        setOperatorFrom("");
    }
    public String getOperatorFrom() {
        return (String) kp.get("operatorFrom");
    }
    public void setOperatorFrom(String operatorFrom) {
        // TODO compile pattern outside method
//        if(!operatorFrom.matches("^(\\s*|\\S+@[-\\w]+\\.[-\\w\\.]+)$")) {
//            throw new IllegalArgumentException("bad operatorFrom: "+operatorFrom);
//        }
        kp.put("operatorFrom",operatorFrom);
    }
    
    {
        // set default to illegal value
        kp.put("operatorContactUrl","ENTER-A-CONTACT-HTTP-URL-FOR-CRAWL-OPERATOR");
    }
    public String getOperatorContactUrl() {
        return (String) kp.get("operatorContactUrl");
    }
    public void setOperatorContactUrl(String operatorContactUrl) {
        // TODO compile pattern outside method
//        if(!operatorContactUrl.matches("^https?://.*$")) {
//            throw new IllegalArgumentException("bad operatorContactUrl: "+operatorContactUrl);
//        }
        kp.put("operatorContactUrl",operatorContactUrl);
    }


    protected String audience = ""; 
    public String getAudience() {
        return audience;
    }
    public void setAudience(String audience) {
        this.audience = audience;
    }
   
    protected String organization = ""; 
    public String getOrganization() {
        return organization;
    }
    public void setOrganization(String organization) {
        this.organization = organization;
    }
    
    public String getUserAgent() {
        String userAgent = getUserAgentTemplate();
        String contactURL = getOperatorContactUrl();
        userAgent = userAgent.replaceFirst("@OPERATOR_CONTACT_URL@", contactURL);
        userAgent = userAgent.replaceFirst("@VERSION@",
                Matcher.quoteReplacement(ArchiveUtils.VERSION));
        return userAgent;
    }

    protected String jobName;
    public String getJobName() {
        return jobName;
    }
    public void setJobName(String jobName) {
        this.jobName = jobName;
    }

    public String getFrom() {
        return getOperatorFrom();
    }

    public void afterPropertiesSet() {
        // force revalidation, throwing exception if invalid
        setOperatorContactUrl(getOperatorContactUrl());
        ensureStandardPoliciesAvailable();
    }

    protected static Validator VALIDATOR = new BeanFieldsPatternValidator(
            CrawlMetadata.class,
            "userAgentTemplate", 
            "^.*\\+@OPERATOR_CONTACT_URL@.*$", 
            "You must supply a userAgentTemplate value that includes " +
            "the string \"@OPERATOR_CONTACT_URL@\" where your crawl" +
            "contact URL will appear.",
            
            "operatorContactUrl", 
            "^https?://.*$", 
            "You must supply an HTTP(S) URL which will be included " +
            "in your user-agent and should explain the purpose of your " +
            "crawl and how to contact the crawl operator in the event " +
            "of webmaster issues.",
            
            "operatorFrom", 
            "^(\\s*|\\S+@[-\\w]+\\.[-\\w\\.]+)|()$",
            "If not blank, operatorFrom must be an email address.");
    
    public Validator getValidator() {
        return VALIDATOR;
    }
}
