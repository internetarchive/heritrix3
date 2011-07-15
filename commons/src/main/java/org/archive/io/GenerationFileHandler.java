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
package org.archive.io;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.FileHandler;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;

import org.archive.util.FileUtils;


/**
 * FileHandler with support for rotating the current file to
 * an archival name with a specified integer suffix, and
 * provision of a new replacement FileHandler with the current
 * filename.
 *
 * @author gojomo
 */
public class GenerationFileHandler extends FileHandler {
    private LinkedList<String> filenameSeries = new LinkedList<String>();
    private boolean shouldManifest = false;

    /**
     * @return Returns the filenameSeries.
     */
    public List<String> getFilenameSeries() {
        return filenameSeries;
    }

    /**
     * Constructor.
     * @param pattern
     * @param append
     * @param shouldManifest
     * @throws IOException
     * @throws SecurityException
     */
    public GenerationFileHandler(String pattern, boolean append,
            boolean shouldManifest)
    throws IOException, SecurityException {
        super(pattern, append);
        filenameSeries.addFirst(pattern);
        this.shouldManifest = shouldManifest;
    }

    /**
     * @param filenameSeries
     * @param shouldManifest
     * @throws IOException
     */
    public GenerationFileHandler(LinkedList<String> filenameSeries,
            boolean shouldManifest)
    throws IOException {
        super((String)filenameSeries.getFirst(), false); // Never append in this case
        this.filenameSeries = filenameSeries;
        this.shouldManifest = shouldManifest;
    }

    /**
     * Move the current file to a new filename with the storeSuffix in place
     * of the activeSuffix; continuing logging to a new file under the
     * original filename.
     *
     * @param storeSuffix Suffix to put in place of <code>activeSuffix</code>
     * @param activeSuffix Suffix to replace with <code>storeSuffix</code>.
     * @return GenerationFileHandler instance.
     * @throws IOException
     */
    public GenerationFileHandler rotate(String storeSuffix,
            String activeSuffix)
    throws IOException {
        close();
        String filename = (String)filenameSeries.getFirst();
        if (!filename.endsWith(activeSuffix)) {
            throw new FileNotFoundException("Active file does not have" +
                " expected suffix");
        }
        String storeFilename = filename.substring(0,
             filename.length() - activeSuffix.length()) +
             storeSuffix;
        File activeFile = new File(filename);
        File storeFile = new File(storeFilename);
        FileUtils.moveAsideIfExists(storeFile);
        if (!activeFile.renameTo(storeFile)) {
            throw new IOException("Unable to move " + filename + " to " +
                storeFilename);
        }
        filenameSeries.add(1, storeFilename);
        GenerationFileHandler newGfh = 
            new GenerationFileHandler(filenameSeries, shouldManifest);
        newGfh.setFormatter(this.getFormatter());
        return newGfh;
    }
    
    /**
     * @return True if should manifest.
     */
    public boolean shouldManifest() {
        return this.shouldManifest;
    }

    /**
     * Constructor-helper that rather than clobbering any existing 
     * file, moves it aside with a timestamp suffix. 
     * 
     * @param filename
     * @param append
     * @param shouldManifest
     * @return
     * @throws SecurityException
     * @throws IOException
     */
    public static GenerationFileHandler makeNew(String filename, boolean append, boolean shouldManifest) throws SecurityException, IOException {
        FileUtils.moveAsideIfExists(new File(filename));
        return new GenerationFileHandler(filename, append, shouldManifest);
    }

    @Override
    public void publish(LogRecord record) {
        // when possible preformat outside synchronized superclass method
        // (our most involved UriProcessingFormatter can cache result)
        Formatter f = getFormatter(); 
        if(!(f instanceof Preformatter)) {
            super.publish(record);
        } else {
            try {
                ((Preformatter)f).preformat(record); 
                super.publish(record);
            } finally {
                ((Preformatter)f).clear();
            }
        }
    }
//
//    TODO: determine if there's another way to have this optimization without
//    negative impact on log-following (esp. in web UI)
//    /**
//     * Flush only 1/100th of the usual once-per-record, to reduce the time
//     * spent holding the synchronization lock. (Flush is primarily called in
//     * a superclass's synchronized publish()). 
//     * 
//     * The eventual close calls a direct flush on the target writer, so all 
//     * rotates/ends will ultimately be fully flushed. 
//     * 
//     * @see java.util.logging.StreamHandler#flush()
//     */
//    @Override
//    public synchronized void flush() {
//        flushCount++;
//        if(flushCount==100) {
//            super.flush();
//            flushCount=0; 
//        }
//    }
//    int flushCount;     
    
}