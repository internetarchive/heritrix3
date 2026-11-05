/*
 * This file is part of the Heritrix web crawler (crawler.archive.org).
 * Licensed to the Internet Archive (IA) by one or more individual contributors.
 * The IA licenses this file to You under the Apache License, Version 2.0.
 * You may obtain a copy at http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 */
package org.archive.modules.extractor;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.archive.modules.CrawlURI;
import org.archive.net.UURI;
import org.archive.net.UURIFactory;
import org.archive.url.URIException;

/**
 * Extracts start URLs, shortcut URLs, icons and screenshots from web application
 * manifests. Relative URLs use the manifest URL, not the page that linked to it.
 * Recognizes application/manifest+json, .webmanifest and HTML manifest links.
 * Run before generic JSON or JavaScript extraction to avoid treating names,
 * identity URLs and scope declarations as crawl targets.
 */
public class ExtractorWebManifest extends ContentExtractor {
    private static final long serialVersionUID = 1L;
    private static final Logger LOGGER = Logger.getLogger(ExtractorWebManifest.class.getName());
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    protected boolean shouldExtract(CrawlURI curi) {
        if (curi.getViaContext() != null && curi.getViaContext().toString()
                .equals("link[rel='manifest']/@href")) {
            return true;
        }
        String contentType = curi.getContentType();
        if (contentType != null && contentType.split(";", 2)[0].trim()
                .equalsIgnoreCase("application/manifest+json")) {
            return true;
        }
        try {
            return curi.getUURI().getPath().toLowerCase(Locale.ROOT).endsWith(".webmanifest");
        } catch (URIException e) {
            return false;
        }
    }

    @Override
    protected boolean innerExtract(CrawlURI curi) {
        try (InputStream input = curi.getRecorder().getContentReplayInputStream()) {
            JsonNode manifest = MAPPER.readTree(input);
            if (manifest == null || !manifest.isObject()) {
                return false;
            }
            extractLink(curi, manifest.path("start_url"), "start_url", Hop.NAVLINK);
            extractImages(curi, manifest.path("icons"), "icons");
            extractImages(curi, manifest.path("screenshots"), "screenshots");
            JsonNode shortcuts = manifest.path("shortcuts");
            if (shortcuts.isArray()) {
                for (JsonNode shortcut : shortcuts) {
                    extractLink(curi, shortcut.path("url"), "shortcuts/url", Hop.NAVLINK);
                    extractImages(curi, shortcut.path("icons"), "shortcuts/icons");
                }
            }
            return true;
        } catch (IOException e) {
            // Leave malformed manifests available for another content extractor.
            LOGGER.log(Level.FINE, "Unable to parse web app manifest " + curi, e);
            return false;
        }
    }

    private void extractImages(CrawlURI curi, JsonNode images, String field) {
        if (images.isArray()) {
            for (JsonNode image : images) {
                extractLink(curi, image.path("src"), field + "/src", Hop.EMBED);
            }
        }
    }

    private void extractLink(CrawlURI curi, JsonNode value, String field, Hop hop) {
        if (!value.isTextual() || value.textValue().isBlank()) {
            return;
        }
        String candidate = value.textValue().trim();
        try {
            UURI destination = UURIFactory.getInstance(curi.getUURI(), candidate);
            if (!"http".equalsIgnoreCase(destination.getScheme())
                    && !"https".equalsIgnoreCase(destination.getScheme())) {
                return;
            }
            CrawlURI link = addRelativeToBase(curi, getExtractorParameters().getMaxOutlinks(),
                    destination.toString(), HTMLLinkContext.get("manifest/" + field), hop);
            if (link != null) {
                numberOfLinksExtracted.incrementAndGet();
            }
        } catch (URIException e) {
            logUriError(e, curi.getUURI(), candidate);
        }
    }
}
