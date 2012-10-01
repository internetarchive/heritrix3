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


import java.io.IOException;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.commons.httpclient.HttpStatus;
import org.archive.checkpointing.Checkpoint;
import org.archive.checkpointing.Checkpointable;
import org.archive.modules.credential.Credential;
import org.archive.modules.credential.HttpAuthenticationCredential;
import org.archive.modules.deciderules.AcceptDecideRule;
import org.archive.modules.deciderules.DecideResult;
import org.archive.modules.deciderules.DecideRule;
import org.archive.net.UURI;
import org.archive.spring.HasKeyedProperties;
import org.archive.spring.KeyedProperties;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.BeanNameAware;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.Lifecycle;


/**
 * A processor of URIs.  The URI provides the context for the process; 
 * settings can be altered based on the URI.
 * 
 * @author pjack
 */
public abstract class Processor 
implements HasKeyedProperties, 
           Lifecycle, 
           BeanNameAware,
           Checkpointable {
    protected KeyedProperties kp = new KeyedProperties();
    public KeyedProperties getKeyedProperties() {
        return kp;
    }

    protected String beanName; 
    public String getBeanName() {
        return this.beanName;
    }
    public void setBeanName(String name) {
        this.beanName = name;
    }
    
    /** 
     * Whether or not this process will execute for a particular URI. 
     * If this is false for a URI, then the URI isn't processed,
     * regardless of what the DecideRules say.
     */
    {
        setEnabled(true);
    }
    public boolean getEnabled() {
        return (Boolean) kp.get("enabled");
    }
    public void setEnabled(boolean enabled) {
        kp.put("enabled",enabled);
    }
    
    
    /** 
     * Decide rule(s) (also particular to a URI) that determine whether 
     * or not a particular URI is processed here. If the rule(s) answer
     * REJECT, processing is skipped. (ACCEPT or PASS allow processing
     * to continue). 
     */
    {
        setShouldProcessRule(new AcceptDecideRule());
    }
    public DecideRule getShouldProcessRule() {
        return (DecideRule) kp.get("shouldProcessRule");
    }
    public void setShouldProcessRule(DecideRule rule) {
        kp.put("shouldProcessRule", rule);
    }

    /**
     * The number of URIs processed by this processor.
     */
    protected AtomicLong uriCount = new AtomicLong(0);

    
    /**
     * Processes the given URI.  First checks {@link #ENABLED} and
     * {@link #DECIDE_RULES}.  If ENABLED is false, then nothing happens.
     * If the DECIDE_RULES indicate REJECT, then the 
     * {@link #innerRejectProcess(ProcessorURI)} method is invoked, and
     * the process method returns.
     * 
     * <p>Next, the {@link #shouldProcess(ProcessorURI)} method is 
     * consulted to see if this Processor knows how to handle the given
     * URI.  If it returns false, then nothing futher occurs.
     * 
     * <p>FIXME: Should innerRejectProcess be called when ENABLED is false,
     * or when shouldProcess returns false?  The previous Processor 
     * implementation didn't handle it that way.
     * 
     * <p>Otherwise, the URI is considered valid.  This processor's count
     * of handled URIs is incremented, and the 
     * {@link #innerProcess(ProcessorURI)} method is invoked to actually
     * perform the process.
     * 
     * @param uri  The URI to process
     * @throws  InterruptedException   if the thread is interrupted
     */
    public ProcessResult process(CrawlURI uri) 
    throws InterruptedException {
        if (!getEnabled()) {
            return ProcessResult.PROCEED;
        }
        
        if (getShouldProcessRule().decisionFor(uri) == DecideResult.REJECT) {
            innerRejectProcess(uri);
            return ProcessResult.PROCEED;
        }
        
        if (shouldProcess(uri)) {
            uriCount.incrementAndGet();
            return innerProcessResult(uri);
        } else {
            return ProcessResult.PROCEED;
        }
    }

    /**
     * Returns the number of URIs this processor has handled.  The returned
     * number does not include URIs that were rejected by the 
     * {@link #ENABLED} flag, by the {@link #DECIDE_RULES}, or by the 
     * {@link #shouldProcess(ProcessorURI)} method.
     * 
     * @return  the number of URIs this processor has handled
     */
    public long getURICount() {
        return uriCount.get();
    }


    /**
     * Determines whether the given uri should be processed by this 
     * processor.  For instance, a processor that only works on HTML 
     * content might reject the URI if its content type is not 
     * "text/html", if its content length is zero, and so on.
     * 
     * @param uri   the URI to test
     * @return  true if this processor should process that uri; false if not
     */
    protected abstract boolean shouldProcess(CrawlURI uri);

    
    protected ProcessResult innerProcessResult(CrawlURI uri) 
    throws InterruptedException {
        innerProcess(uri);
        return ProcessResult.PROCEED;
    }

    /**
     * Actually performs the process.  By the time this method is invoked,
     * it is known that the given URI passes the {@link #ENABLED}, the 
     * {@link #DECIDE_RULES} and the {@link #shouldProcess(ProcessorURI)}
     * tests.  
     * 
     * @param uri    the URI to process
     * @throws InterruptedException   if the thread is interrupted
     */
    protected abstract void innerProcess(CrawlURI uri) 
    throws InterruptedException;


    /**
     * Invoked after a URI has been rejected.  The default implementation
     * does nothing; subclasses may override to log rejects or something.
     * 
     * @param uri   the URI that was rejected
     * @throws InterruptedException   if the thread is interrupted
     */
    protected void innerRejectProcess(CrawlURI uri) 
    throws InterruptedException {        
    }


    public static String flattenVia(CrawlURI puri) {
        UURI uuri = puri.getVia();
        return (uuri == null) ? "" : uuri.toString();
    }

    
    public static boolean isSuccess(CrawlURI puri) {
        boolean result = false;
        int statusCode = puri.getFetchStatus();
        if (statusCode == HttpStatus.SC_UNAUTHORIZED &&
            hasHttpAuthenticationCredential(puri)) {
            result = false;
        } else {
            result = (statusCode > 0);
        }
        return result;        
    }
    
    
    public static long getRecordedSize(CrawlURI puri) {
        if (puri.getRecorder() == null) {
            return puri.getContentSize();
        } else {
            return puri.getRecorder().getRecordedInput().getSize();
        }
    }
    

    /**
     * @return True if we have an HttpAuthentication (rfc2617) payload.
     */
    public static boolean hasHttpAuthenticationCredential(CrawlURI puri) {
        Set<Credential> credentials = puri.getCredentials();
        for (Credential ca: credentials) {
            if (ca instanceof HttpAuthenticationCredential) {
                return true;
            }
        }
        return false;
    }

    // FIXME: Raise to interface
    // FIXME: Internationalize somehow
    // FIXME: Pass in PrintWriter instead creating large in-memory strings
    public String report() {
        return "Processor: "+getClass().getName()+"\n";
    }
    
    protected boolean isRunning = false; 
    public boolean isRunning() {
        return isRunning;
    }

    public void start() {
        if(isRunning) {
            return; 
        }
        isRunning = true; 
        if(recoveryCheckpoint!=null) {
            try {
                JSONObject json = recoveryCheckpoint.loadJson(getBeanName());
                fromCheckpointJson(json); 
            } catch (JSONException e) {
                throw new RuntimeException(e);
            }
        }
    }


    public void stop() {
        isRunning = false; 
    }
    
    public void startCheckpoint(Checkpoint checkpointInProgress) {}
    
    public void doCheckpoint(Checkpoint checkpointInProgress) 
    throws IOException {
        try {
            JSONObject json = toCheckpointJson();
            checkpointInProgress.saveJson(beanName, json); 
        } catch(JSONException j) {
            // impossible
        } 
    }
  
    /**
     * Return a JSONObject of current stat that can be consulted 
     * on recovery to restore necessary values. 
     * 
     * @return JSONObject
     * @throws JSONException
     */
    protected JSONObject toCheckpointJson() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("uriCount", getURICount());
        return json;
    }
    
    /**
     * Restore internal state from JSONObject stored at earlier
     * checkpoint-time.
     * 
     * @param json JSONObject
     * @throws JSONException
     */
    protected void fromCheckpointJson(JSONObject json) throws JSONException {
        uriCount.set(json.getLong("uriCount"));
    }
    
    public void finishCheckpoint(Checkpoint checkpointInProgress) {}
    
    protected Checkpoint recoveryCheckpoint;
    @Autowired(required=false)
    public void setRecoveryCheckpoint(Checkpoint checkpoint) {
        this.recoveryCheckpoint = checkpoint; 
    }
}
