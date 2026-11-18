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
package org.archive.crawler.postprocessor;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Collection;
import java.util.List;

import org.archive.modules.CrawlURI;
import org.archive.modules.extractor.Hop;
import org.archive.modules.extractor.LinkContext;
import org.archive.net.UURIFactory;
import org.junit.jupiter.api.Test;

public class CandidatesProcessorTest {

    @Test
    public void testDropNonEmbedDuplicates() throws Exception {
        CrawlURI page = new CrawlURI(UURIFactory.getInstance("https://example.org/"));
        Collection<CrawlURI> outlinks = page.getOutLinks();
        outlinks.add(page.createCrawlURI("https://example.org/style.css", LinkContext.NAVLINK_MISC, Hop.NAVLINK));
        outlinks.add(page.createCrawlURI("https://example.org/next", LinkContext.NAVLINK_MISC, Hop.NAVLINK));
        outlinks.add(page.createCrawlURI("https://example.org/next", LinkContext.SPECULATIVE_MISC, Hop.SPECULATIVE));
        outlinks.add(page.createCrawlURI("https://example.org/app.js", LinkContext.SPECULATIVE_MISC, Hop.SPECULATIVE));
        outlinks.add(page.createCrawlURI("https://example.org/style.css", LinkContext.EMBED_MISC, Hop.EMBED));
        outlinks.add(page.createCrawlURI("https://example.org/app.js", LinkContext.EMBED_MISC, Hop.EMBED));

        CandidatesProcessor.dropNonEmbedDuplicates(outlinks);

        assertEquals(List.of(
                "L https://example.org/next",
                "X https://example.org/next",
                "E https://example.org/style.css",
                "E https://example.org/app.js"),
                outlinks.stream().map(link -> link.getLastHop() + " " + link.getURI()).toList());
    }
}
