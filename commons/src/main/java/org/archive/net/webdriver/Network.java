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

package org.archive.net.webdriver;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;

// https://w3c.github.io/webdriver-bidi/#module-network
public interface Network extends BiDiModule {
    AddInterceptResult addIntercept(List<InterceptPhase> phases,
                                    List<BrowsingContext.Context> contexts,
                                    List<UrlPattern> urlPatterns);

    CompletableFuture<Void> continueRequestAsync(Request request, List<Header> headers);

    enum InterceptPhase {beforeRequestSent, responseStarted, authRequired}

    record AddInterceptResult(Intercept intercept) {
    }

    record Intercept(String id) implements BiDiJson.Identifier {
    }

    record BeforeRequestSent(
            BrowsingContext.Context context,
            boolean isBlocked,
            BrowsingContext.Navigation navigation,
            long redirectCount,
            RequestData request,
            long timestamp,
            List<Intercept> intercepts,
            Initiator initiator) implements BiDiEvent {
    }

    record Cookie(
            String name,
            byte[] value,
            String domain,
            String path,
            Long size,
            Boolean httpOnly,
            Boolean secure,
            SameSite sameSite,
            Long expiry) {
    }

    record Header(String name, byte[] value) {
        public Header(String name, String value) {
            this(name, value.getBytes(StandardCharsets.UTF_8));
        }
    }

    record Initiator(Long columnNumber, Long lineNumber, InitiatorType type) {
    }

    enum InitiatorType {parser, script, preflight, other}

    record Request(String id) implements BiDiJson.Identifier {
    }

    record RequestData(
            Request request,
            String url,
            String method,
            List<Header> headers,
            List<Cookie> cookies,
            long headersSize,
            long bodySize,
            String destination,
            String initiatorType
    ) {
    }

    enum SameSite {strict, lax, none}

    interface UrlPattern {
        String type();
    }

    record UrlPatternPattern(String type, String protocol) implements UrlPattern {
    }
}
