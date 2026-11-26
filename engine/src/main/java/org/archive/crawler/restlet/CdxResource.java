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

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Path;
import java.util.List;

import org.archive.crawler.framework.Frontier;
import org.archive.crawler.frontier.AbstractFrontier;
import org.archive.modules.index.Capture;
import org.archive.modules.index.CaptureIndex;
import org.archive.util.SURT;
import org.restlet.Context;
import org.restlet.Request;
import org.restlet.Response;
import org.restlet.data.Form;
import org.restlet.data.MediaType;
import org.restlet.data.Status;
import org.restlet.representation.Representation;
import org.restlet.representation.Variant;
import org.restlet.representation.WriterRepresentation;
import org.restlet.resource.ResourceException;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;

/**
 * Lists the captures in a job's {@link CaptureIndex} as CDX lines. The {@code url} parameter
 * takes a URL, a URL prefix ending in {@code *}, or a domain starting with {@code *.}. Without
 * a scheme, both http and https are matched. Without {@code url}, every capture is listed.
 */
public class CdxResource extends JobRelatedResource {
    @Override
    public void init(Context ctx, Request req, Response res) {
        super.init(ctx, req, res);
        getVariants().add(new Variant(MediaType.TEXT_PLAIN));
    }

    @Override
    protected Representation get(Variant variant) throws ResourceException {
        if (!cj.isRunning()) {
            throw new ResourceException(Status.CLIENT_ERROR_NOT_FOUND, "Job isn't running");
        }
        CaptureIndex index;
        try {
            index = cj.getJobContext().getBean(CaptureIndex.class);
        } catch (NoSuchBeanDefinitionException e) {
            throw new ResourceException(Status.CLIENT_ERROR_NOT_FOUND,
                    "CaptureIndex isn't configured");
        }

        var queries = parseQuery(getRequest().getResourceRef().getQueryAsForm(),
                cj.getCrawlController().getFrontier());
        Path jobDir = cj.getJobDir().toPath().toAbsolutePath().normalize();
        return new WriterRepresentation(MediaType.TEXT_PLAIN) {
            @Override
            public void write(Writer writer) throws IOException {
                writer.write(" CDX N b a m s k r M S V g\n");
                for (Query query : queries) {
                    Capture after = null;
                    while (true) {
                        var batch = index.query(query.urlKey(), query.exact(), after, 512);
                        if (batch.isEmpty()) break;
                        for (Capture capture : batch) {
                            writer.write(capture.toCdxLine(jobDir));
                            writer.write("\n");
                        }
                        after = batch.get(batch.size() - 1);
                    }
                }
            }
        };
    }

    record Query(String urlKey, boolean exact) {}

    static List<Query> parseQuery(Form form, Frontier frontier) {
        var parameter = form.getFirst("url");
        if (parameter == null) return List.of(new Query("", false));
        String url = parameter.getValue();
        if (url == null || url.isBlank()) {
            throw new ResourceException(Status.CLIENT_ERROR_BAD_REQUEST, "Empty url parameter");
        }

        boolean prefix = url.endsWith("*");
        if (prefix) url = url.substring(0, url.length() - 1);
        if (url.matches("(?i)^[a-z][a-z0-9.+-]*://.*")) {
            return List.of(queryForUrl(url, frontier, prefix));
        }
        Query http = queryForUrl("http://" + url, frontier, prefix);
        Query https = queryForUrl("https://" + url, frontier, prefix);
        return http.equals(https) ? List.of(http) : List.of(http, https);
    }

    private static Query queryForUrl(String url, Frontier frontier, boolean prefix) {
        int hostStart = url.indexOf("://") + 3;
        boolean domain = url.startsWith("*.", hostStart);
        if (domain) url = url.substring(0, hostStart) + url.substring(hostStart + 2);
        if (frontier instanceof AbstractFrontier f) {
            url = f.getFrontierPreparer().getCanonicalizationPolicy().canonicalize(url);
        }
        String key = SURT.fromURI(url, true);
        if (domain) {
            // Keep the final host-label comma so example.org excludes example2.org.
            int end = key.indexOf(')');
            if (end < 1 || key.charAt(end - 1) != ',') {
                throw new ResourceException(Status.CLIENT_ERROR_BAD_REQUEST,
                        "Domain wildcard requires a host name without a port");
            }
            key = key.substring(0, end);
        }
        return new Query(key, !prefix && !domain);
    }
}
