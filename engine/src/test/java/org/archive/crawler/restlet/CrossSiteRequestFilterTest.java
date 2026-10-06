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

package org.archive.crawler.restlet;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.restlet.Context;
import org.restlet.Request;
import org.restlet.Response;
import org.restlet.Restlet;
import org.restlet.data.Method;
import org.restlet.data.Status;

import static org.junit.jupiter.api.Assertions.*;

public class CrossSiteRequestFilterTest {
    Status handle(Method method, String site, String origin) {
        CrossSiteRequestFilter filter = new CrossSiteRequestFilter(new Context(),
                List.of("https://dashboard.example.org"));
        filter.setNext(new Restlet() {
            @Override
            public void handle(Request request, Response response) {
                response.setStatus(Status.SUCCESS_OK);
            }
        });
        Request request = new Request(method,
                "https://crawler.example.org/engine/job/j/script");
        if (site != null) {
            request.getHeaders().add("Sec-Fetch-Site", site);
        }
        if (origin != null) {
            request.getHeaders().add("Origin", origin);
        }
        Response response = new Response(request);
        filter.handle(request, response);
        return response.getStatus();
    }

    @Test
    public void testSameOriginAllowed() {
        assertEquals(Status.SUCCESS_OK, handle(Method.POST, "same-origin",
                "https://crawler.example.org"));
        assertEquals(Status.SUCCESS_OK, handle(Method.PUT, "same-origin", null));
        assertEquals(Status.SUCCESS_OK, handle(Method.POST, "none", null));
    }

    @Test
    public void testNoHeaderAllowed() {
        // API clients like curl
        assertEquals(Status.SUCCESS_OK, handle(Method.POST, null, null));
    }

    @Test
    public void testCrossSiteRejected() {
        assertEquals(Status.CLIENT_ERROR_FORBIDDEN, handle(Method.POST,
                "cross-site", "https://evil.example"));
        assertEquals(Status.CLIENT_ERROR_FORBIDDEN, handle(Method.DELETE,
                "cross-site", null));
        assertEquals(Status.CLIENT_ERROR_FORBIDDEN, handle(Method.POST,
                "same-site", "https://other.example.org"));
    }

    @Test
    public void testSafeMethodsAllowed() {
        // e.g. following a link from another site
        assertEquals(Status.SUCCESS_OK, handle(Method.GET, "cross-site",
                "https://evil.example"));
    }

    @Test
    public void testTrustedOrigin() {
        assertEquals(Status.SUCCESS_OK, handle(Method.POST, "cross-site",
                "https://dashboard.example.org"));
        assertEquals(Status.SUCCESS_OK, handle(Method.POST, "same-site",
                "HTTPS://Dashboard.example.org"));
    }
}
