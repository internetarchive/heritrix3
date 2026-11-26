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

package org.archive.modules.index;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class CaptureTest {
    @Test
    void cdxLineGivesWarcPathRelativeToJobDir() {
        Path jobDir = Path.of("/job");
        assertEquals("http://(org,example,)/ 20261126000000 http://example.org/ text/html 200"
                        + " sha1:ABC - - 250 100 warcs/a.warc.gz",
                capture(Path.of("/job/warcs/a.warc.gz")).toCdxLine(jobDir));
        assertEquals("http://(org,example,)/ 20261126000000 http://example.org/ text/html 200"
                        + " sha1:ABC - - 250 100 /elsewhere/a.warc.gz",
                capture(Path.of("/elsewhere/a.warc.gz")).toCdxLine(jobDir));
    }

    private static Capture capture(Path path) {
        return new Capture("http://(org,example,)/", 1795651200000L, "http://example.org/", 200,
                "text/html", "sha1:ABC", UUID.randomUUID(), false, false, path, 100, 250, -1, null,
                -1);
    }
}
