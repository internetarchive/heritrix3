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

import org.apache.commons.lang.StringUtils;
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
import org.restlet.resource.Resource;
import org.restlet.resource.ResourceException;
import org.restlet.resource.Variant;
import org.restlet.resource.WriterRepresentation;
import org.xml.sax.SAXException;

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
        getVariants().add(new Variant(MediaType.APPLICATION_XML));
    }

    public Representation represent(Variant variant) throws ResourceException {
        Representation representation;
        if (variant.getMediaType() == MediaType.APPLICATION_XML) {
            representation = new WriterRepresentation(MediaType.APPLICATION_XML) {
                public void write(Writer writer) throws IOException {
                    try {
                        new XmlMarshaller(writer).marshalDocument("engine", presentablify());
                    } catch (SAXException e) {
                        throw new IOException(e);
                    }
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
            if(path==null) {
                path = "";
            }
            boolean added = false;
            if(StringUtils.isNotBlank(path)) {
                added = getEngine().considerAsJobDirectory(new File(path));
            }
            if(!added) {
            	String msg = messageDiv("ERROR! invalid job path: '"+path+"'","ERROR");
                Flash.addFlash(getResponse(),msg,Flash.Kind.NACK);
            } else {
            	try { 
            		getEngine().leaveJobPathFile(path);
            		String msg = messageDiv("Added job path: '"+path+"'","MESSAGE");
                    Flash.addFlash(getResponse(),msg);
            	} catch (IOException e) {
                    String msg = messageDiv(e.getMessage(),"ERROR");
            		Flash.addFlash(getResponse(),msg,Flash.Kind.NACK); 
            	}
            }
        } else if ("create".equals(action)) {
        	String path = form.getFirstValue("addpath");
        	if (path==null) {
        		String warn = messageDiv("WARNING! null path given.","WARNING");
        		Flash.addFlash(getResponse(), warn, Flash.Kind.NACK);
        	} else if (path.indexOf("/") != -1) {
        		String warn = messageDiv("WARNING! sub-directories disallowed: <i>" + path + "</i>","WARNING");
        		Flash.addFlash(getResponse(), warn, Flash.Kind.NACK);
        	} else if (getEngine().getJobConfigs().containsKey(path)) {
        		String warn = messageDiv("ERROR! job exists: <i>" + path + "</i>","ERROR");
        		Flash.addFlash(getResponse(), warn, Flash.Kind.NACK);
        	} else {
        		boolean created = false;
        		try {
        			created = getEngine().createNewJobWithDefaults(path);
        		} catch (IOException e) {
        			String err = messageDiv("ERROR! " + e.toString(),"ERROR");
        			Flash.addFlash(getResponse(), err, Flash.Kind.NACK);
        		}
        		if (created) {
        			String msg = messageDiv("Successfully created job: <i>" + path + "</i>","MESSAGE");
        			Flash.addFlash(getResponse(), msg, Flash.Kind.ACK);
        			getEngine().findJobConfigs();
        		}
        	}
        }
        // default: redirect to GET self
        getResponse().redirectSeeOther(getRequest().getOriginalRef());
    }
    
    protected List<String> getAvailableActions() {
        List<String> actions = new LinkedList<String>();
        actions.add("rescan");
        actions.add("add");
        actions.add("create");
        return actions;
    }

    protected LinkedHashMap<String,Object> presentablify() {
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
        engine.findJobConfigs();
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
    
    /**
     * wraps a message in a styled div given messsage type 
     * @param msg message to be displayed
     * @param type message type selector
     * @return string wrapped in styled <div/>
     * TODO: put this in a sensible place, and use a stylesheet instead
     */
    protected String messageDiv(String message, String type) {
    	HashMap<String,String> colorMap = new HashMap<String,String>();
    	colorMap.put("ERROR","pink");
    	colorMap.put("WARNING","lightyellow");
    	colorMap.put("MESSAGE","lavender");
    	String color;
    	if (colorMap.containsKey(type)) {
    		color = colorMap.get(type);
    	} else {
    		color = "gray";
    	}
    	String style = "style=\"margin:1em;padding:0.2em 1em;"
    		+ "background:" + color + ";\"";
    	return "<div " + style + ">" + message + "</div>\n";
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
        pw.println("</head><body>");
        pw.println("<h1>"+engineTitle+"</h1>"); 
        
        Flash.renderFlashesHTML(pw, getRequest());

        pw.println("<b>Memory: </b>");
        pw.println(engine.heapReport());
        pw.println("<br/><br/>");
        pw.println("<b>Jobs Directory</b>: <a href='jobsdir'>"+jobsDir.getAbsolutePath()+"</a></h2>");
        
        ArrayList<CrawlJob> jobs = new ArrayList<CrawlJob>();
        
        // re-scan job configs on each page load
        engine.findJobConfigs();
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
