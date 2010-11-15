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

import it.unimi.dsi.fastutil.io.FastBufferedOutputStream;
import it.unimi.dsi.mg4j.util.MutableString;

import java.io.Closeable;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.GZIPOutputStream;

import org.apache.commons.lang.StringUtils;
import org.archive.checkpointing.Checkpoint;
import org.archive.util.ArchiveUtils;
import org.archive.util.FileUtils;

/**
 * Utility class for a crawler journal/log that is compressed and 
 * rotates by serial number at checkpoints. 
 * 
 * @author gojomo
 */
public class CrawlerJournal implements Closeable {
    private static final Logger LOGGER = Logger.getLogger(
            CrawlerJournal.class.getName());
    
    /** prefix for error lines*/
    public static final String LOG_ERROR = "E ";
    /** prefix for timestamp lines */
    public static final String LOG_TIMESTAMP = "T ";

    /**
     * Stream on which we record frontier events.
     */
    protected Writer out = null;
    
    /** line count */ 
    protected long lines = 0;
    /** number of lines between timestamps */ 
    protected int timestamp_interval = 0; // 0 means no timestamps
    
    /**
     * File we're writing journal to.
     * Keep a reference in case we want to rotate it off.
     */
    protected File gzipFile = null;
    
    /**
     * Create a new crawler journal at the given location
     * 
     * @param path Directory to make thejournal in.
     * @param filename Name to use for journal file.
     * @throws IOException
     */
    public CrawlerJournal(String path, String filename)
    throws IOException {
        this.gzipFile = new File(path, filename);
        this.out = initialize(gzipFile);
    }
    
    /**
     * Create a new crawler journal at the given location
     * 
     * @param file path at which to make journal
     * @throws IOException
     */
    public CrawlerJournal(File file) throws IOException {
        this.gzipFile = file;
        this.out = initialize(gzipFile);
    }
    
    protected Writer initialize(final File f) throws FileNotFoundException, IOException {
        FileUtils.moveAsideIfExists(f);
        return new OutputStreamWriter(new GZIPOutputStream(
            new FastBufferedOutputStream(new FileOutputStream(f),32*1024)));
    }

    /**
     * Write a line
     * 
     * @param string String
     */
    public synchronized void writeLine(String... strs) {
        try {
            for(String s : strs) {
                this.out.write(s);
            }
            this.out.write("\n");
            noteLine();
        } catch (IOException e) {
            LOGGER.log(
                Level.SEVERE,
                "problem writing journal line: "+StringUtils.join(strs), 
                e);
        }
    }

    /**
     * Write a line. 
     * 
     * @param mstring MutableString to write
     */
    public synchronized void writeLine(MutableString mstring) {
        if (this.out == null) {
            return;
        }
        try {
            mstring.write(out);
            this.out.write("\n");
            noteLine();
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE,"problem writing journal line: "+mstring, e);
        }
    }

    /**
     * Count and note a line
     * 
     * @throws IOException
     */
    protected void noteLine() throws IOException {
        lines++;
        considerTimestamp();
    }

    /**
     * Write a timestamp line if appropriate
     * 
     * @throws IOException
     */
    protected void considerTimestamp() throws IOException {
        if(timestamp_interval > 0 && lines % timestamp_interval == 0) {
            out.write(LOG_TIMESTAMP);
            out.write(ArchiveUtils.getLog14Date());
            out.write("\n");
        }
    }

    /**
     * Flush and close the underlying IO objects.
     */
    public void close() {
        if (this.out == null) {
            return;
        }
        try {
            this.out.flush();
            this.out.close();
            this.out = null;
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE,"problem closing journal", e);
        }
    }

    /**
     * Note a serious error vioa a special log line
     * 
     * @param err
     */
    public synchronized void seriousError(String err) {
        writeLine(LOG_ERROR+ArchiveUtils.getLog14Date()+" "+err+"\n");
    }

    /**
     * Handle a checkpoint by rotating the current log to a checkpoint-named
     * file and starting a new log. 
     * 
     * @param checkpointDir
     * @throws IOException
     */
    public synchronized void rotateForCheckpoint(Checkpoint checkpointInProgress) {
        if (this.out == null || !this.gzipFile.exists()) {
            return;
        }
        close();
        // Rename gzipFile with the checkpoint name as suffix.
        File newName = new File(this.gzipFile.getParentFile(),
                this.gzipFile.getName() + "." + checkpointInProgress.getName());
        try {
            FileUtils.moveAsideIfExists(newName); 
            this.gzipFile.renameTo(newName);
            // Open new gzip file.
            this.out = initialize(this.gzipFile);
        } catch (IOException ioe) {
            LOGGER.log(Level.SEVERE,"Problem rotating recovery journal", ioe);
        }
    }
}
