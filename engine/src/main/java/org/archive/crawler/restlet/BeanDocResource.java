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

import org.apache.commons.codec.digest.DigestUtils;
import org.restlet.data.MediaType;
import org.restlet.data.Status;
import org.restlet.data.Tag;
import org.restlet.representation.Representation;
import org.restlet.representation.StringRepresentation;
import org.restlet.resource.Get;
import org.restlet.resource.ServerResource;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Enumeration;

public class BeanDocResource extends ServerResource {
    private static Tag etag = null;

    @Get("json")
    public Representation getBeans() throws IOException {
        if (etag != null && getRequest().getConditions().getNoneMatch().contains(etag)) {
            getResponse().setStatus(Status.REDIRECTION_NOT_MODIFIED);
            return null;
        }
        var resources = getClass().getClassLoader().getResources("META-INF/heritrix-beans.json");
        var json = concatenateJsonObjects(resources);
        var representation = new StringRepresentation(json, MediaType.APPLICATION_JSON);
        if (etag == null) etag = new Tag(DigestUtils.md5Hex(json));
        representation.setTag(etag);
        return representation;
    }

    private static String concatenateJsonObjects(Enumeration<URL> resources) throws IOException {
        var builder = new StringBuilder("{");
        for (var url : Collections.list(resources)) {
            try (var stream = url.openStream()) {
                var json = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
                if (json.isEmpty()) continue;
                if (builder.length() > 1) builder.append(",");

                // strip leading '{' and trailing '}' (we just assume no whitespace)
                builder.append(json, 1, json.length() - 1);
            }
        }
        builder.append("}");
        return builder.toString();
    }
}
