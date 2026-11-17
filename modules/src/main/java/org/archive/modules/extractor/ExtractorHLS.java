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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.archive.modules.CrawlURI;
import org.archive.net.UURI;
import org.archive.net.UURIFactory;
import org.archive.url.URIException;

/**
 * Extracts resources from RFC 8216 HTTP Live Streaming playlists. URI lines
 * identify segments or nested playlists; URI attributes identify keys,
 * initialization sections, alternate renditions and session data. All links
 * resolve against the fetched playlist and are treated as embedded resources.
 */
public class ExtractorHLS extends ContentExtractor {
    private static final long serialVersionUID = 1L;
    private static final Logger LOGGER = Logger.getLogger(ExtractorHLS.class.getName());
    private static final Set<String> MIME_TYPES = Set.of("application/vnd.apple.mpegurl",
            "audio/mpegurl", "application/x-mpegurl", "audio/x-mpegurl");
    private static final Set<String> URI_TAGS = Set.of("EXT-X-KEY", "EXT-X-MAP", "EXT-X-MEDIA",
            "EXT-X-I-FRAME-STREAM-INF", "EXT-X-SESSION-DATA", "EXT-X-SESSION-KEY");
    // Match contiguous attributes so URI-like text inside another quoted value
    // cannot be mistaken for a URI attribute. Quoted values can contain commas.
    private static final Pattern ATTRIBUTE = Pattern.compile(
            "\\G\\s*([A-Z0-9-]+)=(?:\"([^\"\\r\\n]*)\"|([^,\"\\s]+))\\s*(?:,|$)");

    @Override
    protected boolean shouldExtract(CrawlURI curi) {
        String type = curi.getContentType();
        if (type != null && MIME_TYPES.contains(type.split(";", 2)[0].trim().toLowerCase(Locale.ROOT))) {
            return true;
        }
        try {
            String path = curi.getUURI().getPath().toLowerCase(Locale.ROOT);
            return path.endsWith(".m3u8") || path.endsWith(".m3u");
        } catch (URIException e) {
            return false;
        }
    }

    @Override
    protected boolean innerExtract(CrawlURI curi) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                curi.getRecorder().getContentReplayInputStream(), StandardCharsets.UTF_8))) {
            String header = reader.readLine();
            if (header != null && header.startsWith("\ufeff")) {
                header = header.substring(1);
            }
            if (header == null || !header.trim().equals("#EXTM3U")) {
                return false;
            }
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }
                if (!line.startsWith("#")) {
                    extractLink(curi, line, "uri");
                } else {
                    int colon = line.indexOf(':');
                    if (colon > 0) {
                        String tag = line.substring(1, colon);
                        if (URI_TAGS.contains(tag)) {
                            extractLink(curi, uriAttribute(line.substring(colon + 1)), tag + "/URI");
                        }
                    }
                }
            }
            return true;
        } catch (IOException e) {
            LOGGER.log(Level.FINE, "Unable to read HLS playlist " + curi, e);
            return false;
        }
    }

    private String uriAttribute(String attributes) {
        Matcher matcher = ATTRIBUTE.matcher(attributes);
        String uri = null;
        int end = 0;
        while (matcher.find()) {
            if (matcher.group(1).equals("URI")) {
                uri = matcher.group(2); // RFC 8216 requires a quoted URI.
            }
            end = matcher.end();
        }
        return end == attributes.length() ? uri : null;
    }

    private void extractLink(CrawlURI curi, String candidate, String context) {
        // Variable substitution belongs to newer HLS versions. Do not crawl
        // unresolved templates as literal URLs.
        if (candidate == null || candidate.isBlank() || candidate.contains("{$")) {
            return;
        }
        try {
            UURI destination = UURIFactory.getInstance(curi.getUURI(), candidate);
            if (!"http".equalsIgnoreCase(destination.getScheme())
                    && !"https".equalsIgnoreCase(destination.getScheme())) {
                return;
            }
            if (addRelativeToBase(curi, getExtractorParameters().getMaxOutlinks(), destination.toString(),
                    HTMLLinkContext.get("hls/" + context), Hop.EMBED) != null) {
                numberOfLinksExtracted.incrementAndGet();
            }
        } catch (URIException e) {
            logUriError(e, curi.getUURI(), candidate);
        }
    }
}
