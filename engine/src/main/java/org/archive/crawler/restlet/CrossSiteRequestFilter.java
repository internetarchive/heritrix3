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

import java.util.Collection;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Logger;

import org.restlet.Context;
import org.restlet.Request;
import org.restlet.Response;
import org.restlet.data.MediaType;
import org.restlet.data.Status;
import org.restlet.routing.Filter;

/**
 * Rejects state-changing requests that a browser sent on behalf of another
 * site, to protect against cross-site request forgery.
 * <p>
 * Uses the Sec-Fetch-Site header, which the browser sets from its own view
 * of the page and the target URL, so it isn't affected by reverse proxies
 * rewriting the host or scheme. Requests without the header, from API clients
 * like curl or from very old browsers, are allowed.
 */
public class CrossSiteRequestFilter extends Filter {
    private static final Logger logger =
            Logger.getLogger(CrossSiteRequestFilter.class.getName());

    /** origins allowed to send cross-site requests, e.g. https://example.org */
    protected final Set<String> trustedOrigins =
            new TreeSet<>(String.CASE_INSENSITIVE_ORDER);

    public CrossSiteRequestFilter(Context context,
            Collection<String> trustedOrigins) {
        super(context);
        this.trustedOrigins.addAll(trustedOrigins);
    }

    @Override
    protected int beforeHandle(Request request, Response response) {
        if (request.getMethod().isSafe()) {
            return CONTINUE;
        }
        String site = request.getHeaders().getFirstValue("Sec-Fetch-Site", true);
        // "none" is a request the user started, e.g. from a bookmark
        if (site == null || site.equals("same-origin") || site.equals("none")) {
            return CONTINUE;
        }
        String origin = request.getHeaders().getFirstValue("Origin", true);
        if (origin != null && trustedOrigins.contains(origin)) {
            return CONTINUE;
        }
        logger.warning("rejected " + site + " " + request.getMethod() + " "
                + request.getResourceRef() + " from origin " + origin);
        response.setStatus(Status.CLIENT_ERROR_FORBIDDEN);
        response.setEntity("Cross-site request rejected. To allow requests from "
                + "another site, add its origin to the heritrix.trustedOrigins "
                + "system property.\n", MediaType.TEXT_PLAIN);
        return STOP;
    }
}
