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
package org.archive.modules.extractor;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.interactive.action.PDAction;
import org.apache.pdfbox.pdmodel.interactive.action.PDActionURI;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotation;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink;

import java.io.*;
import java.util.*;


/** Supports PDF parsing operations.  For now this primarily means
 *  extracting URIs, but the logic in extractURIs() could easily be adopted/extended
 * for a variety of PDF processing tasks.
 *
 * @author Parker Thompson
 *
 */
public class PDFParser implements Closeable {

    protected ArrayList<String> foundURIs;
    protected PDDocument documentReader;
    protected byte[] document;

    public PDFParser(String doc) throws IOException {
        resetState();
        getInFromFile(doc);
        initialize();
    }
    public PDFParser(byte[] doc) throws IOException{
        resetState();
        document = doc;
        initialize();
    }

    /** Reinitialize the object as though a new one were created.
     */
    protected void resetState(){
        foundURIs = new ArrayList<String>();
        documentReader = null;
        document = null;
    }

    /**
     * Reset the object and initialize it with a new byte array (the document).
     * @param doc
     * @throws IOException
     */
    public void resetState(byte[] doc) throws IOException{
        resetState();
        document = doc;
        initialize();
    }

    /** Reinitialize the object as though a new one were created, complete
     * with a valid pointer to a document that can be read
     * @param doc
     * @throws IOException
     */
    public void resetState(String doc) throws IOException{
        resetState();
        getInFromFile(doc);
        initialize();
    }

    /**
     * Read a file named 'doc' and store its' bytes for later processing.
     * @param doc
     * @throws IOException
     */
    protected void getInFromFile(String doc) throws IOException{
        File documentOnDisk = new File(doc);
        documentReader = Loader.loadPDF(documentOnDisk);
    }

    /**
     * Get a list of URIs retrieved from the Pdf during the
     * extractURIs operation.
     * @return A list of URIs retrieved from the Pdf during the
     * extractURIs operation.
     */
    public ArrayList<String> getURIs(){
        return foundURIs;
    }

    /**
     * Initialize opens the document for reading.  This is done implicitly
     * by the constuctor.  This should only need to be called directly following
     * a reset.
     * @throws IOException
     */
    protected void initialize() throws IOException{
        if(document != null){
            documentReader = Loader.loadPDF(document);
        }
    }

    /**
     * Extract URIs from all objects found in a Pdf document's catalog.
     * Returns an array list representing all URIs found in the document catalog tree.
     * @return URIs from all objects found in a Pdf document's catalog.
     */
    public ArrayList<String> extractURIs() throws IOException {
        for (PDPage page : documentReader.getPages()) {
            for (PDAnnotation annotation : page.getAnnotations()) {
                if (annotation instanceof PDAnnotationLink) {
                    PDAnnotationLink link = (PDAnnotationLink) annotation;
                    PDAction action = link.getAction();
                    if (action instanceof PDActionURI) {
                        PDActionURI uri = (PDActionURI) action;
                        foundURIs.add(uri.getURI());
                    }
                }
            }
        }
        return getURIs();
    }

    @Override
    public void close() throws IOException {
        if (documentReader != null) {
            documentReader.close();
        }
    }

    public static void main(String[] argv){
        try {
            PDFParser parser = new PDFParser("/tmp/pdfspec.pdf");
            ArrayList<String> uris = parser.extractURIs();
            Iterator<String> i = uris.iterator();
            while(i.hasNext()){
                String uri = (String)i.next();
                System.out.println("got uri: " + uri);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
