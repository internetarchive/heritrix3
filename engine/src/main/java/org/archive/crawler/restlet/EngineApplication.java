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

import org.archive.crawler.framework.Engine;
import org.restlet.Application;
import org.restlet.Directory;
import org.restlet.Restlet;
import org.restlet.Router;
import org.restlet.data.MediaType;
import org.restlet.data.Reference;
import org.restlet.data.Request;
import org.restlet.util.Template;

/**
 * Restlet Application for a Heritrix crawl 'Engine', which is aware of
 * local job configurations/directories and can assemble/launch/monitor/
 * manage crawls. 
 * 
 * @contributor gojomo
 */
public class EngineApplication extends Application {
    Engine engine; 
    public EngineApplication(Engine engine) {
        this.engine = engine;
        getMetadataService().addExtension("log", MediaType.TEXT_PLAIN );
        getMetadataService().addExtension("cxml", MediaType.APPLICATION_XML );
    }

     public synchronized Restlet createRoot() {
        Router router = new Router(getContext());
 
        router.attach("/engine",EngineResource.class)
            .setMatchingMode(Template.MODE_EQUALS);
        router.attach("/engine/",EngineResource.class)
            .setMatchingMode(Template.MODE_EQUALS);

        Directory alljobsdir = new Directory(
                getContext(),
                engine.getJobsDir().toURI().toString());
        alljobsdir.setListingAllowed(true);
        router.attach("/engine/jobsdir",alljobsdir);
        
        
        EnhDirectory jobdir = new EnhDirectory(
                getContext(),
                engine.getJobsDir().toURI().toString() /*TODO: changeme*/) {
                    @Override
                    Reference determineRootRef(Request request) {
                        try {
                            return new Reference(
                                    EngineApplication.this.getEngine()
                                    .getJob((String)request.getAttributes().get("job"))
                                    .getJobDir().getCanonicalFile().toURI().toString());
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }};
        jobdir.setListingAllowed(true);
        jobdir.setModifiable(true);
        jobdir.setEditFilter(JobResource.EDIT_FILTER);
        router.attach("/engine/job/{job}/jobdir",jobdir);
        router.attach("/engine/job/{job}",JobResource.class);
        
        return router;
    }

    public Engine getEngine() {
        return engine;
    }  
}
