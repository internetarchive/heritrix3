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

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.ProtocolVersion;
import org.apache.http.RequestLine;
import org.apache.http.protocol.HTTP;

public class BasicExecutionAwareEntityEnclosingRequest extends
        BasicExecutionAwareRequest implements HttpEntityEnclosingRequest {

    private HttpEntity entity;

    public BasicExecutionAwareEntityEnclosingRequest(final String method,
            final String uri) {
        super(method, uri);
    }

    public BasicExecutionAwareEntityEnclosingRequest(final String method,
            final String uri, final ProtocolVersion ver) {
        super(method, uri, ver);
    }

    public BasicExecutionAwareEntityEnclosingRequest(RequestLine requestline) {
        super(requestline);
    }

    @Override
    public boolean expectContinue() {
        Header expect = getFirstHeader(HTTP.EXPECT_DIRECTIVE);
        return expect != null
                && HTTP.EXPECT_CONTINUE.equalsIgnoreCase(expect.getValue());
    }

    @Override
    public void setEntity(HttpEntity entity) {
        this.entity = entity;

    }

    @Override
    public HttpEntity getEntity() {
        return this.entity;
    }
}
