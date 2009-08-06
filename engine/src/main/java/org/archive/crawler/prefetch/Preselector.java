/* Copyright (C) 2003 Internet Archive.
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
 * SimplePreselector.java
 * Created on Sep 22, 2003
 *
 * $Header$
 */
package org.archive.crawler.prefetch;



import static org.archive.modules.fetcher.FetchStatusCodes.*;

import org.archive.crawler.framework.Scoper;
import org.archive.modules.CrawlURI;
import org.archive.modules.ProcessResult;
import org.archive.modules.ProcessorURI;
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
    protected boolean shouldProcess(ProcessorURI puri) {
        return puri instanceof CrawlURI;
    }

    
    @Override
    protected void innerProcess(ProcessorURI puri) {
        throw new AssertionError();
    }
    

    @Override
    protected ProcessResult innerProcessResult(ProcessorURI puri) {
        CrawlURI curi = (CrawlURI)puri;
        
        // Check if uris should be blocked
        if (getBlockAll()) {
            curi.setFetchStatus(S_BLOCKED_BY_USER);
            return ProcessResult.FINISH;
        }

        // Check if allowed by regular expression
        String regexp = getAllowByRegex();
        if (regexp != null && !regexp.equals("")) {
            if (!TextUtils.matches(regexp, curi.toString())) {
                curi.setFetchStatus(S_BLOCKED_BY_USER);
                return ProcessResult.FINISH;
            }
        }

        // Check if blocked by regular expression
        regexp = getBlockByRegex();
        if (regexp != null && !regexp.equals("")) {
            if (TextUtils.matches(regexp, curi.toString())) {
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
