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

package org.archive.modules.behaviors;

import org.archive.modules.extractor.UriErrorLoggerModule;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Repeatedly clicks a "next page" button to step through paginated content,
 * extracting links after each click. Use as a replacement for ExtractLinksBehavior.
 */
public class PaginationBehavior implements Behavior {
    private final ExtractLinksBehavior extractLinks;
    private final AtomicLong pagesClicked = new AtomicLong();
    private String selector = "button[title=\"Next\"]";
    private int maxPages = 100;
    private long loadTimeout = 5000;

    public PaginationBehavior(UriErrorLoggerModule loggerModule) {
        this.extractLinks = new ExtractLinksBehavior(loggerModule);
    }

    /**
     * CSS selector for the next-page button.
     */
    public void setSelector(String selector) {
        this.selector = selector;
    }

    /**
     * Maximum number of times to click the next-page button.
     */
    public void setMaxPages(int maxPages) {
        this.maxPages = maxPages;
    }

    /**
     * Maximum time to wait for the page to change after each click, in milliseconds.
     */
    public void setLoadTimeout(long loadTimeout) {
        this.loadTimeout = loadTimeout;
    }

    @Override
    public void run(Page page) {
        extractLinks.run(page); // links on the initial page

        for (int i = 0; i < maxPages; i++) {
            // click the next-page button, then wait for the DOM to change (or give up after loadTimeout)
            Boolean clicked = page.evalPromise(/* language=JavaScript */ """
                    (selector, timeout) => new Promise(resolve => {
                        const button = document.querySelector(selector);
                        if (!button || button.disabled) {
                            resolve(false);
                            return;
                        }
                        const observer = new MutationObserver(() => {
                            observer.disconnect();
                            setTimeout(() => resolve(true), 100);
                        });
                        observer.observe(document.body, {childList: true, subtree: true});
                        setTimeout(() => {
                            observer.disconnect();
                            resolve(true);
                        }, timeout);
                        button.click();
                    })""", selector, loadTimeout);
            if (!Boolean.TRUE.equals(clicked)) break;
            pagesClicked.incrementAndGet();

            extractLinks.run(page);
        }
    }

    @Override
    public String report() {
        return Behavior.super.report() + "    Pages clicked: " + pagesClicked.get() + "\n";
    }
}
