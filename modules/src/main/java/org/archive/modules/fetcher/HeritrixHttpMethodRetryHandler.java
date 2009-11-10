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
package org.archive.modules.fetcher;

import java.io.IOException;
import java.net.SocketTimeoutException;

import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.HttpMethodRetryHandler;
import org.apache.commons.httpclient.NoHttpResponseException;
import org.apache.commons.httpclient.methods.PostMethod;

/**
 * Retry handler that tries ten times to establish connection and then once
 * established, if a GET method, tries ten times to get response (If POST,
 * it tries once only).
 * 
 * Its unsafe retrying POSTs.  See 'Rule of Thumb' under 'Method Recovery'
 * here: <a href="http://jakarta.apache.org/commons/httpclient/tutorial.html">
 * HttpClient Tutorial</a>.
 * 
 * @author stack
 * @version $Date$, $Revision$
 */
public class HeritrixHttpMethodRetryHandler implements HttpMethodRetryHandler {
    private static final int DEFAULT_RETRY_COUNT = 10;
    
    private final int maxRetryCount;
    
    /**
     * Constructor.
     */
    public HeritrixHttpMethodRetryHandler() {
        this(DEFAULT_RETRY_COUNT);
    }
    
    /**
     * Constructor.
     * @param maxRetryCount Maximum amount of times to retry.
     */
    public HeritrixHttpMethodRetryHandler(int maxRetryCount) {
    	this.maxRetryCount = maxRetryCount;
    }
    
    public boolean retryMethod(HttpMethod method, IOException exception,
			int executionCount) {
        if(exception instanceof SocketTimeoutException) {
            // already waited for the configured amount of time with no reply; 
            // do not retry further until next go round
            return false; 
        }
		if (executionCount >= this.maxRetryCount) {
			// Do not retry if over max retry count
			return false;
		}
		if (exception instanceof NoHttpResponseException) {
			// Retry if the server dropped connection on us
			return true;
		}
		if (!method.isRequestSent() && (!(method instanceof PostMethod))) {
			// Retry if the request has not been sent fully or
			// if it's OK to retry methods that have been sent
			return true;
		}
		// otherwise do not retry
		return false;
	}
}
