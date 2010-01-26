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
import java.net.URI;
import java.util.List;
import java.util.ListIterator;

import org.apache.commons.io.FileUtils;
import org.restlet.data.CharacterSet;
import org.restlet.data.Form;
import org.restlet.data.Reference;
import org.restlet.data.Request;
import org.restlet.data.Response;
import org.restlet.data.Status;
import org.restlet.resource.FileRepresentation;
import org.restlet.resource.Representation;
import org.restlet.resource.ResourceException;
import org.restlet.resource.Variant;

import com.noelios.restlet.local.DirectoryResource;

/**
 * Enhanced version of Restlet DirectoryResource, adding ability to 
 * edit some files. 
 * 
 * @contributor gojomo
 */
public class EnhDirectoryResource extends DirectoryResource {
    
    public EnhDirectoryResource(EnhDirectory directory, Request request, Response response) throws IOException {
        super(directory, request, response);
    }

    /** 
     * Add EditRepresentation as a variant when appropriate. 
     * 
     * @see com.noelios.restlet.local.DirectoryResource#getVariants()
     */
    @Override
    public List<Variant> getVariants() {
        List<Variant> variants = super.getVariants();
        Form f = getRequest().getResourceRef().getQueryAsForm();
        String format = f.getFirstValue("format");
        if("textedit".equals(format)) {
            if(variants.isEmpty()) {
                // create empty placeholder file if appropriate
                try {
                    File file = new File(new URI(getTargetUri()));
                    if(getEnhDirectory().allowsEdit(file)) {
                        file.createNewFile();
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e); 
                }
                variants = super.getVariants();
            }
            // wrap FileRepresentations in EditRepresentations
            ListIterator<Variant> iter = variants.listIterator(); 
            while(iter.hasNext()) {
                Variant v = iter.next(); 
                if(v instanceof FileRepresentation) {
                    File file = ((FileRepresentation)v).getFile();
                    if(getEnhDirectory().allowsEdit(file)) {
                        iter.remove();
                        // any editable file for our purposes should 
                        // be XML/UTF-8
                        v.setCharacterSet(CharacterSet.UTF_8);
                        iter.add(new EditRepresentation((FileRepresentation)v,this));
                    };
                }
            }
        } else if("paged".equals(format)) {
            ListIterator<Variant> iter = variants.listIterator(); 
            while(iter.hasNext()) {
                Variant v = iter.next(); 
                if(v instanceof FileRepresentation) {
                    File file = ((FileRepresentation)v).getFile();
                    if(getEnhDirectory().allowsPaging(file)) {
                        iter.remove();
                        iter.add(new PagedRepresentation((
                                FileRepresentation)v,
                                this,
                                f.getFirstValue("pos"),
                                f.getFirstValue("lines"),
                                f.getFirstValue("reverse")));
                    };
                }
            }
        }
        
        return variants; 
    }
    
    protected EnhDirectory getEnhDirectory() {
        return (EnhDirectory)getDirectory();
    }

    /** 
     * Accept a POST used to edit or create a file.
     * 
     * @see org.restlet.resource.Resource#acceptRepresentation(org.restlet.resource.Representation)
     */
    public void acceptRepresentation(Representation entity)
    throws ResourceException {
        // TODO: only allowPost on valid targets
        Form form = getRequest().getEntityAsForm();
        String newContents = form.getFirstValue("contents");
        EditRepresentation er;
        try {
            er = (EditRepresentation) getVariants().get(0);
        } catch (ClassCastException cce) {
            throw new ResourceException(Status.CLIENT_ERROR_BAD_REQUEST,
                    "File modification should use either PUT or " +
                    "POST with a '?format=textedit' query-string.");
        }
        File file = er.getFileRepresentation().getFile(); 
        try {
            FileUtils.writeStringToFile(file, newContents,"UTF-8");
            Flash.addFlash(getResponse(), "file updated");
        } catch (IOException e) {
            // TODO report error somehow
            e.printStackTrace();
        }
        // redirect to view version
        Reference ref = getRequest().getOriginalRef().clone(); 
        /// ref.setQuery(null);
        getResponse().redirectSeeOther(ref);
        
    }
}
