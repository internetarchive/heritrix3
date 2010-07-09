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
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.archive.crawler.framework.CrawlJob;
import org.archive.crawler.framework.Engine;
import org.archive.crawler.framework.CrawlController.State;
import org.archive.util.FileUtils;
import org.restlet.Context;
import org.restlet.data.CharacterSet;
import org.restlet.data.Form;
import org.restlet.data.MediaType;
import org.restlet.data.Request;
import org.restlet.data.Response;
import org.restlet.resource.Representation;
import org.restlet.resource.ResourceException;
import org.restlet.resource.Variant;
import org.restlet.resource.WriterRepresentation;

/**
 * Restlet Resource representing an Engine that may be used
 * to assemble, launch, monitor, and manage crawls. 
 * 
 * @contributor gojomo
 * @contributor nlevitt
 */
public class EngineResource extends BaseResource {

    public EngineResource(Context ctx, Request req, Response res) {
        super(ctx, req, res);
        setModifiable(true);
        getVariants().add(new Variant(MediaType.TEXT_HTML));
        getVariants().add(new Variant(MediaType.APPLICATION_XML));
    }

    public Representation represent(Variant variant) throws ResourceException {
        Representation representation;
        if (variant.getMediaType() == MediaType.APPLICATION_XML) {
            representation = new WriterRepresentation(MediaType.APPLICATION_XML) {
                public void write(Writer writer) throws IOException {
                    XmlMarshaller.marshalDocument(writer, "engine", makePresentableMap());
                }
            };
        } else {
            representation = new WriterRepresentation(MediaType.TEXT_HTML) {
                public void write(Writer writer) throws IOException {
                    EngineResource.this.writeHtml(writer);
                }
            };
        }
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
            if (path==null) {
                Flash.addFlash(getResponse(), "Cannot add <i>null</i> path",
                        Flash.Kind.NACK);
            } else {
                File jobFile = new File(path);
                String jobName = jobFile.getName();
                if (!jobFile.isDirectory()) {
                    Flash.addFlash(getResponse(), "Cannot add non-directory: <i>" 
                            + path + "</i>", Flash.Kind.NACK);
                } else if (getEngine().getJobConfigs().containsKey(jobName)) {
                    Flash.addFlash(getResponse(), "Job exists: <i>" 
                            + jobName + "</i>", Flash.Kind.NACK);
                } else if (getEngine().addJobDirectory(new File(path))) {
                    Flash.addFlash(getResponse(), "Added crawl job: "
                            + "'" + path + "'", Flash.Kind.ACK);
                } else {
                    Flash.addFlash(getResponse(), "Could not add job: "
                            + "'" + path + "'", Flash.Kind.NACK);
                }
            }
        } else if ("create".equals(action)) {
        	String path = form.getFirstValue("createpath");
        	if (path==null) {
                // protect against null path
        		Flash.addFlash(getResponse(), "Cannot create <i>null</i> path.", 
        		        Flash.Kind.NACK);
        	} else if (path.indexOf(File.separatorChar) != -1) {
        	    // prevent specifying sub-directories
        		Flash.addFlash(getResponse(), "Sub-directories disallowed: "
        		        + "<i>" + path + "</i>", Flash.Kind.NACK);
        	} else if (getEngine().getJobConfigs().containsKey(path)) {
        	    // protect existing jobs
        		Flash.addFlash(getResponse(), "Job exists: <i>" + path + "</i>", 
        		        Flash.Kind.NACK);
        	} else {
        	    // try to create new job dir
        	    File newJobDir = new File(getEngine().getJobsDir(),path);
        	    if (newJobDir.exists()) {
                    // protect existing directories
                    Flash.addFlash(getResponse(), "Directory exists: "
                            + "<i>" + path + "</i>", Flash.Kind.NACK);
        	    } else {
        	        if (getEngine().createNewJobWithDefaults(newJobDir)) {
        	            Flash.addFlash(getResponse(), "Created new crawl job: "
        	                    + "<i>" + path + "</i>", Flash.Kind.ACK);
        	            getEngine().findJobConfigs();
        	        } else {
                        Flash.addFlash(getResponse(), "Failed to create new job: "
                                + "<i>" + path + "</i>", Flash.Kind.NACK);
        	        }
        	    }
        	}
        } else if ("Exit Java Process".equals(action)) { 
            boolean cancel = false; 
            if(!"on".equals(form.getFirstValue("I'm sure"))) {
                Flash.addFlash(
                        getResponse(),
                        "You must tick \"I'm sure\" to trigger exit", 
                        Flash.Kind.NACK);
                cancel = true; 
            }
            for(Map.Entry<String,CrawlJob> entry : getBuiltJobs().entrySet()) {
                if(!"on".equals(form.getFirstValue("ignore__"+entry.getKey()))) {
                    Flash.addFlash(
                            getResponse(),
                            "Job '"+entry.getKey()+"' still &laquo;"
                                +entry.getValue().getJobStatusDescription()
                                +"&raquo;", 
                            Flash.Kind.NACK);
                    cancel = true; 
                }
            }
            if(!cancel) {
                System.exit(0); 
            }
        }
        // default: redirect to GET self
        getResponse().redirectSeeOther(getRequest().getOriginalRef());
    }
    
 
    protected HashMap<String, CrawlJob> getBuiltJobs() {
        HashMap<String,CrawlJob> builtJobs = new HashMap<String,CrawlJob>(); 
        for(Map.Entry<String,CrawlJob> entry : getEngine().getJobConfigs().entrySet()) {
            if(entry.getValue().hasApplicationContext()) {
                builtJobs.put(entry.getKey(),entry.getValue());
            }
        }
        return builtJobs;
    }

    protected List<String> getAvailableActions() {
        List<String> actions = new LinkedList<String>();
        actions.add("rescan");
        actions.add("add");
        actions.add("create");
        return actions;
    }

    /**
     * Constructs a nested Map data structure with the information represented
     * by this Resource. The result is particularly suitable for use with with
     * {@link XmlMarshaller}.
     * 
     * @return the nested Map data structure
     */
    protected LinkedHashMap<String,Object> makePresentableMap() {
        String baseRef = getRequest().getResourceRef().getBaseRef().toString();
        if(!baseRef.endsWith("/")) {
            baseRef += "/";
        }

        LinkedHashMap<String,Object> info = new LinkedHashMap<String,Object>();
        Engine engine = getEngine();
        info.put("heritrixVersion", engine.getHeritrixVersion());
        File jobsDir = FileUtils.tryToCanonicalize(engine.getJobsDir());
        info.put("jobsDir", jobsDir.getAbsolutePath());
        info.put("jobsDirUrl", baseRef + "jobsdir/");
        info.put("availableActions", getAvailableActions());
        info.put("heapReport", getEngine().heapReportData());

        // re-scan job configs on each page load
        ArrayList<CrawlJob> jobs = new ArrayList<CrawlJob>();

        jobs.addAll(engine.getJobConfigs().values());
        Collections.sort(jobs);
        Collection<Map<String,Object>> jobsInfo = new LinkedList<Map<String,Object>>();
        for (CrawlJob cj: jobs) {
            // cj.writeHtmlTo(pw,"job/");
            Map<String,Object> jobInfo = new LinkedHashMap<String, Object>();
            jobInfo.put("shortName", cj.getShortName());
            jobInfo.put("url", baseRef + "job/" + cj.getShortName());
            jobInfo.put("isProfile", cj.isProfile());
            jobInfo.put("launchCount", cj.getLaunchCount());
            jobInfo.put("lastLaunch", cj.getLastLaunch());
            File primaryConfig = FileUtils.tryToCanonicalize(cj.getPrimaryConfig());
            jobInfo.put("primaryConfig", primaryConfig.getAbsolutePath());
            jobInfo.put("primaryConfigUrl", baseRef + "job/" + cj.getShortName() + "/jobdir/" + primaryConfig.getName());
            if (cj.getCrawlController() != null) {
                jobInfo.put("crawlControllerState", cj.getCrawlController().getState());
                if (cj.getCrawlController().getState() == State.FINISHED) {
                    jobInfo.put("crawlExitStatus", cj.getCrawlController().getCrawlExitStatus());
                }
            }
            jobsInfo.add(jobInfo);
        }
        info.put("jobs", jobsInfo);

        return info;
    }
    
    protected void writeHtml(Writer writer) {
        Engine engine = getEngine();
        String engineTitle = "Heritrix Engine "+engine.getHeritrixVersion();
        File jobsDir = FileUtils.tryToCanonicalize(engine.getJobsDir());
        String baseRef = getRequest().getResourceRef().getBaseRef().toString();
        if(!baseRef.endsWith("/")) {
            baseRef += "/";
        }
        PrintWriter pw = new PrintWriter(writer); 
        pw.println("<head><title>"+engineTitle+"</title>");
        pw.println("<base href='"+baseRef+"'/>");
        pw.println("<link rel=\"stylesheet\" type=\"text/css\" href=\"static/engine.css\"");
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
        
        pw.println("<h2>Add Job Directory</h2>");
        
        // create new job with defaults
        pw.println("<form method=\'POST\'>\n"
        		+ "Create new job directory with recommended starting configuration<br/>\n"
        		+ "<b>Path:</b> " + jobsDir.getAbsolutePath() + File.separator + "\n"
        		+ "<input name='createpath'/>\n"
        		+ "<input type='submit' name='action' value='create'>\n"
        		+ "</form>\n");

        pw.println("<form method=\'POST\'>");
        pw.println("Specify a path to a pre-existing job directory<br/>");
        pw.println("<b>Path:</b> " + "<input size='53' name='addpath'/>");
        pw.println("<input type='submit' name='action' value='add'>");
        pw.println("</form>");

        pw.println(
                "You may also compose or copy a valid job directory into " +
                "the main jobs directory via outside means, then use the " +
                "'rescan' button above to make it appear in this interface. Or, " +
                "use the 'copy' functionality at the botton of any existing " +
                "job's detail page.");
        
        pw.println("<h2>Exit Java</h2>");

        pw.println(
                "This exits the Java process running Heritrix. To restart " +
                "will then require access to the hosting machine. You should " +
                "cleanly terminate and teardown any jobs in progress first.<br/>");
        pw.println("<form method=\'POST\'>");
        for(Map.Entry<String,CrawlJob> entry : getBuiltJobs().entrySet()) {
            pw.println("<br/>Job '"+entry.getKey()+"' still &laquo;"
                            +entry.getValue().getJobStatusDescription()
                            +"&raquo;<br>");
            String checkName = "ignore__"+entry.getKey();
            pw.println("<input type='checkbox' id='"+checkName+"' name='"
                    +checkName+"'><label for='"+checkName+"'> Ignore job '"
                    +entry.getKey()+"' and exit anyway</label><br/>"); 
        }
        pw.println("<br/><input type='submit' name='action' value='Exit Java Process'>");
        pw.println("<input type='checkbox' name=\"I'm sure\" id=\"I'm sure\"><label for=\"I'm sure\"> I'm sure</label>");
        pw.println("</form>");
        pw.println("</body>");
        pw.flush();
    }

    protected Engine getEngine() {
        return ((EngineApplication)getApplication()).getEngine();
    }
}
