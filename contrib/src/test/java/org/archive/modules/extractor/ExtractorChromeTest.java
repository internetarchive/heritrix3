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

package org.archive.modules.extractor;

import org.apache.commons.httpclient.URIException;
import org.archive.modules.CrawlURI;
import org.archive.net.UURIFactory;
import org.junit.Test;

import java.util.Collections;
import java.util.List;

import static java.util.stream.Collectors.toList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeNoException;

public class ExtractorChromeTest {
    @Test
    public void test() throws URIException {
        ExtractorChrome extractor = new ExtractorChrome();
        try {
            extractor.start();
        } catch (RuntimeException e) {
            assumeNoException("Unable to start Chrome", e);
        }
        try {
            CrawlURI curi = new CrawlURI(UURIFactory.getInstance("data:text/html,<a href=http://example.org/page2.html>link</a>"));
            extractor.innerExtract(curi);
            List<String> outLinks = curi.getOutLinks().stream().map(CrawlURI::toString).sorted().collect(toList());
            assertEquals(Collections.singletonList("http://example.org/page2.html"), outLinks);
        } finally {
            extractor.stop();
        }
    }
}