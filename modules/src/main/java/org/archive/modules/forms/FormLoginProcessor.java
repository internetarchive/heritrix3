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

import static org.archive.modules.CoreAttributeConstants.A_WARC_RESPONSE_HEADERS;

import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.httpclient.URIException;
import org.apache.commons.lang.StringUtils;
import org.archive.checkpointing.Checkpointable;
import org.archive.modules.CoreAttributeConstants;
import org.archive.modules.CrawlURI;
import org.archive.modules.CrawlURI.FetchType;
import org.archive.modules.Processor;
import org.archive.modules.SchedulingConstants;
import org.archive.modules.extractor.Hop;
import org.archive.modules.extractor.LinkContext;
import org.archive.modules.extractor.UriErrorLoggerModule;
import org.archive.util.JSONUtils;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;

import com.google.common.base.Function;
import com.google.common.collect.MapMaker;

/**
 * A step, post-ExtractorHTMLForms, where a followup CrawlURI to 
 * attempt a form submission may be synthesized. 
 * 
 * <p>If an HTMLForm was previously discovered (by ExtractorHTMLForms),
 * and that form appears to be a login form, and at the very least
 * the loginUsername setting is non-empty, and the current 
 * (NOT 'action') URI fits under a configured SURT prefix, then a
 * submission CrawlURI will be composed.
 * 
 * <p>This submission CrawlURI will be added to the current URI's
 * outCandidates, and prefilled with settings for a POST and 
 * input values that are a merging of: (a) original discovered 
 * in-page values; (b) the 'loginUsername' into the first plausible
 * text/email-type input field; (c) the 'loginPassword' into the 
 * first password-type input field. 
 * 
 * <p>Typically the settings 'applicableSurtPrefix', 'loginUsername', 
 * and 'loginPassword' would be set in an overlay sheet and only
 * applied to one or more sites (by SURT prefix), rather than 
 * set globally.  An example minimal set of beans to add to CXML
 * could look like:
 * 
 * <pre>
 * {@code
 * <bean id='formLoginFields' class='org.archive.spring.Sheet'>
 *  <property name='map'>
 *   <map>
 *    <entry key='formFiller.loginUsername' value='EXAMPLE_USERNAME'/>
 *    <entry key='formFiller.loginPassword' value='EXAMPLE_PASSWORD'/>
 *   </map>
 *  </property>
 * </bean>
 *
 * <bean class='org.archive.crawler.spring.SurtPrefixesSheetAssociation'>
 *  <property name='surtPrefixes'>
 *   <list>
 *    <value>http://(net,example,www,)/</value>
 *    <value>http://(com,example,</value>
 *   </list>
 *  </property>
 *  <property name='targetSheetNames'>
 *   <list>
 *    <value>formLoginFields</value>
 *   </list>
 *  </property>
 * </bean>
 * }
 * </pre>
 * 
 * <p>(Remember: https URIs are always collapsed to http form before
 * overlay-surt-prefix comparisons, so surtPrefixes in the above
 * association should always be in http form, even if the actual 
 * target URIs are https.)
 * 
 * <p>Finally, while there is not yet support for testing if the 
 * submitted CrawlURI succeeded, this processor keeps track of 
 * a count of FORMS seen that are eligible for attempts, and 
 * attempts made (for now, just once), per 'formTrackingDomain'
 * (which is either the applicableSurtPrefix or the 
 * form-origin-URI trimmed to its pathless root in SURT form). 
 * This is also added to the submission URI for logging to the
 * resulting WARC 'response' record. 
 *
 * @author gojomo
 * @version $Date$, $Revision$
 */
public class FormLoginProcessor extends Processor implements Checkpointable {
    @SuppressWarnings("unused")
    private static final long serialVersionUID = -1L;
    private static final Logger logger =
        Logger.getLogger(FormLoginProcessor.class.getName());
    
    // formProvince (String) -> count
    ConcurrentMap<String, AtomicLong> eligibleFormsSeenCount = 
            new MapMaker().makeComputingMap(new Function<String, AtomicLong>() {
                @Override
                public AtomicLong apply(String arg0) {
                    return new AtomicLong(0L);
                }
            });

    // formProvince (String) -> count
    ConcurrentMap<String, AtomicLong> eligibleFormsAttemptsCount = 
            new MapMaker().makeComputingMap(new Function<String, AtomicLong>() {
                @Override
                public AtomicLong apply(String arg0) {
                    return new AtomicLong(0L);
                }
            });
    
    /**
     * SURT prefix against which configured username/password is
     * applicable. If non-blank, configured username/password
     * will only be attempted against forms discovered on URIs 
     * which have this prefix (aka the 'form province'). 
     */
    {
        setApplicableSurtPrefix("");
    }
    public String getApplicableSurtPrefix() {
        return (String) kp.get("applicableSurtPrefix");
    }
    public void setApplicableSurtPrefix(String applicableSurtPrefix) {
        kp.put("applicableSurtPrefix",applicableSurtPrefix);
    }

    /**
     * Username (or similar) string to use in appropriate 
     * form input field. If blank, no submission will be attempted.
     * Default is blank. 
     */
    {
        setLoginUsername("");
    }
    public String getLoginUsername() {
        return (String) kp.get("loginUsername");
    }
    public void setLoginUsername(String loginUsername) {
        kp.put("loginUsername",loginUsername);
    }
    
    /**
     * Password string to use in appropriate form input field. 
     */
    {
        setLoginPassword("");
    }
    public String getLoginPassword() {
        return (String) kp.get("loginPassword");
    }
    public void setLoginPassword(String loginPassword) {
        kp.put("loginPassword",loginPassword);
    }
    
    transient protected UriErrorLoggerModule loggerModule;
    public UriErrorLoggerModule getLoggerModule() {
        return this.loggerModule;
    }
    @Autowired
    public void setLoggerModule(UriErrorLoggerModule loggerModule) {
        this.loggerModule = loggerModule;
    }
     
    public FormLoginProcessor() {
        super();
    }

    @Override
    protected boolean shouldProcess(CrawlURI curi) {
        return StringUtils.isNotEmpty(getLoginUsername());
    }
    
    @Override
    protected void innerProcess(CrawlURI curi) {
        if(!curi.getUURI().getSurtForm().startsWith(getApplicableSurtPrefix())) {
            // this URI is specifically excluded from the form-
            return;
        }
        
        // add the header with info that we are in a province 
        // (initially, may show 0 login attempts for page on
        // which form first encountered, or any earlier-encountered 
        // pages)
        String formProvince = getFormProvince(curi);
        curi.getDataList(A_WARC_RESPONSE_HEADERS).add(warcHeaderFor(formProvince)); 

        // now, maybe attempt a form-submission
        if(curi.getDataList(ExtractorHTMLForms.A_HTML_FORM_OBJECTS).size()>0) {
            for( Object formObject : curi.getDataList(ExtractorHTMLForms.A_HTML_FORM_OBJECTS)) {
                HTMLForm form = (HTMLForm) formObject;
                if(form.seemsLoginForm()) {
                    eligibleFormsSeenCount.get(formProvince).incrementAndGet();
                    if(eligibleFormsAttemptsCount.get(formProvince).get()<1) {
                        eligibleFormsAttemptsCount.get(formProvince).incrementAndGet();
                        createFormSubmissionAttempt(curi,form,formProvince); 
                    } else {
                        // note decline-to-submit: in volume, may be signal of failed first login
                        curi.getAnnotations().add("nosubmit:"+submitStatusFor(formProvince));
                    }
                    return;
                }
            }
        }
    }
    
    /**
     * Get the 'form province' - either the configured (applicableSurtPrefix) 
     * or inferred (full current server) range of URIs that is considered
     * covered by one form login
     * 
     * @param curi
     * @return
     */
    protected String getFormProvince(CrawlURI curi) {
        if (StringUtils.isNotBlank(getApplicableSurtPrefix())) {
            return getApplicableSurtPrefix();
        }
        try {
            return curi.getUURI().resolve("/").getSurtForm();
        } catch (URIException e) {
            logger.log(Level.WARNING,"error trimming to root",e);
            return curi.getClassKey(); // should never happen
        }
    }
    
    protected void createFormSubmissionAttempt(CrawlURI curi, HTMLForm templateForm, String formProvince) {
        LinkContext lc = new LinkContext.SimpleLinkContext("form/@action");
        try {
            CrawlURI submitCuri = curi.makeConsequentCandidate(templateForm.getAction(),lc, Hop.SUBMIT);
            submitCuri.setFetchType(FetchType.HTTP_POST);
            submitCuri.getData().put(
                    CoreAttributeConstants.A_SUBMIT_DATA, 
                    templateForm.asHttpClientDataWith(
                        getLoginUsername(), 
                        getLoginPassword()));
            //submitCuri.setSchedulingDirective(Math.max(curi.getSchedulingDirective()-1, 0));
            submitCuri.setSchedulingDirective(SchedulingConstants.HIGH);
            submitCuri.setForceFetch(true);
            curi.getOutCandidates().add(submitCuri);
            curi.getAnnotations().add("submit:"+templateForm.getAction());
        } catch (URIException ue) {
            loggerModule.logUriError(ue,curi.getUURI(),templateForm.getAction());
        }
    }
    
    protected String warcHeaderFor(String formProvince) {
        return "WARC-Simple-Form-Province-Status: "+submitStatusFor(formProvince);
    }
    
    protected String submitStatusFor(String formProvince) {
        return eligibleFormsAttemptsCount.get(formProvince).get()
                +","+eligibleFormsSeenCount.get(formProvince).get()
                +","+formProvince;
    }
    
    @Override
    protected JSONObject toCheckpointJson() throws JSONException {
        JSONObject json = super.toCheckpointJson();
        json.put("eligibleFormsAttemptsCount", eligibleFormsAttemptsCount);
        json.put("eligibleFormsSeenCount", eligibleFormsSeenCount);
        return json;
    }
    
    @Override
    protected void fromCheckpointJson(JSONObject json) throws JSONException {
        super.fromCheckpointJson(json);
        JSONUtils.putAllAtomicLongs(
                eligibleFormsAttemptsCount,
                json.getJSONObject("eligibleFormsAttemptsCount"));
        JSONUtils.putAllAtomicLongs(
                eligibleFormsSeenCount,
                json.getJSONObject("eligibleFormsSeenCount"));
    }
}
