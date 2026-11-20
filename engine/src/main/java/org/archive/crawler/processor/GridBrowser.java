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

package org.archive.crawler.processor;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

/**
 * A browser session of a {@link BrowserProcessor} on a WebDriver server that supports WebDriver BiDi, such as a
 * <a href="https://www.selenium.dev/documentation/grid/">Selenium Grid</a> (version 4 or later). The {@code browsers}
 * entry {@code http://host:port} is shorthand for this bean with only {@code uri} set.
 * <p>
 * {@code executable} doesn't apply, as the Grid chooses the browser. {@code options} are passed to the browser as
 * command-line arguments. Each bean is one session. Grid limits sessions rather than tabs, so use one
 * per Grid node and raise {@code concurrency} for more pages at once. A session that stops answering is deleted and
 * replaced by a new one.
 * <p>
 * The browser connects to the recording proxy directly, so unless the Grid shares this machine's loopback, set the
 * processor's {@link BrowserProcessor#setProxyBindAddress proxyBindAddress},
 * {@link BrowserProcessor#setProxyHost proxyHost} and
 * {@link BrowserProcessor#setProxyAllowedAddresses proxyAllowedAddresses}. Anyone who can reach the Grid can drive
 * its browsers, so consider enabling its authentication and setting {@code username} and {@code password}.
 */
public class GridBrowser extends BrowserConfig {
    private URI uri;
    private String username;
    private String password;
    private String browserName;

    public String getUri() {
        return uri == null ? null : uri.toString();
    }

    /**
     * The WebDriver endpoint, as {@code http://host:port}, with a path if the endpoint has one. A
     * {@code user:password@} before the host sets {@code username} and {@code password}.
     */
    public void setUri(String uri) {
        URI parsed;
        try {
            parsed = new URI(uri);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Expected http://host:port but got '" + redact(uri) + "': "
                    + e.getReason());
        }
        if (!("http".equals(parsed.getScheme()) || "https".equals(parsed.getScheme())) || parsed.getHost() == null
                || parsed.getRawQuery() != null || parsed.getRawFragment() != null) {
            throw new IllegalArgumentException("Expected http://host:port but got '" +
                    redact(uri) + "'");
        }
        String userInfo = parsed.getUserInfo();
        if (userInfo != null) {
            int colon = userInfo.indexOf(':');
            username = colon == -1 ? userInfo : userInfo.substring(0, colon);
            password = colon == -1 ? null : userInfo.substring(colon + 1);
            String authority = parsed.getRawAuthority();
            parsed = URI.create(parsed.getScheme() + "://" + authority.substring(authority.lastIndexOf('@') + 1)
                    + parsed.getRawPath());
        }
        this.uri = parsed;
    }

    /**
     * Hides any user and password in a URI that is to go in an error message.
     */
    static String redact(String uri) {
        return uri.replaceFirst("//[^/@]*@", "//***@");
    }

    URI uri() {
        return uri;
    }

    public String getUsername() {
        return username;
    }

    /**
     * Username for HTTP Basic authentication to the endpoint, such as Selenium Grid's
     * {@code --username} router option.
     */
    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    /**
     * Password for HTTP Basic authentication to the endpoint.
     */
    public void setPassword(String password) {
        this.password = password;
    }

    public String getBrowserName() {
        return browserName;
    }

    /**
     * {@code chrome}, {@code firefox} or {@code MicrosoftEdge} to ask the Grid for that browser only. By default the
     * Grid chooses between Chrome and Firefox.
     */
    public void setBrowserName(String browserName) {
        if (browserName != null && !List.of("chrome", "firefox", "MicrosoftEdge").contains(browserName)) {
            throw new IllegalArgumentException("Expected browserName chrome, firefox or MicrosoftEdge but got '"
                    + browserName + "'");
        }
        this.browserName = browserName;
    }
}
