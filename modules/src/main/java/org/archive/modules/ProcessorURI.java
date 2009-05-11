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
 * ProcessorURI.java
 * Created on October 5, 2006
 *
 * $Header$
 */
package org.archive.modules;


import java.util.Collection;
import java.util.Map;
import java.util.Set;

import org.apache.commons.httpclient.HttpMethod;
import org.archive.modules.credential.CredentialAvatar;
import org.archive.modules.extractor.Link;
import org.archive.modules.extractor.LinkContext;
import org.archive.net.UURI;
import org.archive.util.Recorder;


/**
 * A URI to be processed.
 * 
 * @author pjack
 */
public interface ProcessorURI {

    
    public static enum FetchType { HTTP_GET, HTTP_POST, UNKNOWN };
    
    /**
     * Returns the URI being processed.
     * 
     * @return  the URI
     */
    UURI getUURI();

    
    UURI getVia();
    
    boolean isPrerequisite();
    void setPrerequisite(boolean prereq);

    // Used to be a map attribute.
    void setError(String msg);
    
    // Used to be a map attribute.
    long getFetchBeginTime();
    void setFetchBeginTime(long time);
    
    // Used to be a map attribute
    long getFetchCompletedTime();
    void setFetchCompletedTime(long time);
    
    String getDNSServerIPLabel();
    void setDNSServerIPLabel(String label);
    
    FetchType getFetchType();
    void setFetchType(FetchType type);
    
    Recorder getRecorder();

    boolean containsDataKey(String attr);
    Map<String,Object> getData();
    void makeHeritable(String attr);
    Map<String,Object> getPersistentDataMap();
    void addPersistentDataMapKey(String s);
    
    String getUserAgent();
    void setUserAgent(String ua);

    long getContentSize();
    void setContentSize(long size);
    
	String getContentDigestSchemeString();
    byte[] getContentDigest();
    void setContentDigest(String algorithm, byte[] digest);

    String getContentType();
    void setContentType(String mimeType);

    long getContentLength();
    
    Collection<String> getAnnotations();
    Collection<Throwable> getNonFatalFailures();
    
    int getFetchStatus();
    void setFetchStatus(int status);
    
    // Used to be a map attribute. May still want to be one.
    HttpMethod getHttpMethod();
    void setHttpMethod(HttpMethod method);
    
    
    boolean hasCredentialAvatars();
    Set<CredentialAvatar> getCredentialAvatars();

    String getPathFromSeed();
    boolean isSeed();
    void setSeed(boolean seed);
    
    boolean isLocation();

    
    Collection<Link> getOutLinks();
    
    UURI getBaseURI();
    void setBaseURI(UURI base);

    boolean hasBeenLinkExtracted();
    void linkExtractorFinished();

    LinkContext getViaContext();

    int getFetchAttempts();

    boolean isSuccess();

    void incrementDiscardedOutLinks();

    String getSourceTag();
    
    boolean forceFetch();
 
}
