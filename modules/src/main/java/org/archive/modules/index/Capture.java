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

import java.nio.file.Path;
import java.util.UUID;

/**
 * A response or revisit record written to a WARC file, as stored in a {@link CaptureIndex}.
 *
 * @param time             fetch start time in milliseconds
 * @param digest           payload digest, e.g. {@code sha1:BASE32}. For a revisit, the digest of
 *                         the payload it refers to.
 * @param recordId         the record's WARC-Record-ID
 * @param length           length of the record in the file, compressed if the file is
 * @param requestOffset    offset of the matching request record in the same file, or -1 if none
 *                         was written
 * @param path             the file holding the record, without any {@code .open} suffix
 * @param refersToPath     for a revisit, the file holding the record it refers to, or null if it's
 *                         not known
 * @param refersToOffset   for a revisit, the offset of the record it refers to, or -1 if it's not
 *                         known
 */
public record Capture(long time, int status, String mimeType, String digest, UUID recordId,
                      boolean revisit, boolean truncated, Path path, long offset, long length,
                      long requestOffset, Path refersToPath, long refersToOffset) {
}
