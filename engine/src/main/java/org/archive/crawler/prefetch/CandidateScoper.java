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

import java.util.logging.Level;
import java.util.logging.Logger;

import org.archive.crawler.framework.Scoper;
import org.archive.modules.CrawlURI;
import org.archive.modules.ProcessResult;
import org.archive.modules.fetcher.FetchStatusCodes;

/**
 * Simple single-URI scoper, considers passed-in URI as candidate; sets 
 * fetchstatus negative and skips to end of processing if out-of-scope. 
 * 
 * @author gojomo
 */
public class CandidateScoper extends Scoper {
    @SuppressWarnings("unused")
    private static final long serialVersionUID = 1L;

    private static final Logger logger = Logger.getLogger(CandidateScoper.class.getName());

    @Override
    protected ProcessResult innerProcessResult(CrawlURI curi) throws InterruptedException {
        try {
            if (!isInScope(curi)) {
                // Scope rejected
                curi.setFetchStatus(FetchStatusCodes.S_OUT_OF_SCOPE);
                return ProcessResult.FINISH;
            }
        } catch (Exception e) {
            curi.setFetchStatus(FetchStatusCodes.S_RUNTIME_EXCEPTION);
            logger.log(Level.SEVERE, "problem scoping " + curi, e);
            return ProcessResult.FINISH;
        }
        return ProcessResult.PROCEED;
    }

    @Override
    protected boolean shouldProcess(CrawlURI uri) {
        return true;
    }

    @Override
    protected void innerProcess(CrawlURI uri) throws InterruptedException {
        assert false;
    }

}
