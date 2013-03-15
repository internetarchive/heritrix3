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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.archive.crawler.framework.CrawlJob;
import org.archive.crawler.framework.Engine;
import org.archive.crawler.framework.CrawlController.State;
import org.archive.crawler.restlet.models.CrawlJobModel;
import org.archive.crawler.restlet.models.EngineModel;
import org.archive.crawler.restlet.models.ViewModel;
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

import freemarker.template.Configuration;
import freemarker.template.DefaultObjectWrapper;
import freemarker.template.Template;
import freemarker.template.TemplateException;

/**
 * Restlet Resource representing an Engine that may be used
 * to assemble, launch, monitor, and manage crawls. 
 * 
 * @contributor gojomo
 * @contributor nlevitt
 * @contributor adam-miller
 */
public class EngineResource extends BaseResource {

    private Configuration _templateConfiguration;
    public EngineResource(Context ctx, Request req, Response res) {
        super(ctx, req, res);
        setModifiable(true);
        getVariants().add(new Variant(MediaType.TEXT_HTML));
        getVariants().add(new Variant(MediaType.APPLICATION_XML));
        
        Configuration tmpltCfg = new Configuration();
        tmpltCfg.setClassForTemplateLoading(this.getClass(),"");        
        tmpltCfg.setObjectWrapper(new DefaultObjectWrapper());
        setTemplateConfiguration(tmpltCfg);
    }

    public void setTemplateConfiguration(Configuration tmpltCfg) {
        _templateConfiguration=tmpltCfg;
    }
    public Configuration getTemplateConfiguration(){
        return _templateConfiguration;
    }
    public Representation represent(Variant variant) throws ResourceException {
        Representation representation;
        if (variant.getMediaType() == MediaType.APPLICATION_XML) {
            representation = new WriterRepresentation(MediaType.APPLICATION_XML) {
                public void write(Writer writer) throws IOException {
                    XmlMarshaller.marshalDocument(writer, "engine", makeDataModel());
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
            if(!"on".equals(form.getFirstValue("im_sure"))) {
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


    /**
     * Constructs a nested Map data structure with the information represented
     * by this Resource. The result is particularly suitable for use with with
     * {@link XmlMarshaller}.
     * 
     * @return the nested Map data structure
     */
    protected EngineModel makeDataModel() {
        String baseRef = getRequest().getResourceRef().getBaseRef().toString();
        if(!baseRef.endsWith("/")) {
            baseRef += "/";
        }
        EngineModel model = new EngineModel(getEngine(), baseRef);
        
        List<Map<String,Object>> jobList = new ArrayList<Map<String,Object>>();
        model.put("jobs", jobList);
        
        //Generate list of jobs
        ArrayList<Map.Entry<String,CrawlJob>> jobConfigurations = new ArrayList<Map.Entry<String,CrawlJob>>(getEngine().getJobConfigs().entrySet());
        Collections.sort(jobConfigurations, new Comparator<Map.Entry<String, CrawlJob>>() {
            public int compare(Map.Entry<String, CrawlJob> cj1, Map.Entry<String, CrawlJob> cj2) {
                return (cj2.getValue()).compareTo(cj1.getValue());
            }
        });
        for(Map.Entry<String,CrawlJob> jobConfig : jobConfigurations) {
            CrawlJob job = jobConfig.getValue();
            HashMap<String, Object> crawlJobModel = new HashMap<String, Object>();
            crawlJobModel.put("shortName",job.getShortName());
            crawlJobModel.put("url",baseRef+"job/"+job.getShortName());
            crawlJobModel.put("isProfile",job.isProfile());
            crawlJobModel.put("launchCount",job.getLaunchCount());
            crawlJobModel.put("lastLaunch",job.getLastLaunch());
            crawlJobModel.put("hasApplicationContext",job.hasApplicationContext());
            crawlJobModel.put("statusDescription", job.getJobStatusDescription());
            crawlJobModel.put("isLaunchInfoPartial", job.isLaunchInfoPartial());
            File primaryConfig = FileUtils.tryToCanonicalize(job.getPrimaryConfig());
            crawlJobModel.put("primaryConfig", primaryConfig.getAbsolutePath());
            crawlJobModel.put("primaryConfigUrl", baseRef + "jobdir/" + primaryConfig.getName());
            if (job.getCrawlController() != null) {
                crawlJobModel.put("crawlControllerState", job.getCrawlController().getState());
                if (job.getCrawlController().getState() == State.FINISHED) {
                    crawlJobModel.put("crawlExitStatus", job.getCrawlController().getCrawlExitStatus());
                }
            }
            
            crawlJobModel.put("key", jobConfig.getKey());
            jobList.add(crawlJobModel);
        }
        
        

        return model;
    }
    
    protected void writeHtml(Writer writer) {
        EngineModel model = makeDataModel();
        String baseRef = getRequest().getResourceRef().getBaseRef().toString();
        if(!baseRef.endsWith("/")) {
            baseRef += "/";
        }
        Configuration tmpltCfg = getTemplateConfiguration();

        ViewModel viewModel = new ViewModel();
        viewModel.setFlashes(Flash.getFlashes(getRequest()));
        viewModel.put("baseRef",baseRef);
        viewModel.put("filePathSeparator", File.pathSeparator);
        viewModel.put("engine", model);
        viewModel.put("cssRef", getStylesheetRef());

        try {
            Template template = tmpltCfg.getTemplate("Engine.ftl");
            template.process(viewModel, writer);
            writer.flush();
        } catch (IOException e) { 
            throw new RuntimeException(e); 
        } catch (TemplateException e) { 
            throw new RuntimeException(e); 
        }
    }

    protected Engine getEngine() {
        return ((EngineApplication)getApplication()).getEngine();
    }
}
