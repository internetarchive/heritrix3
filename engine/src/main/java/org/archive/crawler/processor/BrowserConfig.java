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

import java.util.List;

/**
 * Settings for one of the {@link BrowserProcessor#setBrowsers browsers} of a BrowserProcessor.
 * Settings left unset take the value of the BrowserProcessor's property of the same name.
 */
public abstract class BrowserConfig {
    private Integer concurrency;
    private String executable;
    private List<String> options;

    /**
     * Parses a {@link BrowserProcessor#setBrowsers browsers} entry given as a string into a config
     * with nothing overridden.
     */
    static BrowserConfig parse(String browser) {
        if (browser.equals("local")) return new LocalBrowser();
        try {
            var ssh = new SshBrowser();
            ssh.setUri(browser);
            return ssh;
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unsupported browser '" + browser +
                    "': expected 'local' or 'ssh://[user@]host[:port]'");
        }
    }

    public Integer getConcurrency() {
        return concurrency;
    }

    /**
     * Maximum number of web pages that can be open in this browser at once.
     */
    public void setConcurrency(Integer concurrency) {
        this.concurrency = concurrency;
    }

    public String getExecutable() {
        return executable;
    }

    /**
     * Webdriver executable to launch for this browser.
     */
    public void setExecutable(String executable) {
        this.executable = executable;
    }

    public List<String> getOptions() {
        return options;
    }

    /**
     * Extra command-line options to be passed to this browser's webdriver executable.
     */
    public void setOptions(List<String> options) {
        this.options = options;
    }
}
