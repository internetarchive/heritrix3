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

import java.io.File;

import org.restlet.Context;
import org.restlet.data.MediaType;
import org.restlet.Request;
import org.restlet.Response;
import org.restlet.representation.Representation;
import org.restlet.representation.StringRepresentation;
import org.restlet.resource.ResourceException;
import org.restlet.representation.Variant;

/**
 * Restlet Resource which generates fresh reports and then redirects
 * requests to the report in the filesystem. 
 * 
 * @author gojomo
 */
public class ReportGenResource extends JobRelatedResource {
    protected String reportClass;

    @Override
    public void init(Context ctx, Request req, Response res) throws ResourceException {
        super.init(ctx, req, res);
        getVariants().add(new Variant(MediaType.TEXT_PLAIN));
        reportClass = (String)req.getAttributes().get("reportClass");
    }

    @Override
    protected Representation get(Variant variant) throws ResourceException {
        // generate report
        if (cj == null || cj.getCrawlController() == null) {
            throw new ResourceException(500);
        }
        File f = cj.getCrawlController().getStatisticsTracker().writeReportFile(reportClass);
        if (f==null) {
            throw new ResourceException(500);
        }
        // redirect
        String relative = JobResource.getHrefPath(f, cj);
        if(relative!=null) {
            getResponse().redirectSeeOther("../"+relative+"?m="+f.lastModified());
            return new StringRepresentation("");
        } else {
            return new StringRepresentation(
                    "Report dumped to "+f.getAbsolutePath()
                    +" (outside job directory)");
        }
    }
}
