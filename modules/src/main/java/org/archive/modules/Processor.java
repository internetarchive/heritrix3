/* Copyright (C) 2006 Internet Archive.
 *
 * This file is part of the Heritrix web crawler (crawler.archive.org).
 *
 * Heritrix is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser Public License as published by
 * the Free Software Foundation; either version 2.1 of the License, or
 * any later version.
 *
 * Heritrix is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser Public License for more details.
 *
 * You should have received a copy of the GNU Lesser Public License
 * along with Heritrix; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 *
 * Processor.java
 * Created on October 5, 2006
 *
 * $Header$
 */
package org.archive.modules;


import java.io.Serializable;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.commons.httpclient.HttpStatus;
import org.archive.modules.credential.CredentialAvatar;
import org.archive.modules.credential.HttpAuthenticationCredential;
import org.archive.modules.deciderules.AcceptDecideRule;
import org.archive.modules.deciderules.DecideResult;
import org.archive.modules.deciderules.DecideRule;
import org.archive.net.UURI;
import org.archive.spring.HasKeyedProperties;
import org.archive.spring.KeyedProperties;
import org.springframework.context.Lifecycle;


/**
 * A processor of URIs.  The URI provides the context for the process; 
 * settings can be altered based on the URI.
 * 
 * @author pjack
 */
public abstract class Processor 
implements Serializable, HasKeyedProperties, Lifecycle {
    protected KeyedProperties kp = new KeyedProperties();
    public KeyedProperties getKeyedProperties() {
        return kp;
    }

    String name = this.getClass().getSimpleName(); 
    public String getName() {
        return this.name;
    }
    public void setName(String name) {
        this.name = name;
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
    private AtomicLong uriCount = new AtomicLong(0);

    
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
            hasHttpAuthenticationCredentialAvatar(puri)) {
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
    public static boolean hasHttpAuthenticationCredentialAvatar(CrawlURI puri) {
        Set<CredentialAvatar> avatars = puri.getCredentialAvatars();
        for (CredentialAvatar ca: avatars) {
            if (ca.match(HttpAuthenticationCredential.class)) {
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
    
    boolean isRunning = false; 
    public boolean isRunning() {
        return isRunning;
    }

    public void start() {
        isRunning = true; 
    }

    public void stop() {
        isRunning = false; 
    }
}
