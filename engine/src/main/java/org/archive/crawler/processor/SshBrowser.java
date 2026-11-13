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

import org.archive.net.webdriver.SshBrowserProcess;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * A browser of a {@link BrowserProcessor} launched on another machine over ssh, as described under
 * {@link BrowserProcessor#setBrowsers browsers}. The {@code browsers} entry
 * {@code ssh://[user@]host[:port]} is shorthand for this bean with only {@code uri} set.
 */
public class SshBrowser extends BrowserConfig {
    private URI uri;

    public String getUri() {
        return uri == null ? null : uri.toString();
    }

    /**
     * The machine to launch the browser on, as {@code ssh://[user@]host[:port]}.
     */
    public void setUri(String uri) {
        URI parsed;
        try {
            parsed = new URI(uri);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException(
                    "Expected ssh://[user@]host[:port] but got '" + uri + "'", e);
        }
        SshBrowserProcess.checkUri(parsed);
        this.uri = parsed;
    }

    URI uri() {
        return uri;
    }
}
