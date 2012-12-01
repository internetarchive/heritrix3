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
package org.archive.modules.extractor;

import groovy.text.SimpleTemplateEngine;
import groovy.text.Template;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;

import org.apache.commons.httpclient.URIException;
import org.archive.io.ReplayCharSequence;
import org.archive.modules.CrawlURI;
import org.archive.modules.fetcher.FetchStatusCodes;
import org.archive.util.TextUtils;

/**
 * An extractor that uses regular expressions to find strings in the fetched
 * content of a URI, and constructs outlink URIs from those strings.
 * 
 * <p>
 * The crawl operator configures these parameters:
 * 
 * <ul>
 * <li> <code>uriRegex</code>: a regular expression to match against the url</li>
 * <li> <code>contentRegexes</code> a map of named regular expressions { name =>
 * regex } to run against the content</li>
 * <li> <code>template</code>: the template for constructing the outlinks</li>
 * </ul>
 * 
 * <p>
 * The URI is checked against <code>uriRegex</code>. The match is done using
 * {@link Matcher#matches()}, so the full URI string must match, not just a
 * substring. If it does match, then the matching groups are available to the
 * URI-building template as <code>${uriRegex[n]}</code>. If it does not match,
 * processing of the URI is finished and no outlinks are extracted.
 * 
 * <p>
 * Then the extractor looks for matches for each of the
 * <code>contentRegexes</code> in the fetched content. If any of the regular
 * expressions produce no matches, processing of the URI is finished and no
 * outlinks are extracted. If at least one match is found for each regular
 * expression, then an outlink is constructed, using the URI-building template,
 * for every combination of matches. The matching groups are available to the
 * template as <code>${name[n]}</code>.
 * 
 * <p>
 * Outlinks are constructed using the URI-building <code>template</code>.
 * Variable interpolation using the familiar ${...} syntax is supported. The
 * template is evaluated for each combination of regular expression matches
 * found, and the matching groups are available to the template as
 * <code>${regexName[n]}</code>. An example template might look like:
 * <code>http://example.org/${uriRegex[1]}/foo?bar=${myContentRegex[0]}</code>.
 * 
 * <p>
 * The template is evaluated as a Groovy Template, so further capabilities
 * beyond simple variable interpolation are available.
 * 
 * @see <a
 *      href="http://groovy.codehaus.org/Groovy+Templates">http://groovy.codehaus.org/Groovy+Templates</a>
 * 
 * @contributor nlevitt
 * @contributor travis
 */
public class ExtractorMultipleRegex extends Extractor {

    private static final Logger LOGGER =
        Logger.getLogger(ExtractorMultipleRegex.class.getName());

    {
        setUriRegex("");
    }
    /**
     * Regular expression against which to match the URI. If the URI matches,
     * then the matching groups are available to the URI-building template as
     * <code>${uriRegex[n]}</code>. If it does not match, processing of this URI
     * is finished and no outlinks are extracted.
     */
    public void setUriRegex(String uriRegex) {
        kp.put("uriRegex", uriRegex);
    }
    public String getUriRegex() {
        return (String) kp.get("uriRegex");
    }
    
    {
        setContentRegexes(new LinkedHashMap<String, String>());
    }
    /**
     * A map of { name => regex }. The extractor looks for matches for each
     * regular expression in the content of the URI being processed. If any of
     * the regular expressions produce no matches, processing of the URI is
     * finished and no outlinks are extracted. If at least one match is found
     * for each regular expression, then an outlink is constructed for every
     * combination of matches. The matching groups are available to the
     * URI-building template as <code>${name[n]}</code>.
     */
    public void setContentRegexes(Map<String, String> contentRegexes) {
        kp.put("contentRegexes", contentRegexes);
    }
    @SuppressWarnings("unchecked")
    public Map<String, String> getContentRegexes() {
        return (Map<String, String>) kp.get("contentRegexes");
    }
    
    {
        setTemplate("");
    }
    /**
     * URI-building template. Provides variable interpolation using the familiar
     * ${...} syntax. The template is evaluated for each combination of regular
     * expression matches found, and the matching groups are available to the
     * template as <code>${regexName[n]}</code>. An example template might look
     * like:
     * <code>http://example.org/${uriRegex[1]}/foo?bar=${myContentRegex[0]}</code>.
     * 
     * <p>
     * The template is evaluated as a Groovy Template, so further capabilities
     * beyond simple variable interpolation are available.
     * 
     * @see <a
     *      href="http://groovy.codehaus.org/Groovy+Templates">http://groovy.codehaus.org/Groovy+Templates</a>
     */
    public void setTemplate(String template) {
        kp.put("template", template);
    }
    public String getTemplate() {
        return (String) kp.get("template");
    }
    
    /*
     * Cache of groovy templates because they're a little expensive to create.
     * Needs to be a map rather than a single value to handle overrides.
     * XXX confirm Template is thread safe
     */
    protected ConcurrentHashMap<String,Template> groovyTemplates = new ConcurrentHashMap<String, Template>();
    protected Template groovyTemplate() {
        Template groovyTemplate = groovyTemplates.get(getTemplate());
        
        if (groovyTemplate == null) {
            try {
                groovyTemplate = new SimpleTemplateEngine().createTemplate(getTemplate());
                groovyTemplates.put(getTemplate(), groovyTemplate);
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "problem with groovy template " + getTemplate(), e);
            }
        }
        
        return groovyTemplate;
    }
    
    
    @Override
    protected boolean shouldProcess(CrawlURI uri) {
        if (uri.getContentLength() <= 0) {
            return false;
        }
        if (!getExtractorParameters().getExtract404s()
                && uri.getFetchStatus() == FetchStatusCodes.S_NOT_FOUND) {
            return false;
        }
        return true;
    }
    
    protected class MatchList extends LinkedList<GroupList> {
        private static final long serialVersionUID = 1L;
        public MatchList(String regex, CharSequence cs) {
            Matcher matcher = TextUtils.getMatcher(regex, cs);
            while (matcher.find()) {
                add(new GroupList(matcher));
            }
        }
        public MatchList(GroupList... groupList) {
            for (GroupList x: groupList) {
                add(x);
            }
        }
    };
    protected class GroupList extends LinkedList<String> {
        private static final long serialVersionUID = 1L;
        public GroupList(MatchResult matchResult) {
            for (int i = 0; i <= matchResult.groupCount(); i++) {
                add(matchResult.group(i));
            }
        }
    };
    
    @Override
    public void extract(CrawlURI curi) {
        // { regex name -> list of matches }
        Map<String, MatchList> matchLists;

        // uri regex
        Matcher matcher = TextUtils.getMatcher(getUriRegex(), curi.getURI());
        if (matcher.matches()) {
            matchLists = new LinkedHashMap<String,MatchList>();
            matchLists.put("uriRegex", new MatchList(new GroupList(matcher)));
        } else {
            return; // if uri regex doesn't match, we're done
        }
        
        ReplayCharSequence cs;
        try {
            cs = curi.getRecorder().getContentReplayCharSequence();
        } catch (IOException e) {
            curi.getNonFatalFailures().add(e);
            LOGGER.log(Level.WARNING, "Failed get of replay char sequence in "
                    + Thread.currentThread().getName(), e);
            return;
        }
        
        // run all the regexes on the content and cache results
        for (String regexName: getContentRegexes().keySet()) {
            String regex = getContentRegexes().get(regexName);
            MatchList matchList = new MatchList(regex, cs);
            if (matchList.isEmpty()) {
                return; // no match found for regex, so we can stop now
            }
            matchLists.put(regexName, matchList);
        }

        /*
         * If we have 3 regexes, the first one has 1 match, second has 12
         * matches, third has 3 matches, then we have 36 combinations of
         * matches, thus 36 outlinks to extracted.
         */
        int numOutlinks = 1;
        for (MatchList matchList: matchLists.values()) {
            numOutlinks *= matchList.size();
        }
        
        String[] regexNames = matchLists.keySet().toArray(new String[0]);
        for (int i = 0; i < numOutlinks; i++) {
            Map<String, Object> bindings = makeBindings(matchLists, regexNames, i);
            buildAndAddOutlink(curi, bindings);
        }
    }
    
    // bindings are the variables available to populate the template
    // { String patternName => List<String> groups }  
    protected Map<String,Object> makeBindings(Map<String, MatchList> matchLists,
            String[] regexNames, int outlinkIndex) {
        Map<String,Object> bindings = new LinkedHashMap<String,Object>();

        int tmp = outlinkIndex;
        for (int regexIndex = 0; regexIndex < regexNames.length; regexIndex++) {
            MatchList matchList = matchLists.get(regexNames[regexIndex]);
            int matchIndex = tmp % matchList.size();
            bindings.put(regexNames[regexIndex], matchList.get(matchIndex));
            tmp = tmp / matchList.size();
        }
        
        return bindings;
    }
    
    protected void buildAndAddOutlink(CrawlURI curi, Map<String, Object> bindings) {
        String outlinkUri = groovyTemplate().make(bindings).toString();
        
        try {
            Link.addRelativeToBase(curi, 
                    getExtractorParameters().getMaxOutlinks(), outlinkUri, 
                    HTMLLinkContext.INFERRED_MISC, Hop.INFERRED);
        } catch (URIException e) {
            logUriError(e, curi.getUURI(), outlinkUri);
        }
    }
}
