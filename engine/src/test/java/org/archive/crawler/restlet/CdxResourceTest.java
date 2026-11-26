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

package org.archive.crawler.restlet;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.restlet.data.Reference;

class CdxResourceTest {
    private List<CdxResource.Query> parseQuery(String query) {
        var reference = new Reference("http://localhost/engine/job/test/cdx" + query);
        return CdxResource.parseQuery(reference.getQueryAsForm(), null);
    }

    @Test
    void parsesExactAndWildcardQueries() {
        assertEquals(List.of(new CdxResource.Query("https://(org,example,)/a?x=%2B&y=b", true)),
                parseQuery("?url=https%3A%2F%2Fexample.org%2Fa%3Fx%3D%252B%26y%3Db"));
        assertEquals(List.of(new CdxResource.Query("http://(org,example,)/a", true),
                        new CdxResource.Query("https://(org,example,)/a", true)),
                parseQuery("?url=example.org/a"));
        assertEquals(List.of(new CdxResource.Query("http://(org,example,)/a/", false),
                        new CdxResource.Query("https://(org,example,)/a/", false)),
                parseQuery("?url=example.org/a/*"));
        assertEquals(List.of(new CdxResource.Query("http://(org,example,", false),
                        new CdxResource.Query("https://(org,example,", false)),
                parseQuery("?url=*.example.org/"));
        assertEquals(List.of(new CdxResource.Query("https://(com,example,", false)),
                parseQuery("?url=https://*.example.com"));
        assertEquals(List.of(new CdxResource.Query("http://(com,example,", false)),
                parseQuery("?url=http://*.example.com/"));
        assertEquals(List.of(new CdxResource.Query("", false)), parseQuery(""));
    }
}
