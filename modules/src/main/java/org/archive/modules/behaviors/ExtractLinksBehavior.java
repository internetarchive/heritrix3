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

import org.archive.url.URIException;
import org.archive.modules.CrawlURI;
import org.archive.modules.extractor.Hop;
import org.archive.modules.extractor.LinkContext;
import org.archive.modules.extractor.UriErrorLoggerModule;
import org.archive.net.UURI;
import org.archive.net.UURIFactory;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Extracts navigation links from the loaded page using JavaScript.
 */
public class ExtractLinksBehavior implements Behavior {
    private final AtomicLong numberOfLinksExtracted = new AtomicLong(0);
    private final UriErrorLoggerModule loggerModule;

    public ExtractLinksBehavior(UriErrorLoggerModule loggerModule) {
        this.loggerModule = loggerModule;
    }

    public void run(Page page) {
        List<String> urls = page.eval(/* language=JavaScript */ """
                () => {
                    const links = new Set();
                    for (const el of document.querySelectorAll('a[href]')) {
                        let href = el.href;
                        if (href instanceof SVGAnimatedString) {
                            href = new URL(href.baseVal, el.ownerDocument.location.href).toString();
                        }
                        if (href.startsWith('http://') || href.startsWith('https://')) {
                            links.add(href.replace(/#.*$/, ''));
                        }
                    }
                    return Array.from(links);
                }""");
        for (var url : urls) {
            try {
                UURI dest = UURIFactory.getInstance(page.curi().getUURI(), url);
                CrawlURI link = page.curi().createCrawlURI(dest, LinkContext.NAVLINK_MISC, Hop.NAVLINK);
                page.curi().getOutLinks().add(link);
                numberOfLinksExtracted.incrementAndGet();
            } catch (URIException e) {
                loggerModule.logUriError(e, page.curi().getUURI(), url);
            }
        }
    }

    @Override
    public String report() {
        return Behavior.super.report() + "    Links extracted: " + numberOfLinksExtracted.get() + "\n";
    }
}
