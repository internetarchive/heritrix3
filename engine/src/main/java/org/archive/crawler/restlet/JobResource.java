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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import org.apache.commons.io.filefilter.IOFileFilter;
import org.apache.commons.lang.StringEscapeUtils;
import org.apache.commons.lang.StringUtils;
import org.archive.checkpointing.Checkpoint;
import org.archive.crawler.framework.CrawlController.State;
import org.archive.crawler.framework.CrawlJob;
import org.archive.crawler.framework.Engine;
import org.archive.crawler.reporting.AlertHandler;
import org.archive.crawler.reporting.AlertThreadGroup;
import org.archive.crawler.reporting.Report;
import org.archive.spring.ConfigPath;
import org.archive.util.ArchiveUtils;
import org.archive.util.FileUtils;
import org.archive.util.TextUtils;
import org.restlet.Context;
import org.restlet.data.CharacterSet;
import org.restlet.data.Form;
import org.restlet.data.MediaType;
import org.restlet.data.Reference;
import org.restlet.data.Request;
import org.restlet.data.Response;
import org.restlet.resource.Representation;
import org.restlet.resource.ResourceException;
import org.restlet.resource.Variant;
import org.restlet.resource.WriterRepresentation;

/**
 * Restlet Resource representing a single local CrawlJob inside an
 * Engine.
 * 
 * @contributor gojomo
 * @contributor nlevitt
 */
public class JobResource extends BaseResource {
    public static final IOFileFilter EDIT_FILTER = 
        FileUtils.getRegexFileFilter(".*\\.((c?xml)|(txt))$");

    @SuppressWarnings("unused")
    private static final Logger logger = Logger.getLogger(JobResource.class.getName());

    protected CrawlJob cj; 
    
    public JobResource(Context ctx, Request req, Response res) throws ResourceException {
        super(ctx, req, res);
        setModifiable(true);
        getVariants().add(new Variant(MediaType.TEXT_HTML));
        getVariants().add(new Variant(MediaType.APPLICATION_XML));
        cj = getEngine().getJob(TextUtils.urlUnescape((String)req.getAttributes().get("job")));
    }

    public Representation represent(Variant variant) throws ResourceException {
        if(cj==null) {
            throw new ResourceException(404);
        }

        Representation representation = null;
        if (variant.getMediaType() == MediaType.APPLICATION_XML) {
            representation = new WriterRepresentation(MediaType.APPLICATION_XML) {
                public void write(Writer writer) throws IOException {
                    XmlMarshaller.marshalDocument(writer, "job", makePresentableMap());
                    }
            };
        } else {
            representation = new WriterRepresentation(MediaType.TEXT_HTML) {
            public void write(Writer writer) throws IOException {
                JobResource.this.writeHtml(writer);
            }
        };
        }
        
        // TODO: remove if not necessary in future?
        // honor requested charset?
        representation.setCharacterSet(CharacterSet.UTF_8);
        return representation;
    }

    /**
     * Constructs a nested Map data structure with the information represented
     * by this Resource. The result is particularly suitable for use with with
     * {@link XmlMarshaller}.
     * 
     * @return the nested Map data structure
     */
    protected LinkedHashMap<String,Object> makePresentableMap() {
        LinkedHashMap<String,Object> info = new LinkedHashMap<String,Object>();

        String baseRef = getRequest().getResourceRef().getBaseRef().toString();
        if (!baseRef.endsWith("/")) {
            baseRef += "/";
        }
        Reference baseRefRef = new Reference(baseRef);
        
        info.put("shortName", cj.getShortName());
        if (cj.getCrawlController() != null) {
            info.put("crawlControllerState", cj.getCrawlController().getState());
            if (cj.getCrawlController().getState() == State.FINISHED) {
                info.put("crawlExitStatus", cj.getCrawlController().getCrawlExitStatus());
            }
        }
        info.put("statusDescription", cj.getJobStatusDescription());
        info.put("availableActions", getAvailableActions());

        info.put("launchCount", cj.getLaunchCount());
        info.put("lastLaunch", cj.getLastLaunch());
        info.put("isProfile", cj.isProfile());
        File primaryConfig = FileUtils.tryToCanonicalize(cj.getPrimaryConfig());
        info.put("primaryConfig", primaryConfig.getAbsolutePath());
        info.put("primaryConfigUrl", baseRef + "jobdir/" + primaryConfig.getName());

        if (cj.getJobLog().exists()) try {
            List<String> logLines = new LinkedList<String>();
            FileUtils.pagedLines(cj.getJobLog(), -1, -5, logLines);
            info.put("jobLogTail", logLines);
        } catch (IOException ioe) {
            throw new RuntimeException(ioe); 
        }
        
        if (cj.hasApplicationContext()) {
            info.put("uriTotalsReport", cj.uriTotalsReportData());
            info.put("sizeTotalsReport", cj.sizeTotalsReportData());
            info.put("rateReport", cj.rateReportData());
            info.put("loadReport", cj.loadReportData());
            info.put("elapsedReport", cj.elapsedReportData());
            info.put("threadReport", cj.threadReportData());
            info.put("frontierReport", cj.frontierReportData());
            info.put("heapReport", getEngine().heapReportData());
            
            if ((cj.isRunning() || (cj.hasApplicationContext() && !cj.isLaunchable()))
                    && cj.getCrawlController().getLoggerModule().getCrawlLogPath().getFile().exists()) {
                try {
                    List<String> logLines = new LinkedList<String>();
                    FileUtils.pagedLines(
                            cj.getCrawlController().getLoggerModule().getCrawlLogPath().getFile(),
                            -1, -10, logLines);
                    info.put("crawlLogTail", logLines);
                } catch (IOException ioe) {
                    throw new RuntimeException(ioe); 
                }
            }
        }

        List<Map<String,String>> configFiles = new LinkedList<Map<String,String>>();
        for(String cppp: cj.getConfigPaths().keySet()) {
            Map<String,String> configFileInfo = new LinkedHashMap<String,String>();
            configFileInfo.put("key", cppp);
            File path = FileUtils.tryToCanonicalize(cj.getConfigPaths().get(cppp).getFile());
            configFileInfo.put("path", path.getAbsolutePath());
            Reference urlRef = new Reference(baseRefRef, getHrefPath(path, cj)).getTargetRef();
            configFileInfo.put("url", urlRef.toString());
            configFiles.add(configFileInfo);
        }
        info.put("configFiles", configFiles);

        return info;
    }

    protected Set<String> getAvailableActions() {
        Set<String> actions = new LinkedHashSet<String>();
        
        if (!cj.hasApplicationContext()) {
            actions.add("build");
        }
        if (!cj.isProfile() && cj.isLaunchable()) {
            actions.add("launch");
        }
        if (cj.isPausable()) {
            actions.add("pause");
        }
        if (cj.isUnpausable()) {
            actions.add("unpause");
        }
        if (cj.getCheckpointService() != null && cj.isRunning()) {
            actions.add("checkpoint");
        }
        if (cj.isRunning()) {
            actions.add("terminate");
        }
        if (cj.hasApplicationContext()) {
            actions.add("teardown");
        }
        
        return actions;
    }
    
    protected void writeHtml(Writer writer) {
        PrintWriter pw = new PrintWriter(writer); 
        String jobTitle = cj.getShortName() + " - " 
                          + cj.getJobStatusDescription() 
                          + " - Job main page";
        String baseRef = getRequest().getResourceRef().getBaseRef().toString();
        if(!baseRef.endsWith("/")) {
            baseRef += "/";
        }
        // TODO: replace with use a templating system (FreeMarker?)
        pw.println("<head><title>"+jobTitle+"</title>");
        pw.println("<base href='"+baseRef+"'/>");
        pw.println("<link rel=\"stylesheet\" type=\"text/css\" href=\"../../static/engine.css\"");
        pw.println("</head><body>");
        pw.print("<h1>Job <i>"+cj.getShortName()+"</i> (");
        
        if (cj.isLaunchInfoPartial()) {
          pw.print("at least ");
        }
        pw.print(cj.getLaunchCount() + " launches");
        if(cj.getLastLaunch()!=null) {
            long ago = System.currentTimeMillis() - cj.getLastLaunch().getMillis();
            pw.print(", last "+ArchiveUtils.formatMillisecondsToConventional(ago, 2)+" ago");
        }
        pw.println(")</h1>");
        
        Flash.renderFlashesHTML(pw, getRequest());
        
        if(cj.isProfile()) {
            pw.print(
                "<p>As a <i>profile</i>, this job may be built for " +
                "testing purposes but not launched. Use the 'copy job to' " +
                "functionality at bottom to copy this profile to a " +
                "launchable job.</p>");
        }
        
        // button controls
        pw.println("<div style='white-space:nowrap'><form method='POST'>");
        // PREP, LAUNCH
        pw.print("<input type='submit' name='action' value='build' ");
        pw.print(cj.hasApplicationContext()?"disabled='disabled' title='build job'":"");
        pw.println("/>");
        pw.print("<input type='submit' name='action' value='launch'");
        if(cj.isProfile()) {
            pw.print("disabled='disabled' title='profiles cannot be launched'");
        }
        if(!cj.isLaunchable()) {
            pw.print("disabled='disabled' ");
        }
        pw.println("/>&nbsp;&nbsp;&nbsp;");
        
        // PAUSE, UNPAUSE, CHECKPOINT
        pw.println("<input ");
        if(!cj.isPausable()) {
            pw.println(" disabled ");
        }
        pw.println(" type='submit' name='action' value='pause'/>");
        pw.println("<input ");
        if(!cj.isUnpausable()) {
            pw.println(" disabled ");
        }
        pw.println(" type='submit' name='action' value='unpause'/>");
        pw.println("<input ");
        if(!cj.isRunning()) { 
            pw.println(" disabled ");
        }
        pw.println(" type='submit' name='action' value='checkpoint'/>");
        
        // TERMINATE, RESET
        pw.println("&nbsp;&nbsp;&nbsp;<input ");
        if(!cj.isRunning()) {
            pw.println(" disabled ");
        }
        pw.println(" type='submit' name='action' value='terminate'/>");
        pw.println("<input type='submit' name='action' value='teardown' ");
        pw.print(cj.hasApplicationContext()?"":"disabled='disabled' title='no instance'");
        pw.println("/><br/>");

        
        // display checkpoint options
        if(cj.getCheckpointService()!=null) {
            Checkpoint recoveryCheckpoint = cj.getCheckpointService().getRecoveryCheckpoint();
            if(recoveryCheckpoint!=null) {
                pw.println("recover from <i>"+recoveryCheckpoint.getName()+"</i>");
            } else if (cj.getCheckpointService().hasAvailableCheckpoints() && cj.isLaunchable()) {
                pw.println("select an available checkpoint before launch to recover:");
                pw.println("<select name='checkpoint'><option> </option>");
                for(File f : cj.getCheckpointService().findAvailableCheckpointDirectories()) {
                    pw.println("<option>"+f.getName()+"</option>");
                }
                pw.println("</select>");
            }
        }
        
        pw.println("</form></div>");

        
        // configuration 
        pw.println("configuration: ");
        printLinkedFile(pw, cj.getPrimaryConfig());
        for(File f : cj.getImportedConfigs(cj.getPrimaryConfig())) {
            pw.println("imported: ");
            printLinkedFile(pw,f);
        }
        
//        if(cj.isXmlOk()) {
//            pw.println("cxml ok<br/>");
//            if(cj.isContainerOk()) {
//                pw.println("container ok<br/>");
//                if(cj.isContainerValidated()) {
//                    pw.println("config valid<br/>");
//                } else {
//                    pw.println("CONFIG INVALID<br/>");
//                }
//            } else {
//                pw.println("CONTAINER BAD<br/>");
//            }
//        }else {
//            // pw.println("XML NOT WELL-FORMED<br/>");
//        }

        pw.println("<h2>Job Log ");
        pw.println("(<a href='jobdir/"
                +cj.getJobLog().getName()
                +"?format=paged&pos=-1&lines=-128&reverse=y'><i>more</i></a>)");
        pw.println("</h2>");
        pw.println("<div style='font-family:monospace; white-space:pre-wrap; white-space:normal; text-indent:-10px; padding-left:10px;'>");
        if(cj.getJobLog().exists()) {
            try {
                List<String> logLines = new LinkedList<String>();
                FileUtils.pagedLines(cj.getJobLog(), -1, -5, logLines);
                Collections.reverse(logLines);
                for(String line : logLines) {
                    pw.print("<p style='margin:0px'>");
                    StringEscapeUtils.escapeHtml(pw,line);
                    pw.print("</p>");
                }
            } catch (IOException ioe) {
                throw new RuntimeException(ioe); 
            }
        }
        pw.println("</div>");
         
        pw.println("<h2>Job is "+cj.getJobStatusDescription()+"</h2>");

        if(cj.hasApplicationContext()) {
            pw.println("<b>Totals</b><br/>&nbsp;&nbsp;");
            pw.println(cj.uriTotalsReport());
            pw.println("<br/>&nbsp;&nbsp;");
            pw.println(cj.sizeTotalsReport());
                        
            pw.println("<br/><b>Alerts</b><br>&nbsp;&nbsp;");
            pw.println(cj.getAlertCount()==0 ? "<i>none</i>" : cj.getAlertCount()); 
            if(cj.getAlertCount()>0) {
                printLinkedFile(
                        pw, 
                        cj.getCrawlController().getLoggerModule().getAlertsLogPath().getFile(), 
                        "tail alert log...",
                        "format=paged&pos=-1&lines=-128");
            }
            
            pw.println("<br/><b>Rates</b><br/>&nbsp;&nbsp;");
            pw.println(cj.rateReport());
            
            pw.println("<br/><b>Load</b><br/>&nbsp;&nbsp;");
            pw.println(cj.loadReport());
            
            pw.println("<br/><b>Elapsed</b><br/>&nbsp;&nbsp;");
            pw.println(cj.elapsedReport());
            
            pw.println("<br/><a href='report/ToeThreadsReport'><b>Threads</b></a><br/>&nbsp;&nbsp;");
            pw.println(cj.threadReport());
    
            pw.println("<br/><a href='report/FrontierSummaryReport'><b>Frontier</b></a><br/>&nbsp;&nbsp;");
            pw.println(cj.frontierReport());
            
            pw.println("<br/><b>Memory</b><br/>&nbsp;&nbsp;");
            pw.println(getEngine().heapReport());
            
            if ((cj.isRunning() || (cj.hasApplicationContext() && !cj.isLaunchable()))
                    && cj.getCrawlController().getLoggerModule().getCrawlLogPath().getFile().exists()) {
                // show crawl log for running or finished crawls
                pw.println("<h3>Crawl Log");
                printLinkedFile(
                        pw,
                        cj.getCrawlController().getLoggerModule().getCrawlLogPath().getFile(),
                        "<i>more</i>",
                        "format=paged&pos=-1&lines=-128&reverse=y");
                pw.println("</h3>");
                pw.println("<pre style='overflow:auto'>");
                try {
                    List<String> logLines = new LinkedList<String>();
                    FileUtils.pagedLines(
                            cj.getCrawlController().getLoggerModule().getCrawlLogPath().getFile(),
                            -1, 
                            -10, 
                            logLines);
                    Collections.reverse(logLines);
                    for(String line : logLines) {
                        StringEscapeUtils.escapeHtml(pw,line);
                        pw.println();
                    }
                } catch (IOException ioe) {
                    throw new RuntimeException(ioe); 
                }
                pw.println("</pre>");
            }
            
        }
        
        if(cj.hasApplicationContext()) {
            pw.println("<h2>Reports</h2>");
            for (Report report: cj.getCrawlController().getStatisticsTracker().getReports()) {
                if (report.getShouldReportDuringCrawl()) {
                    String className = report.getClass().getSimpleName();
                    String shortName = className.substring(0,className.length()-"Report".length());
                    pw.println("<a href='report/"+className+"'>"+shortName+"</a>");
                }
            }
        }
        
        pw.println("<h2>Files</h2>");
        pw.println("<h3>Browse <a href='jobdir'>Job Directory</a></h3>");
        // specific paths from wired context
        pw.println("<h3>Configuration-referenced Paths</h3>");
        if(cj.getConfigPaths().isEmpty()) {
            pw.println("<i>build the job to discover referenced paths</i>");
        } else {
            pw.println("<dl>");
            for(String cppp : cj.getConfigPaths().keySet()) {
                ConfigPath cp = cj.getConfigPaths().get(cppp);
                pw.println("<dt>"+cppp+": "+cp.getName()+"</dt>");
                pw.println("<dd>");
                if(!StringUtils.isEmpty(cp.getPath())) {
                    printLinkedFile(
                            pw, 
                            cp.getFile(), 
                            cp.getFile().toString(),
                            cp.getPath().endsWith(".log")?"format=paged&pos=-1&lines=-128&reverse=y":null);
                } else {
                    pw.println("<i>unset</i>");
                }
                pw.println("</dd>");
            }
            pw.println("</dl>");

        }
        
        pw.println("<h2>Advanced</h2>");
        pw.println("<h3><a href='script'>Scripting console</a></h3>");

        if(!cj.hasApplicationContext()) {
            pw.println("<i>build the job to browse bean instances</i>");
        } else {
            pw.println("<h3><a href='beans'>Browse beans</a></h3>");
        }

        pw.println("<h2>Copy</h2>");
        pw.println(
            "<form method='POST'>Copy job to <input name='copyTo'/>" +
            "<input value='copy' type='submit'/>" +
            "<input id='asProfile' type='checkbox' name='asProfile'/>" +
            "<label for='asProfile'>as profile</label></form>");
        pw.println("<hr/>");
    }

    /**
     * Print a link to the given File
     * 
     * @param pw PrintWriter
     * @param f File
     */
    protected void printLinkedFile(PrintWriter pw, File f) { 
        printLinkedFile(pw,f,f.toString(),null);
    }
    
    /**
     * Print a link to the given File, using the given link text
     * 
     * @param pw PrintWriter
     * @param f File
     */
    protected void printLinkedFile(PrintWriter pw, File f, String linktext, String queryString) {      
        String relativePath = JobResource.getHrefPath(f,cj);
        pw.println("<a href='" 
                + relativePath 
                + ((queryString==null) ? "" : "?" + queryString)
                + "'>" 
                + linktext +"</a>");
        if(EDIT_FILTER.accept(f)) {
            pw.println("[<a href='" 
                    + relativePath 
                    +  "?format=textedit'>edit</a>]<br/>");
        }
    }

    /**
     * Get a usable HrefPath, relative to the JobResource, for the given
     * file. Assumes usual helper resources ('jobdir/', 'anypath/') at
     * the usual locations.
     * 
     * @param f File to provide an href (suitable for clicking or redirection)
     * @param cj CrawlJob for calculating jobdir-relative path if possible
     * @return String path suitable as href or Location header
     */
    public static String getHrefPath(File f, CrawlJob cj) {
        String jobDirRelative = cj.jobDirRelativePath(f);
        if(jobDirRelative!=null) {
            return "jobdir/"+jobDirRelative;
        }
        // TODO: delegate this to EngineApplication, or make
        // conditional on whether /anypath/ service is present?
        String fullPath = f.getAbsolutePath();
        fullPath = fullPath.replace(File.separatorChar, '/');
        return "../../anypath/"+fullPath;
    }

    protected Engine getEngine() {
        return ((EngineApplication)getApplication()).getEngine();
    }

    @Override
    public void acceptRepresentation(Representation entity) throws ResourceException {
        if (cj == null) {
            throw new ResourceException(404);
        }

        // copy op?
        Form form = null;
            form = getRequest().getEntityAsForm();
        String copyTo = form.getFirstValue("copyTo");
        if(copyTo!=null) {
            copyJob(copyTo,"on".equals(form.getFirstValue("asProfile")));
            return;
        }
        AlertHandler.ensureStaticInitialization();
        AlertThreadGroup.setThreadLogger(cj.getJobLogger());
        String action = form.getFirstValue("action");
        if("launch".equals(action)) {
            String selectedCheckpoint = form.getFirstValue("checkpoint");
            if(StringUtils.isNotEmpty(selectedCheckpoint)) {
                cj.getCheckpointService().setRecoveryCheckpointByName(selectedCheckpoint);
            }
            cj.launch(); 
        } else if("checkXML".equals(action)) {
            cj.checkXML();
        } else if("instantiate".equals(action)) {
            cj.instantiateContainer();
        } else if("build".equals(action)||"validate".equals(action)) {
            cj.validateConfiguration();
        } else if("teardown".equals(action)) {
            if(!cj.teardown()) {
                Flash.addFlash(getResponse(), "waiting for job to finish", Flash.Kind.NACK);
            }
        } else if("pause".equals(action)) {
            cj.getCrawlController().requestCrawlPause();
        } else if("unpause".equals(action)) {
            cj.getCrawlController().requestCrawlResume();
        } else if("checkpoint".equals(action)) {
            String cp = cj.getCheckpointService().requestCrawlCheckpoint();
            if(StringUtils.isNotEmpty(cp)) {
                Flash.addFlash(getResponse(), "Checkpoint <i>"+cp+"</i> saved",Flash.Kind.ACK);
            } else {
                Flash.addFlash(getResponse(), "Checkpoint not made -- perhaps no progress since last? (see logs)",Flash.Kind.NACK);
            }
        } else if("terminate".equals(action)) {
            cj.terminate();
        }
        AlertThreadGroup.setThreadLogger(null);
            
        // default: redirect to GET self
        getResponse().redirectSeeOther(getRequest().getOriginalRef());
    }

    protected void copyJob(String copyTo, boolean asProfile) throws ResourceException {
        try {
            getEngine().copy(cj, copyTo, asProfile);
        } catch (IOException e) {
            Flash.addFlash(getResponse(), "Job not copied: "+e.getMessage(), Flash.Kind.NACK);
            getResponse().redirectSeeOther(getRequest().getOriginalRef());
            return;
        }
        // redirect to destination job page
        getResponse().redirectSeeOther(copyTo);
    }
    
    
}
