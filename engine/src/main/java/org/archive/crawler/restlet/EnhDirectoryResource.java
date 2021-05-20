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
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;

import org.apache.commons.io.FileUtils;
import org.restlet.data.*;
import org.restlet.engine.local.DirectoryServerResource;
import org.restlet.representation.EmptyRepresentation;
import org.restlet.representation.FileRepresentation;
import org.restlet.representation.Representation;
import org.restlet.representation.Variant;
import org.restlet.resource.ResourceException;

/**
 * Enhanced version of Restlet DirectoryResource, adding ability to 
 * edit some files. 
 * 
 * @author gojomo
 */
public class EnhDirectoryResource extends DirectoryServerResource {
    /**
     * Add EditRepresentation as a variant when appropriate. 
     * 
     * @see org.restlet.engine.local.DirectoryServerResource#getVariants()
     */
    @Override
    public List<Variant> getVariants() {
        List<Variant> superVariants = super.getVariants();
        if (superVariants == null) {
            return null; // PUT and DELETE return no content
        }
        List<Variant> variants = new LinkedList<>(superVariants);
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
                superVariants = super.getVariants();
                if (superVariants == null) {
                    return null;
                }
                variants = new LinkedList<>(superVariants);
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
        } else {
            ListIterator<Variant> iter = variants.listIterator(); 
            while(iter.hasNext()) {
                Variant v = iter.next();
                v.setCharacterSet(CharacterSet.UTF_8);
            }
        }
        
        return variants; 
    }
    
    protected EnhDirectory getEnhDirectory() {
        return (EnhDirectory) getDirectory();
    }

    /**
     * Accept a POST used to edit or create a file.
     * 
     * @see org.restlet.resource.ServerResource#post(Representation)
     */
    @Override
    protected Representation post(Representation entity) throws ResourceException {
        // TODO: only allowPost on valid targets
        Form form = new Form(entity);
        String newContents = form.getFirstValue("contents");
        File file = new File(URI.create(getTargetUri()));
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
        return new EmptyRepresentation();
    }

    /*
     * XXX: We override Restlet's default PUT behaviour (see FileClientHelper.handleFilePut) as it unhelpfully changes
     * the file extension based on the content-type and there's no apparent way to disable that.
     */
    @Override
    public Representation put(Representation entity) throws ResourceException {
        File file = new File(URI.create(getTargetUri()));
        if (getTargetUri().endsWith("/") || file.isDirectory()) {
            return super.put(entity);
        }
        boolean created = !file.exists();
        try (FileOutputStream out = new FileOutputStream(file)) {
            entity.write(out);
        } catch (FileNotFoundException e) {
            throw new ResourceException(Status.CLIENT_ERROR_NOT_FOUND, e);
        } catch (IOException e) {
            throw new ResourceException(Status.SERVER_ERROR_INTERNAL, e);
        }
        if (created) {
            getResponse().setStatus(Status.SUCCESS_CREATED);
        }
        return new EmptyRepresentation();
    }
}
