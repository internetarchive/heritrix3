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

import java.io.*;

import freemarker.template.TemplateException;
import org.archive.crawler.restlet.models.ViewModel;
import org.restlet.data.CharacterSet;
import org.restlet.data.MediaType;
import org.restlet.representation.CharacterRepresentation;
import org.restlet.representation.FileRepresentation;

/**
 * Representation wrapping a FileRepresentation, displaying its contents
 * in a TextArea for editing.
 * 
 * @author gojomo
 */
public class EditRepresentation extends CharacterRepresentation {
    protected FileRepresentation fileRepresentation;
    protected EnhDirectoryResource dirResource;

    public EditRepresentation(FileRepresentation representation, EnhDirectoryResource resource) {
        super(MediaType.TEXT_HTML);
        fileRepresentation = representation;
        dirResource = resource; 
        // TODO: remove if not necessary in future?
        setCharacterSet(CharacterSet.UTF_8);
    }

    @Override
    public Reader getReader() throws IOException {
        StringWriter writer = new StringWriter((int)fileRepresentation.getSize()+100);
        write(writer); 
        return new StringReader(writer.toString());
    }

    @Override
    public void write(Writer writer) throws IOException {
        var template = ((EngineApplication)dirResource.getApplication()).getTemplateConfiguration().getTemplate("Edit.ftl");
        try {
            var model = new ViewModel();
            model.setFlashes(Flash.getFlashes(dirResource.getRequest()));
            var viewRef = dirResource.getRequest().getOriginalRef().clone();
            viewRef.setQuery(null);
            model.put("viewRef", viewRef);
            model.put("file", fileRepresentation.getFile());
            template.process(model, writer);
        } catch (TemplateException e) {
            throw new IOException("Rendering Edit.ftl: " + e.getMessage(), e);
        }
    }
}
