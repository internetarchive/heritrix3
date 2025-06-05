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

import org.json.JSONObject;
import org.json.JSONPropertyName;

import java.util.List;
import java.util.Map;

// https://w3c.github.io/webdriver-bidi/#module-session
public interface Session extends BiDiModule {
    NewResult new_(CapabilitiesRequest capabilities);

    void subscribe(List<String> events, List<BrowsingContext.Context> contexts);

    record NewResult(String sessionId, JSONObject capabilities) {
    }

    record CapabilitiesRequest(Map<String,Object> alwaysMatch,
                               List<Map<String,Object>> firstMatch) {
    }

    record CapabilityRequest(
            boolean acceptInsecureCerts,
            ProxyConfiguration proxy,
            @BiDiJson.PropertyName("moz:firefoxOptions") FirefoxOptions firefoxOptions
    ) {
    }

    record ProxyConfiguration(
            String proxyType,
            String httpProxy,
            String sslProxy
    ) {
    }

    record FirefoxOptions(Map<String,Object> prefs) {
    }
}
