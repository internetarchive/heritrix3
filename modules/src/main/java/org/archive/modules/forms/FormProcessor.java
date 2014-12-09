package org.archive.modules.forms;

import static org.archive.modules.CoreAttributeConstants.A_WARC_RESPONSE_HEADERS;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.httpclient.URIException;
import org.archive.checkpointing.Checkpointable;
import org.archive.modules.CoreAttributeConstants;
import org.archive.modules.CrawlURI;
import org.archive.modules.CrawlURI.FetchType;
import org.archive.modules.Processor;
import org.archive.modules.SchedulingConstants;
import org.archive.modules.extractor.Hop;
import org.archive.modules.extractor.LinkContext;
import org.archive.modules.extractor.UriErrorLoggerModule;
import org.archive.modules.forms.HTMLForm.FormInput;
import org.archive.util.JSONUtils;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;

public class FormProcessor extends Processor implements Checkpointable {

    @SuppressWarnings("unused")
    private static final long serialVersionUID = -1L;
    private static final Logger logger =
        Logger.getLogger(FormProcessor.class.getName());
    
    // formProvince (String) -> count
    protected LoadingCache<String, AtomicLong> eligibleFormsSeenCount =
            CacheBuilder.newBuilder()
                .<String, AtomicLong>build(
                    new CacheLoader<String, AtomicLong>() {
                        public AtomicLong load(String arg0) {
                            return new AtomicLong(0L);
                        }
                    });

    // formProvince (String) -> count
    protected LoadingCache<String, AtomicLong> eligibleFormsAttemptsCount =
            CacheBuilder.newBuilder()
                    .<String, AtomicLong>build(
                            new CacheLoader<String, AtomicLong>() {
                                public AtomicLong load(String arg0) {
                                    return new AtomicLong(0L);
                                }
                            });
    
    protected ExtractorHTMLForms extractorHTMLForms = null;

    transient protected UriErrorLoggerModule loggerModule;
    public UriErrorLoggerModule getLoggerModule() {
        return this.loggerModule;
    }
    @Autowired
    public void setLoggerModule(UriErrorLoggerModule loggerModule) {
        this.loggerModule = loggerModule;
    }
     
    public FormProcessor() {
        super();
    }
    
    public ExtractorHTMLForms getExtractorHTMLForms() {
        return extractorHTMLForms;
    }
    public void setExtractorHTMLForms(ExtractorHTMLForms extractorHTMLForms) {
        this.extractorHTMLForms = extractorHTMLForms;
    }

    @Override
    protected boolean shouldProcess(CrawlURI curi) {
        ExtractorHTMLForms formExtractor = getExtractorHTMLForms();
        
        if (formExtractor == null || !formExtractor.getExtractAllForms()) 
            return false;
        
        for (Object form: curi.getDataList(ExtractorHTMLForms.A_HTML_FORM_OBJECTS)) {
            String method = ((HTMLForm)form).getMethod();
            
            if (!((HTMLForm)form).seemsLoginForm() && method != null && method.toUpperCase().equals("GET")) {
                return true;
            }
        }
        
        return false;
    }

    /**
     * return the elements in a List that match a Comparator as a sub-list
     * @param list
     * @param comparator
     * @param comparisonTest
     * @return
     */
    protected <T> List<T> subList(List<T> list, Comparator<T> comparator, T comparisonTest) {
        List<T> subList = new ArrayList<T>();
        
        for (T entry : list) {
            if (comparator.compare(entry, comparisonTest) == 0) {
                subList.add(entry);
            }
        }
        
        return subList;
    }
    
    
    @Override
    protected void innerProcess(CrawlURI curi) {
        String formProvince = getFormProvince(curi);
        curi.getDataList(A_WARC_RESPONSE_HEADERS).add(warcHeaderFor(formProvince)); 
        
        if(curi.getDataList(ExtractorHTMLForms.A_HTML_FORM_OBJECTS).size()>0) {
            for (Object entry: curi.getDataList(ExtractorHTMLForms.A_HTML_FORM_OBJECTS)) {
                HTMLForm form = (HTMLForm)entry;
                
                try {
                    String method = ((HTMLForm)form).getMethod();
                    
                    if (!form.seemsLoginForm() && method != null && method.toUpperCase().equals("GET")) {
                        
                        //handle possibility of multiple submit inputs in a single form
                        List<FormInput> allInputs = ((HTMLForm)form).getAllInputs();
                        
                        List<FormInput> subListNotSubmits = subList(allInputs,
                                new Comparator<FormInput>() {
                                    public int compare(FormInput t1,
                                            FormInput t2) {
                                        
                                        if (!t1.type.toUpperCase().equals("SUBMIT")) 
                                            return 0;
                                        
                                        return 1;
                                    }
                                }, null);                        
                        
                        for (FormInput input : allInputs) {
                            if (input.type.toUpperCase().equals("SUBMIT")) {
                                
                                HTMLForm f = new HTMLForm();
                                f.action = form.action;
                                f.method = form.method;
                                
                                for (FormInput nonSubmitFormInput : subListNotSubmits) {
                                    f.addField(nonSubmitFormInput.type, nonSubmitFormInput.name, nonSubmitFormInput.value);
                                }
                                
                                f.addField(input.type, input.name, input.value);
                                
                                eligibleFormsAttemptsCount.get(formProvince).incrementAndGet();
                                createFormSubmissionAttempt(curi,f,formProvince); 
                            }
                        }
                    }
                } catch (ExecutionException e) {
                    throw new RuntimeException(e);  // can't happen?
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
            String method = templateForm.getMethod();
            
            if (method == null || !method.toUpperCase().equals("GET")) return;
            
            String submitUrl = templateForm.getAction() == null ? curi.getURI() : templateForm.getAction();

            submitUrl += "?" + templateForm.asFormDataString();
            
            CrawlURI submitCuri = curi.createCrawlURI(submitUrl, lc, Hop.SUBMIT);
            submitCuri.setFetchType(FetchType.HTTP_GET);
            submitCuri.getData().put(
                    CoreAttributeConstants.A_SUBMIT_DATA, 
                    templateForm.asFormDataString());
            submitCuri.setSchedulingDirective(SchedulingConstants.HIGH);
            submitCuri.setForceFetch(true);
            curi.getOutLinks().add(submitCuri);
            curi.getAnnotations().add("submit:"+templateForm.getAction());
        } catch (URIException ue) {
            loggerModule.logUriError(ue,curi.getUURI(),templateForm.getAction());
        }
    }
    
    protected String warcHeaderFor(String formProvince) {
        return "WARC-Simple-Form-Province-Status: "+submitStatusFor(formProvince);
    }
    
    protected String submitStatusFor(String formProvince) {
        try {
            return eligibleFormsAttemptsCount.get(formProvince).get()
                    +","+eligibleFormsSeenCount.get(formProvince).get()
                    +","+formProvince;
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }
    
    @Override
    protected JSONObject toCheckpointJson() throws JSONException {
        JSONObject json = super.toCheckpointJson();
        json.put("eligibleFormsAttemptsCount", eligibleFormsAttemptsCount.asMap());
        json.put("eligibleFormsSeenCount", eligibleFormsSeenCount.asMap());
        return json;
    }
    
    @Override
    protected void fromCheckpointJson(JSONObject json) throws JSONException {
        super.fromCheckpointJson(json);
        JSONUtils.putAllAtomicLongs(
                eligibleFormsAttemptsCount.asMap(),
                json.getJSONObject("eligibleFormsAttemptsCount"));
        JSONUtils.putAllAtomicLongs(
                eligibleFormsSeenCount.asMap(),
                json.getJSONObject("eligibleFormsSeenCount"));
    }

}
