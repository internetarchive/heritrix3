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
package org.archive.util.ms;


import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.archive.io.Endian;
import org.archive.io.RandomAccessInputStream;
import org.archive.io.SeekInputStream;
import org.archive.io.SeekReader;


/**
 * Reads .doc files.
 * 
 * @author pjack
 */
public class Doc {
    
    
    final private static Logger LOGGER = Logger.getLogger(Doc.class.getName());
    

    /**
     * Static utility library, do not instantiate.
     */
    private Doc() {
    }


    /**
     * Returns the text of the .doc file with the given file name.
     * 
     * @param docFilename   the name of the file whose text to return
     * @return  the text of that file
     * @throws IOException  if an IO error occurs
     */
    public static SeekReader getText(String docFilename) throws IOException {
        return getText(new File(docFilename));
    }


    /**
     * Returns the text of the given .doc file.
     * 
     * @param doc   the .doc file whose text to return
     * @return   the text of that file
     * @throws IOException   if an IO error occurs
     */
    public static SeekReader getText(File doc) throws IOException {
        RandomAccessFile raf = new RandomAccessFile(doc, "r");
        RandomAccessInputStream rais = new RandomAccessInputStream(raf);
        return getText(rais);
    }

    
    /**
     * Returns the text of the given .doc file.
     * 
     * @param doc   the .doc file whose text to return
     * @return   the text of that file
     * @throws IOException   if an IO error occurs
     */
    public static SeekReader getText(SeekInputStream doc) throws IOException {
        BlockFileSystem bfs = new DefaultBlockFileSystem(doc, 16);
        return getText(bfs, 20);
    }

    
    /**
     * Returns the text for the given .doc file.  The given cacheSize refers
     * to the number of the .doc file's piece table entries to cache.  Most
     * .doc files only have 1 piece table entry; however, a "fast-saved"
     * .doc file might have several.  A cacheSize of 20 should be ample for
     * most .doc files in the world.  Since piece table entries are small --
     * only 12 bytes each -- caching them prevents many otherwise necessary
     * file pointer repositionings.
     * 
     * @param wordDoc   the .doc file as a BlockFileSystem
     * @param cacheSize  the number of piece table entries to cache
     * @return   a reader that will return the text in the file
     * @throws IOException   if an IO error occurs
     */
    public static SeekReader getText(BlockFileSystem wordDoc, int cacheSize) 
    throws IOException {
        List<Entry> entries = wordDoc.getRoot().list();
        Entry main = find(entries, "WordDocument");
        SeekInputStream mainStream = main.open();
        
        mainStream.position(10);
        int flags = Endian.littleChar(mainStream);
        boolean complex = (flags & 0x0004) == 0x0004;
        boolean tableOne = (flags & 0x0200) == 0x0200;
        String tableName = tableOne ? "1Table" : "0Table";
        Entry table = find(entries, tableName);
        if (LOGGER.isLoggable(Level.FINEST)) {
            LOGGER.finest("Main entry:  " + main);
            LOGGER.finest("Table entry: " + table);
        }
        SeekInputStream tableStream = table.open();
        
        mainStream.position(24);
        int fcMin = Endian.littleInt(mainStream);
        int fcMax = Endian.littleInt(mainStream);
        
        mainStream.position(76);
        int cppText = Endian.littleInt(mainStream);
        
        mainStream.position(418);
        int fcClx = Endian.littleInt(mainStream);
        int fcSz = Endian.littleInt(mainStream);
        
        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.fine("fcMin: " + fcMin);
            LOGGER.fine("fcMax: " + fcMax);
            LOGGER.fine("FcClx: " + fcClx);
            LOGGER.fine("szClx: " + fcSz);
            LOGGER.fine("complex: " + complex);
            LOGGER.fine("cppText: " + cppText);
        }
        PieceTable pt = new PieceTable(tableStream, fcClx, fcMax - fcMin, cacheSize);
        return new PieceReader(pt, mainStream);
    }


    private static Entry find(List<Entry> entries, String name) {
        for (Entry e: entries) {
            if (e.getName().equals(name)) {
                return e;
            }
        }
        return null;
    }

}
