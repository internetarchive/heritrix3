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
import java.io.PrintWriter;
import java.io.StringWriter;

import org.archive.crawler.framework.Engine;
import org.archive.util.TextUtils;
import org.restlet.Application;
import org.restlet.Directory;
import org.restlet.Redirector;
import org.restlet.Restlet;
import org.restlet.Router;
import org.restlet.data.MediaType;
import org.restlet.data.Reference;
import org.restlet.data.Request;
import org.restlet.data.Response;
import org.restlet.data.Status;
import org.restlet.resource.Representation;
import org.restlet.resource.StringRepresentation;
import org.restlet.service.StatusService;
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
        setStatusService(new EngineStatusService());
    }

     public synchronized Restlet createRoot() {
        Router router = new Router(getContext());

        router.attach("/",new Redirector(null,"/engine",Redirector.MODE_CLIENT_TEMPORARY));
        router.attach("/engine",EngineResource.class)
            .setMatchingMode(Template.MODE_EQUALS);
        router.attach("/engine/",EngineResource.class)
            .setMatchingMode(Template.MODE_EQUALS);

        Directory alljobsdir = new Directory(
                getContext(),
                engine.getJobsDir().toURI().toString());
        alljobsdir.setListingAllowed(true);
        router.attach("/engine/jobsdir",alljobsdir);
        
        
        EnhDirectory anypath = new EnhDirectory(
                getContext(),
                engine.getJobsDir().toURI().toString() /*TODO: changeme*/) {
                    @Override
                    Reference determineRootRef(Request request) {
                        String ref = "file:/";
                        return new Reference(ref);
                    }};
        anypath.setListingAllowed(true);
        anypath.setModifiable(true);
        anypath.setEditFilter(JobResource.EDIT_FILTER);
        
        router.attach("/engine/anypath/",anypath);
        
        EnhDirectory jobdir = new EnhDirectory(
                getContext(),
                engine.getJobsDir().toURI().toString() /*TODO: changeme*/) {
                    @Override
                    Reference determineRootRef(Request request) {
                        try {
                            return new Reference(
                                EngineApplication.this.getEngine()
                                .getJob(TextUtils.urlUnescape(
                                    (String)request.getAttributes().get("job")))
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
        router.attach("/engine/job/{job}/report/{reportClass}",ReportGenResource.class);
        router.attach("/engine/job/{job}/beans",BeanBrowseResource.class);
        router.attach("/engine/job/{job}/beans/{beanPath}",BeanBrowseResource.class);
        router.attach("/engine/job/{job}/script",ScriptResource.class);

        // static files (won't serve directory, but will serve files in it)
        String resource = "clap://class/org/archive/crawler/restlet";
        Directory staticDir = new Directory(getContext(),resource); 
        router.attach("/engine/static/",staticDir);

        return router;
    }

    public Engine getEngine() {
        return engine;
    }  
    
    /**
     * Customize Restlet error to include back button and full stack.
     */
    protected class EngineStatusService extends StatusService {

        @Override
        public Representation getRepresentation(Status status, Request request, Response response) {
            StringWriter st = new StringWriter();
            PrintWriter pw = new PrintWriter(st);
            if(status.getCode()==404){
                pw.append("<h1>Page not found</h1>\n");
                pw.append("The page you are looking for does not exist.  "+
                        "You may be able to recover by going " +
                "<a href='javascript:history.back();void(0);'>back</a>.\n");
            }
            else{
                pw.append("<h1>An error occured</h1>\n");
                pw.append(
                        "You may be able to recover and try something " +
                        "else by going " +
                "<a href='javascript:history.back();void(0);'>back</a>.\n");
                if(status.getThrowable()!=null) {
                    pw.append("<h2>Cause: "+
                            status.getThrowable().toString()+"</h2>\n");
                    pw.append("<pre>");
                    status.getThrowable().printStackTrace(pw);
                    pw.append("</pre>");
                }
            }
            pw.flush();
            return new StringRepresentation(st.toString(),MediaType.TEXT_HTML);
        }

    }

}
