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

import freemarker.template.ObjectWrapper;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import org.archive.crawler.framework.Engine;
import org.archive.crawler.restlet.models.ViewModel;
import org.restlet.data.MediaType;
import org.restlet.representation.Representation;
import org.restlet.representation.WriterRepresentation;
import org.restlet.resource.ServerResource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;

/**
 * Abstract {@code Resource} with common shared functionality. 
 * 
 * @author nlevitt
 */
public abstract class BaseResource extends ServerResource {
    @Override
    public EngineApplication getApplication() {
        return (EngineApplication) super.getApplication();
    }

    protected Engine getEngine() {
        return getApplication().getEngine();
    }

    protected String getStaticRef(String resource) {
        String rootRef = getRequest().getRootRef().toString();
        return rootRef + "/engine/static/" + resource;
    }

    protected Representation render(String templateName, ViewModel viewModel) {
        return render(templateName, viewModel, null);
    }

    protected Representation render(String templateName, ViewModel viewModel, ObjectWrapper objectWrapper) {
        String baseRef = getRequest().getResourceRef().getBaseRef().toString();
        if(!baseRef.endsWith("/")) {
            baseRef += "/";
        }
        viewModel.put("baseRef", baseRef);
        viewModel.setFlashes(Flash.getFlashes(getRequest()));

        Template template;
        try {
            template = getApplication().getTemplateConfiguration().getTemplate(templateName);
        } catch (IOException e) {
            throw new UncheckedIOException("Error reading template " + templateName, e);
        }

        return new WriterRepresentation(MediaType.TEXT_HTML) {
            @Override
            public void write(Writer writer) throws IOException {
                try {
                    template.process(viewModel, writer, objectWrapper);
                } catch (TemplateException e) {
                    throw new RuntimeException(e);
                }
                writer.flush();
            }
        };
    }
}
