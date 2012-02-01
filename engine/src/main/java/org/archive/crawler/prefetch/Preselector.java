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

package org.archive.crawler.prefetch;



import static org.archive.modules.fetcher.FetchStatusCodes.*;

import org.archive.crawler.framework.Scoper;
import org.archive.modules.CrawlURI;
import org.archive.modules.ProcessResult;
import org.archive.util.TextUtils;


/**
 * If set to recheck the crawl's scope, gives a yes/no on whether
 * a CrawlURI should be processed at all. If not, its status
 * will be marked OUT_OF_SCOPE and the URI will skip directly
 * to the first "postprocessor".
 *
 *
 * @author gojomo
 *
 */
public class Preselector extends Scoper {
    @SuppressWarnings("unused")
    private static final long serialVersionUID = 3L;

    /**
     * Recheck if uri is in scope. This is meaningful if the scope is altered
     * during a crawl. URIs are checked against the scope when they are added to
     * queues. Setting this value to true forces the URI to be checked against
     * the scope when it is comming out of the queue, possibly after the scope
     * is altered.
     */
    {
        setRecheckScope(false);
    }
    public boolean getRecheckScope() {
        return (Boolean) kp.get("recheckScope");
    }
    public void setRecheckScope(boolean recheck) {
        kp.put("recheckScope",recheck);
    }

    /**
     * Block all URIs from being processed. This is most likely to be used in
     * overrides to easily reject certain hosts from being processed.
     */
    {
        setBlockAll(false);
    }
    public boolean getBlockAll() {
        return (Boolean) kp.get("blockAll");
    }
    public void setBlockAll(boolean recheck) {
        kp.put("blockAll",recheck);
    }

    /**
     * Block all URIs matching the regular expression from being processed.
     */
    {
        setBlockByRegex("");
    }
    public String getBlockByRegex() {
        return (String) kp.get("blockByRegex");
    }
    public void setBlockByRegex(String regex) {
        kp.put("blockByRegex",regex);
    }

    /**
     * Allow only URIs matching the regular expression to be processed.
     */
    {
        setAllowByRegex("");
    }
    public String getAllowByRegex() {
        return (String) kp.get("allowByRegex");
    }
    public void setAllowByRegex(String regex) {
        kp.put("allowByRegex",regex);
    }
    
    /**
     * Constructor.
     */
    public Preselector() {
        super();
    }

    @Override
    protected boolean shouldProcess(CrawlURI puri) {
        return puri instanceof CrawlURI;
    }

    
    @Override
    protected void innerProcess(CrawlURI puri) {
        throw new AssertionError();
    }
    

    @Override
    protected ProcessResult innerProcessResult(CrawlURI puri) {
        CrawlURI curi = (CrawlURI)puri;
        
        // Check if uris should be blocked
        if (getBlockAll()) {
            curi.setFetchStatus(S_BLOCKED_BY_USER);
            return ProcessResult.FINISH;
        }

        // Check if allowed by regular expression
        String regex = getAllowByRegex();
        if (regex != null && !regex.equals("")) {
            if (!TextUtils.matches(regex, curi.toString())) {
                curi.setFetchStatus(S_BLOCKED_BY_USER);
                return ProcessResult.FINISH;
            }
        }

        // Check if blocked by regular expression
        regex = getBlockByRegex();
        if (regex != null && !regex.equals("")) {
            if (TextUtils.matches(regex, curi.toString())) {
                curi.setFetchStatus(S_BLOCKED_BY_USER);
                return ProcessResult.FINISH;
            }
        }

        // Possibly recheck scope
        if (getRecheckScope()) {
            if (!isInScope(curi)) {
                // Scope rejected
                curi.setFetchStatus(S_OUT_OF_SCOPE);
                return ProcessResult.FINISH;
            }
        }
        
        return ProcessResult.PROCEED;
    }
}
