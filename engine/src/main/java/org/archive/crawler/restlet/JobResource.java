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
import java.io.Writer;
import java.util.logging.Logger;

import org.apache.commons.io.filefilter.IOFileFilter;
import org.apache.commons.lang3.StringUtils;
import org.archive.crawler.framework.CrawlJob;
import org.archive.crawler.framework.Engine;
import org.archive.crawler.reporting.AlertHandler;
import org.archive.crawler.reporting.AlertThreadGroup;
import org.archive.crawler.restlet.models.CrawlJobModel;
import org.archive.crawler.restlet.models.ViewModel;
import org.archive.util.FileUtils;
import org.archive.util.TextUtils;
import org.restlet.Context;
import org.restlet.data.CharacterSet;
import org.restlet.data.Form;
import org.restlet.data.MediaType;
import org.restlet.Request;
import org.restlet.Response;
import org.restlet.representation.EmptyRepresentation;
import org.restlet.representation.Representation;
import org.restlet.representation.WriterRepresentation;
import org.restlet.resource.ResourceException;
import org.restlet.representation.Variant;

import freemarker.template.Configuration;
import freemarker.template.ObjectWrapper;
import freemarker.template.Template;
import freemarker.template.TemplateException;

/**
 * Restlet Resource representing a single local CrawlJob inside an Engine.
 * 
 * @author gojomo
 * @author nlevitt
 * @author adam-miller
 */
public class JobResource extends BaseResource {
    public static final IOFileFilter EDIT_FILTER = FileUtils
            .getRegexFileFilter(".*\\.((c?xml)|(txt))$");

    @SuppressWarnings("unused")
    private static final Logger logger = Logger.getLogger(JobResource.class
            .getName());

    protected CrawlJob cj;

    @Override
    public void init(Context ctx, Request req, Response res)
            throws ResourceException {
        super.init(ctx, req, res);
        getVariants().add(new Variant(MediaType.TEXT_HTML));
        getVariants().add(new Variant(MediaType.APPLICATION_XML));
        cj = getEngine().getJob(
                TextUtils.urlUnescape((String) req.getAttributes().get("job")));
    }

    @Override
    public Representation get(Variant variant) throws ResourceException {
        if (cj == null) {
            throw new ResourceException(404);
        }

        if (variant.getMediaType() == MediaType.APPLICATION_XML) {
            return new WriterRepresentation(MediaType.APPLICATION_XML) {
                public void write(Writer writer) throws IOException {
                    CrawlJobModel model = makeDataModel();
                    model.put("heapReport", getEngine().heapReportData());
                    XmlMarshaller.marshalDocument(writer, "job", model);
                }
            };
        } else {
            ViewModel viewModel = new ViewModel();
            viewModel.put("heapReport", getEngine().heapReportData());
            viewModel.put("job", makeDataModel());
            return render("Job.ftl", viewModel);
        }
    }

    /**
     * Constructs a nested Map data structure with the information represented
     * by this Resource. The result is particularly suitable for use with with
     * {@link XmlMarshaller}.
     * 
     * @return the nested Map data structure
     */
    protected CrawlJobModel makeDataModel() {
        String baseRef = getRequest().getResourceRef().getBaseRef().toString();
        if (!baseRef.endsWith("/")) {
            baseRef += "/";
        }
        return new CrawlJobModel(cj,baseRef);
    }

    /**
     * Get a usable HrefPath, relative to the JobResource, for the given file.
     * Assumes usual helper resources ('jobdir/', 'anypath/') at the usual
     * locations.
     * 
     * @param f
     *            File to provide an href (suitable for clicking or redirection)
     * @param cj
     *            CrawlJob for calculating jobdir-relative path if possible
     * @return String path suitable as href or Location header
     */
    public static String getHrefPath(File f, CrawlJob cj) {
        String jobDirRelative = cj.jobDirRelativePath(f);
        if (jobDirRelative != null) {
            return "jobdir/" + jobDirRelative;
        }
        // TODO: delegate this to EngineApplication, or make
        // conditional on whether /anypath/ service is present?
        String fullPath = f.getAbsolutePath();
        fullPath = fullPath.replace(File.separatorChar, '/');
        if (!fullPath.startsWith("/")) fullPath = "/" + fullPath;
        return "../../anypath" + fullPath;
    }

    @Override
    public Representation post(Representation entity, Variant variant)
            throws ResourceException {
        if (cj == null) {
            throw new ResourceException(404);
        }

        // copy op?
        Form form = new Form(entity);
        String copyTo = form.getFirstValue("copyTo");
        if (copyTo != null) {
            copyJob(copyTo, "on".equals(form.getFirstValue("asProfile")));
            return new EmptyRepresentation();
        }
        AlertHandler.ensureStaticInitialization();
        AlertThreadGroup.setThreadLogger(cj.getJobLogger());
        String action = form.getFirstValue("action");
        if ("launch".equals(action)) {
            String selectedCheckpoint = form.getFirstValue("checkpoint");
            if (StringUtils.isNotEmpty(selectedCheckpoint)) {
                cj.getCheckpointService().setRecoveryCheckpointByName(
                        selectedCheckpoint);
            }
            cj.launch();
        } else if ("checkXML".equals(action)) {
            cj.checkXML();
        } else if ("instantiate".equals(action)) {
            cj.instantiateContainer();
        } else if ("build".equals(action) || "validate".equals(action)) {
            cj.validateConfiguration();
        } else if ("teardown".equals(action)) {
            if (!cj.teardown()) {
                Flash.addFlash(getResponse(), "waiting for job to finish",
                        Flash.Kind.NACK);
            }
        } else if ("pause".equals(action)) {
            cj.getCrawlController().requestCrawlPause();
        } else if ("unpause".equals(action)) {
            cj.getCrawlController().requestCrawlResume();
        } else if ("checkpoint".equals(action)) {
            String cp = cj.getCheckpointService().requestCrawlCheckpoint();
            if (StringUtils.isNotEmpty(cp)) {
                Flash.addFlash(getResponse(), "Checkpoint <i>" + cp
                        + "</i> saved", Flash.Kind.ACK);
            } else {
                Flash.addFlash(
                        getResponse(),
                        "Checkpoint not made -- perhaps no progress since last? (see logs)",
                        Flash.Kind.NACK);
            }
        } else if ("terminate".equals(action)) {
            cj.terminate();
        }
        AlertThreadGroup.setThreadLogger(null);

        // default: redirect to GET self
        getResponse().redirectSeeOther(getRequest().getOriginalRef());
        return new EmptyRepresentation();
    }

    protected void copyJob(String copyTo, boolean asProfile)
            throws ResourceException {
        try {
            getEngine().copy(cj, copyTo, asProfile);
        } catch (IOException e) {
            Flash.addFlash(getResponse(), "Job not copied: " + e.getMessage(),
                    Flash.Kind.NACK);
            getResponse().redirectSeeOther(getRequest().getOriginalRef());
            return;
        }
        // redirect to destination job page
        getResponse().redirectSeeOther(copyTo);
    }

}
