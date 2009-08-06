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
    
    public String getURI();
    
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
