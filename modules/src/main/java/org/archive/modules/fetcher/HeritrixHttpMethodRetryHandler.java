/* HttpRecorderGetMethod
*
 * Created on Sep 29, 2004
*
* Copyright (C) 2003 Internet Archive.
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
