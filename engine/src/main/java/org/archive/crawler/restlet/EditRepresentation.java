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

    protected String getStaticRef(String resource) {
        String rootRef = dirResource.getRequest().getRootRef().toString();
        return rootRef + "/engine/static/" + resource;
    }

    @Override
    public void write(Writer writer) throws IOException {
        PrintWriter pw = new PrintWriter(writer); 
        pw.println("<!DOCTYPE html>");
        pw.println("<html>");
        pw.println("<head><title>"+fileRepresentation.getFile().getName()+"</title>");
        pw.println("<link rel='stylesheet' href='" + getStaticRef("codemirror/codemirror.css") + "'>");
        pw.println("<link rel='stylesheet' href='" + getStaticRef("codemirror/util/dialog.css") + "'>");
        pw.println("<script src='" + getStaticRef("codemirror/codemirror.js") + "'></script>");
        pw.println("<script src='" + getStaticRef("codemirror/mode/xmlpure.js") + "'></script>");
        pw.println("<script src='" + getStaticRef("codemirror/util/dialog.js") + "'></script>");
        pw.println("<style>.CodeMirror { background: #fff; }</style>");
        pw.println("</head>");
        pw.println("<body style='background-color:#ddd'>");
        pw.println("<form style='position:absolute;top:15px;bottom:15px;left:15px;right:15px;overflow:auto' method='POST'>");
        pw.println("<textarea style='width:98%;height:90%;font-family:monospace' name='contents' id='editor'>");
        StringEscapeUtils.escapeHtml(pw,fileRepresentation.getText()); 
        pw.println("</textarea>");
        pw.println("<div id='savebar'>");
        pw.println("<input type='submit' value='save changes' id='savebutton'>");
        pw.println(fileRepresentation.getFile());
        Reference viewRef = dirResource.getRequest().getOriginalRef().clone(); 
        viewRef.setQuery(null);
        pw.println("<a href='"+viewRef+"'>view</a>");
        Flash.renderFlashesHTML(pw, dirResource.getRequest());
        pw.println("</div>");
        pw.println("</form>");
        pw.println("<script>");
        pw.println("var editor = document.getElementById('editor');");
        pw.println("var savebar = document.getElementById('savebar');");
        pw.println("var savebutton = document.getElementById('savebutton');");
        pw.println("var cmopts = {");
        pw.println("    mode: {name: 'xmlpure'},");
        pw.println("    indentUnit: 1, lineNumbers: true, autofocus: true,");
        pw.println("    onChange: function() { savebutton.disabled = false; },");
        pw.println("}");
        pw.println("var cm = CodeMirror.fromTextArea(editor, cmopts);");
        pw.println("window.onresize = function() {");
        pw.println("    cm.getScrollerElement().style.height = innerHeight - savebar.offsetHeight - 30 + 'px';");
        pw.println("    cm.refresh();");
        pw.println("}");
        pw.println("window.onresize();");
        pw.println("savebutton.disabled = true;");
        pw.println("</script>");
        pw.println("</body>");
        pw.println("</html>");
        pw.close();
    }

    public FileRepresentation getFileRepresentation() {
        return fileRepresentation;
    }
}
