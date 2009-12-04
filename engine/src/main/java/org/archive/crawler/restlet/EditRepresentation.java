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

import java.io.IOException;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;

import org.apache.commons.lang.StringEscapeUtils;
import org.restlet.data.CharacterSet;
import org.restlet.data.MediaType;
import org.restlet.data.Reference;
import org.restlet.resource.CharacterRepresentation;
import org.restlet.resource.FileRepresentation;

/**
 * Representation wrapping a FileRepresentation, displaying its contents
 * in a TextArea for editting. 
 * 
 * @contributor gojomo
 */
public class EditRepresentation extends CharacterRepresentation {
    FileRepresentation fileRepresentation; 
    EnhDirectoryResource dirResource;
    
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
        PrintWriter pw = new PrintWriter(writer); 
        pw.println("<head><title>"+fileRepresentation.getFile().getName()+"</title></head>");

        Flash.renderFlashesHTML(pw, dirResource.getRequest());
        pw.println("<form method='POST'>");
        // TODO: enable button on after changes made
        pw.println("<input type='submit' value='save changes'/>");
        pw.println(fileRepresentation.getFile());
        Reference viewRef = dirResource.getRequest().getOriginalRef().clone(); 
        viewRef.setQuery(null);
        pw.println("<a href='"+viewRef+"'>view</a>");
        pw.println("<br/>");
        pw.println("<textarea style='width:100%;height:94%;' name='contents'>");
        StringEscapeUtils.escapeHtml(pw,fileRepresentation.getText()); 
        pw.println("</textarea></form>");
        pw.close();
    }

    public FileRepresentation getFileRepresentation() {
        return fileRepresentation;
    }
}
