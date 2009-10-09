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
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;

import org.apache.commons.lang.StringUtils;
import org.archive.crawler.framework.CrawlJob;
import org.archive.crawler.framework.Engine;
import org.restlet.Context;
import org.restlet.data.CharacterSet;
import org.restlet.data.Form;
import org.restlet.data.MediaType;
import org.restlet.data.Request;
import org.restlet.data.Response;
import org.restlet.resource.Representation;
import org.restlet.resource.Resource;
import org.restlet.resource.ResourceException;
import org.restlet.resource.Variant;
import org.restlet.resource.WriterRepresentation;

/**
 * Restlet Resource representing an Engine that may be used
 * to assemble, launch, monitor, and manage crawls. 
 * 
 * @contributor gojomo
 */
public class EngineResource extends Resource {

    public EngineResource(Context ctx, Request req, Response res) {
        super(ctx, req, res);
        setModifiable(true);
        getVariants().add(new Variant(MediaType.TEXT_HTML));
    }

    public Representation represent(Variant variant) throws ResourceException {
        Representation representation = new WriterRepresentation(
                MediaType.TEXT_HTML) {
            public void write(Writer writer) throws IOException {
                EngineResource.this.writeHtml(writer);
            }
        };
        // TODO: remove if not necessary in future?
        representation.setCharacterSet(CharacterSet.UTF_8);
        return representation;
    }
    
    @Override
    public void acceptRepresentation(Representation entity) throws ResourceException {
        Form form = getRequest().getEntityAsForm();
        String action = form.getFirstValue("action");
        if("rescan".equals(action)) {
            getEngine().findJobConfigs(); 
        } else if ("add".equals(action)) {
            String path = form.getFirstValue("addpath");
            if(path==null) {
                path = "";
            }
            boolean added = false;
            if(StringUtils.isNotBlank(path)) {
                added = getEngine().considerAsJobDirectory(new File(path));
            }
            if(!added) {
                Flash.addFlash(getResponse(), "not a valid preexisting job directory: \""+path+"\"", Flash.Kind.NACK);
            }
        } else if ("create".equals(action)) {
        	String errStyle = "style=\"margin:1em;padding:0.2em 1em;background:pink;\"";
        	String warnStyle = errStyle;
        	String msgStyle = "style=\"margin:1em;padding:0.2em 1em;background:khaki;\"";
        	String path = form.getFirstValue("addpath");
        	if (path==null) {
        		String warn = "<div "+warnStyle+">WARNING: no job created. "
        		+ "null path given.</div>\n";
        		Flash.addFlash(getResponse(), warn, Flash.Kind.NACK);
        		System.err.println(warn);  
        	} else if (path.indexOf("/") != -1) {
        		String warn = "<div "+warnStyle+">WARNING: "
        		+ "no job created. sub-directories disallowed: "
        		+ "<i>" + path + "</i></div>\n";
        		Flash.addFlash(getResponse(), warn, Flash.Kind.NACK);
        		System.err.println(warn);
        	} else {
        		boolean created = false;
        		try {
        			created = getEngine().createNewJobWithDefaults(path);
        		} catch (IOException e) {
        			String err = "<div "+errStyle+">ERROR! failed to create new job: "
        			+ "<i>" + path + "</i> "+ e.toString() + "</div>\n";
        			Flash.addFlash(getResponse(), err, Flash.Kind.NACK);
        			System.err.println(err);

        		}
        		if (created) {
        			String msg = "<p "+msgStyle+">Successfully created job: " 
        			+ "<i>" + path + "</i></p>\n";
        			Flash.addFlash(getResponse(), msg, Flash.Kind.NACK);
        			getEngine().findJobConfigs();
        		}
        	}
        }
        // default: redirect to GET self
        getResponse().redirectSeeOther(getRequest().getOriginalRef());
    }

    protected void writeHtml(Writer writer) {
        Engine engine = getEngine();
        String engineTitle = "Heritrix Engine "+engine.getHeritrixVersion();
        File jobsDir = engine.getJobsDir();
        try {
            jobsDir = jobsDir.getCanonicalFile();
        } catch (IOException ioe) {
            // live with the noncanonical file
        }
        String baseRef = getRequest().getResourceRef().getBaseRef().toString();
        if(!baseRef.endsWith("/")) {
            baseRef += "/";
        }
        PrintWriter pw = new PrintWriter(writer); 
        pw.println("<head><title>"+engineTitle+"</title>");
        pw.println("<base href='"+baseRef+"'/>");
        pw.println("</head><body>");
        pw.println("<h1>"+engineTitle+"</h1>"); 
        
        Flash.renderFlashesHTML(pw, getRequest());

        pw.println("<b>Memory: </b>");
        pw.println(engine.heapReport());
        pw.println("<br/><br/>");
        pw.println("<b>Jobs Directory</b>: <a href='jobsdir'>"+jobsDir.getAbsolutePath()+"</a></h2>");
        
        ArrayList<CrawlJob> jobs = new ArrayList<CrawlJob>();
        jobs.addAll(engine.getJobConfigs().values());
         
        pw.println("<form method=\'POST\'><h2>Job Directories ("+jobs.size()+")");
        pw.println("<input type='submit' name='action' value='rescan'>");
        pw.println("</h2></form>");
        Collections.sort(jobs);
        pw.println("<ul>");
        for(CrawlJob cj: jobs) {
            pw.println("<li>");
            cj.writeHtmlTo(pw,"job/");
            pw.println("</li>");
        }
        pw.println("</ul>");
        pw.println(
            "To create a new job, use the 'copy' functionality on " +
            "an existing job's detail page. Or, create a new job " +
            "directory manually the main jobs directory and use the " +
            "'rescan' button above. Or, supply a full path to another " +
            "valid job directory at the engine machine below.<br/><br/>");
        
        // create new job with defaults
        pw.println("<form method=\'POST\'>\n"
        		+ "Create new job with recommended defaults<br />\n"
        		+ jobsDir.getAbsolutePath() + "/\n"
        		+ "<input size='16' name='addpath'/>\n"
        		+ "<input type='submit' name='action' value='create'>\n"
        		+ "</form>\n");

        pw.println("<form method=\'POST\'>");
        pw.println("Add job directory: <input size='50' name='addpath'/>");
        pw.println("<input type='submit' name='action' value='add'>");
        pw.println("</form>");

        pw.println("</body>");
        pw.flush();
    }

    protected Engine getEngine() {
        return ((EngineApplication)getApplication()).getEngine();
    }
}
