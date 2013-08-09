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

package org.archive.modules.forms;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;

import org.apache.commons.lang.StringUtils;
import org.archive.io.ReplayCharSequence;
import org.archive.modules.CrawlURI;
import org.archive.modules.extractor.Extractor;
import org.archive.modules.extractor.ExtractorHTML;
import org.archive.util.TextUtils;

/**
 * Extracts extra information about FORMs in HTML, loading this
 * into the CrawlURI (for potential later use by FormLoginProcessor)
 * and adding a small annotation to the crawl.log. 
 * 
 * Must come after ExtractorHTML, as it relies on information left
 * in the CrawlURI's A_FORM_OFFSETS data key. 
 * 
 * By default (with 'extractAllForms' equal false), only 
 * saves-to-CrawlURI and annotates forms that appear to be login
 * forms, by the test HTMLForm.seemsLoginForm(). 
 * 
 * Typical CXML configuration would be, first, as top-level named beans:
 * 
 * <pre>
 * {@code
 * <bean id="extractorForms" class="org.archive.modules.forms.ExtractorHTMLForms">
 *   <!-- <property name="extractAllForms" value="false" /> -->
 * </bean>
 * <bean id="formFiller" class="org.archive.modules.forms.FormLoginProcessor">
 *   <!-- generally these are overlaid with sheets rather than set directly -->
 *   <!-- <property name="applicableSurtPrefix" value="" /> -->
 *   <!-- <property name="loginUsername" value="" /> -->
 *   <!-- <property name="loginPassword" value="" /> -->
 * </bean> 
 * }
 * </pre>
 *
 * Then, inside the fetch chain, after all other extractors:
 * 
 * <pre>
 * {@code
 * <bean id="fetchProcessors" class="org.archive.modules.FetchChain">
 *  <property name="processors">
 *   <list>
 *    ...ALL USUAL PREPROCESSORS/FETCHERS/EXTRACTORS HERE, THEN...
 *    <ref bean="extractorForms"/>
 *    <ref bean="formFiller"/>
 *   </list>
 *  </property>
 * </bean>
 * }
 * </pre>
 *
 * NOTE: This processor may open a ReplayCharSequence from the 
 * CrawlURI's Recorder, without closing that ReplayCharSequence, to allow
 * reuse by later processors in sequence. In the usual (Heritrix) case, a 
 * call after all processing to the Recorder's endReplays() method ensures
 * timely close of any reused ReplayCharSequences. Reuse of this processor
 * elsewhere should ensure a similar cleanup call to Recorder.endReplays()
 * occurs. 
 * 
 * @contributor gojomo
 */
public class ExtractorHTMLForms extends Extractor {
    @SuppressWarnings("unused")
    private static final long serialVersionUID = 2L;

    public static final String A_HTML_FORM_OBJECTS = "html-form-objects";

    private static Logger logger =
        Logger.getLogger(ExtractorHTMLForms.class.getName());
 
    /**
     * If true, report all FORMs. If false, report only those that
     * appear to be a login-enabling FORM. 
     * Default is false.
     */
    {
        setExtractAllForms(false);
    }
    public boolean getExtractAllForms() {
        return (Boolean) kp.get("extractAllForms");
    }
    public void setExtractAllForms(boolean extractAllForms) {
        kp.put("extractAllForms",extractAllForms);
    }
    
    public ExtractorHTMLForms() {
    }

    protected boolean shouldProcess(CrawlURI uri) {
        return uri.containsDataKey(ExtractorHTML.A_FORM_OFFSETS);
    }

    public void extract(CrawlURI curi) {
        try {
            ReplayCharSequence cs = curi.getRecorder().getContentReplayCharSequence();
            analyze(curi, cs);
        } catch (IOException e) {
            curi.getNonFatalFailures().add(e);
            logger.log(Level.WARNING,"Failed get of replay char sequence in " +
                    Thread.currentThread().getName(), e);
        }
    }

    /**
     * Run analysis: find form METHOD, ACTION, and all INPUT names/values
     * 
     * Log as configured. 
     * 
     * @param curi CrawlURI we're processing.
     * @param cs Sequence from underlying ReplayCharSequence. This
     * is TRANSIENT data. Make a copy if you want the data to live outside
     * of this extractors' lifetime.
     */
    protected void analyze(CrawlURI curi, CharSequence cs) {
        for (Object offset : curi.getDataList(ExtractorHTML.A_FORM_OFFSETS)) {
            int offsetInt = (Integer) offset;
            CharSequence relevantSequence = cs.subSequence(offsetInt, cs.length());
            String method = findAttributeValueGroup("(?i)^[^>]*\\smethod\\s*=\\s*([^>\\s]+)[^>]*>",1,relevantSequence);
            String action = findAttributeValueGroup("(?i)^[^>]*\\saction\\s*=\\s*([^>\\s]+)[^>]*>",1,relevantSequence);
            HTMLForm form = new HTMLForm();
            form.setMethod(method);
            form.setAction(action); 
            for(CharSequence input : findGroups("(?i)(<input\\s[^>]*>)|(</?form>)",1,relevantSequence)) {
                String type = findAttributeValueGroup("(?i)^[^>]*\\stype\\s*=\\s*([^>\\s]+)[^>]*>",1,input);
                String name = findAttributeValueGroup("(?i)^[^>]*\\sname\\s*=\\s*([^>\\s]+)[^>]*>",1,input);
                String value = findAttributeValueGroup("(?i)^[^>]*\\svalue\\s*=\\s*([^>\\s]+)[^>]*>",1,input);
                form.addField(type,name,value);
            }
            if (form.seemsLoginForm() || getExtractAllForms()) {
                curi.getDataList(A_HTML_FORM_OBJECTS).add(form);
                curi.getAnnotations().add(form.asAnnotation());
            }
        }
    }
    
    protected List<CharSequence> findGroups(String pattern, int groupNumber, CharSequence cs) {
        ArrayList<CharSequence> groups = new ArrayList<CharSequence>();
        Matcher m = TextUtils.getMatcher(pattern, cs);
        try {
            while(m.find()) {
                if(m.group(groupNumber)!=null) {
                    groups.add(cs.subSequence(m.start(groupNumber),m.end(groupNumber)));
                } else {
                    // group not found: end find condition
                    break; 
                }
            } 
            return groups;
        } finally {
            TextUtils.recycleMatcher(m);
        }
    }
    
    protected String findAttributeValueGroup(String pattern, int groupNumber, CharSequence cs) {
        Matcher m = TextUtils.getMatcher(pattern, cs);
        try {
            if(m.find()) {
                String value = m.group(groupNumber);
                /*
                 * In a case like this <input name="foo"/> the group here will
                 * be "foo"/ ... it's difficult to adjust the regex to avoid
                 * slurping that trailing slash, so handle it here
                 */
                value = StringUtils.removeEnd(value, "'/");
                value = StringUtils.removeEnd(value, "\"/");
                value = StringUtils.strip(value, "\'\""); // strip quotes if present
                return value;
            } else {
                return null; 
            }
        } finally {
            TextUtils.recycleMatcher(m);
        }
    }
}

