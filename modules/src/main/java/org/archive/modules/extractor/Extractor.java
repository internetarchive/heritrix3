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


import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.httpclient.URIException;
import org.archive.modules.CrawlURI;
import org.archive.modules.Processor;
import org.archive.net.UURI;
import org.archive.net.UURIFactory;
import org.springframework.beans.factory.annotation.Autowired;


/**
 * Extracts links from fetched URIs.  This class provides error handling
 * for some common issues that occur when parsing document content.  You
 * almost certainly want to subclass {@link ContentExtractor} instead of
 * this class.
 * 
 * @author pjack
 */
public abstract class Extractor extends Processor {


    /** Logger. */
    private static final Logger logger = 
        Logger.getLogger(Extractor.class.getName());

    public static final ExtractorParameters DEFAULT_PARAMETERS = 
        new ExtractorParameters() {
            public int getMaxOutlinks() {
                return 6000;
            }
        };

    transient protected UriErrorLoggerModule loggerModule;
    public UriErrorLoggerModule getLoggerModule() {
        return this.loggerModule;
    }
    @Autowired
    public void setLoggerModule(UriErrorLoggerModule loggerModule) {
        this.loggerModule = loggerModule;
    }
    
    {
    	setExtractorParameters(DEFAULT_PARAMETERS);
    }
    transient ExtractorParameters extractorParameters;
    public ExtractorParameters getExtractorParameters() {
        return extractorParameters;
    }
    @Autowired(required=false)
    public void setExtractorParameters(ExtractorParameters helper) {
        this.extractorParameters = helper; 
    }
    
    /**
     * Processes the given URI.  This method just delegates to 
     * {@link #extract(ExtractorURI)}, catching runtime exceptions and
     * errors that are usually non-fatal, to highlight them in the 
     * relevant log(s). 
     * 
     * <p>Notably, StackOverflowError is caught here, as that seems to 
     * happen a lot when dealing with document parsing APIs.
     * 
     * @param uri  the URI to extract links from
     */
    final protected void innerProcess(CrawlURI uri)
    throws InterruptedException {
        try {
            extract(uri);
        } catch (NullPointerException npe) {
            handleException(uri, npe);
        } catch (StackOverflowError soe) {
            handleException(uri, soe);
        } catch (java.nio.charset.CoderMalfunctionError cme) {
            // See http://sourceforge.net/tracker/index.php?func=detail&aid=1540222&group_id=73833&atid=539099
            handleException(uri, cme);
        }
    }
    
    
    private void handleException(CrawlURI uri, Throwable t) {
        // both annotate (to highlight in crawl log) & add as local-error
        uri.getAnnotations().add("err=" + t.getClass().getName());
        uri.getNonFatalFailures().add(t);
        // also log as INFO
        // TODO: remove as redundant, given nonfatal logging?
        logger.log(Level.INFO, "Exception", t);        
    }


    /**
     * Extracts links from the given URI.  Subclasses should use 
     * {@link ExtractorURI#getInputStream()} or 
     * {@link ExtractorURI#getCharSequence()} to process the content of the
     * URI.  Any links that are discovered should be added to the
     * {@link ExtractorURI#getOutLinks()} set.
     * 
     * @param uri  the uri to extract links from
     */
    protected abstract void extract(CrawlURI uri);

    
    public void logUriError(URIException e, UURI uuri, 
            CharSequence l) {
        if (e.getReasonCode() == UURIFactory.IGNORED_SCHEME) {
            // don't log those that are intentionally ignored
            return; 
        }
        loggerModule.logUriError(e, uuri, l);
    }

}
