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

import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.PdfName;
import com.lowagie.text.pdf.PdfObject;
import com.lowagie.text.pdf.PdfDictionary;
import com.lowagie.text.pdf.PRIndirectReference;
import com.lowagie.text.pdf.PdfArray;

import java.io.*;
import java.util.*;


/** Supports PDF parsing operations.  For now this primarily means
 *  extracting URIs, but the logic in extractURIs() could easily be adopted/extended
 * for a variety of PDF processing tasks.
 *
 * @author Parker Thompson
 *
 */
//TODO make this more effecient, it currently had to read the whole file into memory
// before processing can begin, and appears to take much longer than it "should"
// to parse small, but admittedly complex, documents.
public class PDFParser {

    protected ArrayList<String> foundURIs;
    protected ArrayList<ArrayList<Integer>> encounteredReferences;
    protected PdfReader documentReader;
    protected byte[] document;
    protected PdfDictionary catalog;

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
        encounteredReferences = new ArrayList<ArrayList<Integer>>();
        documentReader = null;
        document = null;
        catalog = null;

        for(int i=0; i < encounteredReferences.size(); i++){
            encounteredReferences.add(new ArrayList<Integer>());
        }
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

        long length = documentOnDisk.length();
        document = new byte[(int)length];

        FileInputStream inStream = new FileInputStream(documentOnDisk);

        inStream.read(document);
    }

    /**
     * Indicates, based on a PDFObject's generation/id pair whether
     * the parser has already encountered this object (or a reference to it)
     * so we don't infinitely loop on circuits within the PDF.
     * @param generation
     * @param id
     * @return True if already seen.
     */
    protected boolean haveSeen(int generation, int id){

        // if we can't store this generation grow our list until we can
        if(generation >= encounteredReferences.size()){
            for(int i=encounteredReferences.size(); i <= generation; i++){
                encounteredReferences.add(new ArrayList<Integer>());
            }

            // clearly we haven't seen it
            return false;
        }

        ArrayList<Integer> generationList
         = encounteredReferences.get(generation);
        
        for (int i: generationList) {
            if(i == id){
                return true;
            }
        }
        return false;
    }

    /**
     * Note that an object (id/generation pair) has been seen by this parser
     * so that it can be handled differently when it is encountered again.
     * @param generation
     * @param id
     */
    protected void markAsSeen(int generation, int id){
        ArrayList<Integer> objectIds = encounteredReferences.get(generation);
        objectIds.add(id);
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
            documentReader = new PdfReader(document);
        }

        catalog = documentReader.getCatalog();
    }

    /**
     * Extract URIs from all objects found in a Pdf document's catalog.
     * Returns an array list representing all URIs found in the document catalog tree.
     * @return URIs from all objects found in a Pdf document's catalog.
     */
    public ArrayList<String> extractURIs(){
        extractURIs(catalog);
        return getURIs();
    }

    /**
     * Parse a PdfDictionary, looking for URIs recursively and adding
     * them to foundURIs
     * @param entity
     */
    @SuppressWarnings("unchecked")
    protected void extractURIs(PdfObject entity){

            // deal with dictionaries
            if(entity.isDictionary()){

                PdfDictionary dictionary= (PdfDictionary)entity;

                Set<PdfName> allkeys = dictionary.getKeys();
                for (PdfName key: allkeys) {
                    PdfObject value = dictionary.get(key);

                    // see if it's the key is a UR[I,L]
                    if( key.toString().equals("/URI") ||
		            key.toString().equals("/URL") ) {
                        foundURIs.add(value.toString());

                    }else{
                        this.extractURIs(value);
                    }

                }

            // deal with arrays
            }else if(entity.isArray()){

                PdfArray array = (PdfArray)entity;
                for (PdfObject pdfObject : (Iterable<PdfObject>)array.getArrayList()) {
                    this.extractURIs(pdfObject);
                }

            // deal with indirect references
            }else if(entity.getClass() == PRIndirectReference.class){

                    PRIndirectReference indirect = (PRIndirectReference)entity;

                    // if we've already seen a reference to this object
                    if( haveSeen( indirect.getGeneration(), indirect.getNumber()) ){
                        return;

                    // note that we've seen it if it's new
                    }else{
                        markAsSeen(indirect.getGeneration(), indirect.getNumber() );
                    }

                    // dereference the "pointer" and process the object
                    indirect.getReader(); // FIXME: examine side-effects
                    PdfObject direct = PdfReader.getPdfObject(indirect);

                    this.extractURIs(direct);
            }
    }

    public static void main(String[] argv){

        try {
            PDFParser parser = new PDFParser("/home/parkert/files/pdfspec.pdf");
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
