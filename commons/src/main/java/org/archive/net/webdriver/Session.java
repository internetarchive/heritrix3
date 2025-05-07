package org.archive.net.webdriver;

import org.json.JSONObject;

import java.util.List;

// https://w3c.github.io/webdriver-bidi/#module-session
public interface Session extends BiDiModule {
    NewResult new_(CapabilitiesRequest capabilities);

    void subscribe(List<String> events, List<BrowsingContext.Context> contexts);

    record NewResult(String sessionId, JSONObject capabilities) {
    }

    record CapabilitiesRequest(CapabilityRequest alwaysMatch) {
    }

    record CapabilityRequest(
            boolean acceptInsecureCerts,
            ProxyConfiguration proxy
    ) {
    }

    record ProxyConfiguration(
            String proxyType,
            String httpProxy,
            String sslProxy
    ) {
    }
}
