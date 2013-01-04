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

import org.apache.http.HttpVersion;
import org.apache.http.ProtocolVersion;
import org.apache.http.RequestLine;
import org.apache.http.client.methods.AbstractExecutionAwareRequest;
import org.apache.http.message.BasicRequestLine;
import org.apache.http.util.Args;

public class BasicExecutionAwareRequest extends AbstractExecutionAwareRequest {

    private final String method;
    private final String uri;

    private RequestLine requestline;

    /**
     * Creates an instance of this class using the given request method
     * and URI.
     *
     * @param method request method.
     * @param uri request URI.
     */
    public BasicExecutionAwareRequest(final String method, final String uri) {
        super();
        this.method = Args.notNull(method, "Method name");
        this.uri = Args.notNull(uri, "Request URI");
        this.requestline = null;
    }

    /**
     * Creates an instance of this class using the given request method, URI
     * and the HTTP protocol version.
     *
     * @param method request method.
     * @param uri request URI.
     * @param ver HTTP protocol version.
     */
    public BasicExecutionAwareRequest(final String method, final String uri, final ProtocolVersion ver) {
        this(new BasicRequestLine(method, uri, ver));
    }

    /**
     * Creates an instance of this class using the given request line.
     *
     * @param requestline request line.
     */
    public BasicExecutionAwareRequest(final RequestLine requestline) {
        super();
        this.requestline = Args.notNull(requestline, "Request line");
        this.method = requestline.getMethod();
        this.uri = requestline.getUri();
    }

    /**
     * Returns the HTTP protocol version to be used for this request.
     *
     * @see #BasicHttpRequest(String, String)
     */
    public ProtocolVersion getProtocolVersion() {
        return getRequestLine().getProtocolVersion();
    }

    /**
     * Returns the request line of this request.
     *
     * @see #BasicHttpRequest(String, String)
     */
    public RequestLine getRequestLine() {
        if (this.requestline == null) {
            this.requestline = new BasicRequestLine(this.method, this.uri, HttpVersion.HTTP_1_1);
        }
        return this.requestline;
    }

    @Override
    public String toString() {
        return this.method + " " + this.uri + " " + this.headergroup;
    }
}
