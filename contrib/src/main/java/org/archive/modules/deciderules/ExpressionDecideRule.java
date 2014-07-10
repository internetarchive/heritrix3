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

import groovy.text.SimpleTemplateEngine;
import groovy.text.Template;

import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.archive.modules.CrawlURI;

/**
 * Example usage:
 * <pre> &lt;bean class="org.archive.modules.deciderules.ExpressionDecideRule">
 *     &lt;property name="groovyExpression" value='curi.via == null &amp;amp;&amp;amp; curi ==~ "^https?://(?:www\\.)?(facebook|vimeo|flickr)\\.com/.*"'/>
 * &lt/bean></pre>
 *
 * @contributor nlevitt
 */
public class ExpressionDecideRule extends PredicatedDecideRule {
    private static final long serialVersionUID = 1L;

    private static final Logger logger =
            Logger.getLogger(ExpressionDecideRule.class.getName());

    {
        setGroovyExpression("");
    }
    public void setGroovyExpression(String groovyExpression) {
        kp.put("groovyExpression", groovyExpression);
    }
    public String getGroovyExpression() {
        return (String) kp.get("groovyExpression");
    }

    protected ConcurrentHashMap<String,Template> groovyTemplates = new ConcurrentHashMap<String, Template>();
    protected Template groovyTemplate() {
        Template groovyTemplate = groovyTemplates.get(getGroovyExpression());

        if (groovyTemplate == null) {
            try {
                groovyTemplate = new SimpleTemplateEngine().createTemplate("${" + getGroovyExpression() + "}");
                groovyTemplates.put(getGroovyExpression(), groovyTemplate);
            } catch (Exception e) {
                logger.log(Level.SEVERE, "problem with groovy expression " + getGroovyExpression(), e);
            }
        }

        return groovyTemplate;
    }

    @Override
    protected boolean evaluate(CrawlURI curi) {
        HashMap<String, Object> binding = new HashMap<String, Object>();
        binding.put("curi", curi);
        return String.valueOf(true).equals(groovyTemplate().make(binding).toString());
    }
}