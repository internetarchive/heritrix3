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

import java.util.List;

import org.restlet.Context;
import org.restlet.data.MediaType;
import org.restlet.data.Preference;
import org.restlet.data.Request;
import org.restlet.data.Response;
import org.restlet.resource.Resource;
import org.restlet.resource.Variant;

/**
 * Abstract {@code Resource} with common shared functionality. 
 * 
 * @contributor nlevitt
 */
public abstract class BaseResource extends Resource {

    public BaseResource(Context ctx, Request req, Response res) {
        super(ctx, req, res);
    }

    /**
     * If client can accept text/html, always prefer it. WebKit-based browsers
     * claim to want application/xml, but we don't want to give it to them. See
     * {@link https://webarchive.jira.com/browse/HER-1603}
     */
    public Variant getPreferredVariant() {
        boolean addExplicitTextHtmlPreference = false;

        for (Preference<MediaType> mediaTypePreference: getRequest().getClientInfo().getAcceptedMediaTypes()) {
            if (mediaTypePreference.getMetadata().equals(MediaType.TEXT_HTML)) {
                mediaTypePreference.setQuality(Float.MAX_VALUE);
                addExplicitTextHtmlPreference = false;
                break;
            } else if (mediaTypePreference.getMetadata().includes(MediaType.TEXT_HTML)) {
                addExplicitTextHtmlPreference = true;
            }
        }
        
        if (addExplicitTextHtmlPreference) {
            List<Preference<MediaType>> acceptedMediaTypes = getRequest().getClientInfo().getAcceptedMediaTypes();
            acceptedMediaTypes.add(new Preference<MediaType>(MediaType.TEXT_HTML, Float.MAX_VALUE));
            getRequest().getClientInfo().setAcceptedMediaTypes(acceptedMediaTypes);
        }
        
        
        return super.getPreferredVariant();
    }
}
