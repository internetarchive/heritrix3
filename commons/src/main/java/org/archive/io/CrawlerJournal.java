/* CrawlerJournal.java
 *
 * Created on Mar 6, 2007
 *
 * Copyright (C) 2007 Internet Archive.
 *
 * This file is part of the Heritrix web crawler (crawler.archive.org).
 *
 * Heritrix is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser Public License as published by
 * the Free Software Foundation; either version 2.1 of the License, or
 * any later version.
 *
 * Heritrix is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser Public License for more details.
 *
 * You should have received a copy of the GNU Lesser Public License
 * along with Heritrix; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */
package org.archive.io;

import it.unimi.dsi.fastutil.io.FastBufferedOutputStream;
import it.unimi.dsi.mg4j.util.MutableString;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import org.archive.checkpointing.RecoverAction;
import org.archive.util.ArchiveUtils;
import org.archive.util.FileUtils;

/**
 * Utility class for a crawler journal/log that is compressed and 
 * rotates by serial number at checkpoints. 
 * 
 * @author gojomo
 */
public class CrawlerJournal {

    /** prefix for error lines*/
    public static final String LOG_ERROR = "E ";
    /** prefix for timestamp lines */
    public static final String LOG_TIMESTAMP = "T ";
    
    /**
     * Get a BufferedReader on the crawler journal given
     * 
     * TODO: move to a general utils class 
     * 
     * @param source File journal
     * @return journal buffered reader.
     * @throws IOException
     */
    public static BufferedReader getBufferedReader(File source) throws IOException {
        InputStream is = new BufferedInputStream(new FileInputStream(source));
        boolean isGzipped = source.getName().toLowerCase().
            endsWith(GZIP_SUFFIX);
        if(isGzipped) {
            is = new GZIPInputStream(is);
        }
        return new BufferedReader(new InputStreamReader(is));
    }

    /**
     * Stream on which we record frontier events.
     */
    protected Writer out = null;
    
    /** line count */ 
    protected long lines = 0;
    /** number of lines between timestamps */ 
    protected int timestamp_interval = 0; // 0 means no timestamps

    
    /** suffix to recognize gzipped files */
    public static final String GZIP_SUFFIX = ".gz";
    
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
    
    /**
     * Allocate a buffer for accumulating lines to write and reuse it.
     */
    protected MutableString accumulatingBuffer = new MutableString(1024);

    protected Writer initialize(final File f) throws FileNotFoundException, IOException {
        FileUtils.moveAsideIfExists(f);
        return new OutputStreamWriter(new GZIPOutputStream(
            new FastBufferedOutputStream(new FileOutputStream(f))));
    }

    /**
     * Write a line
     * 
     * @param string String
     */
    public synchronized void writeLine(String string) {
        try {
            this.out.write(string);
            this.out.write("\n");
            noteLine();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Write a line of two strings
     * 
     * @param s1 String
     * @param s2 String
     */
    public synchronized void writeLine(String s1, String s2) {
        try {
            this.out.write(s1);
            this.out.write(s2);
            this.out.write("\n");
            noteLine();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    /**
     * Write a line of three strings
     * 
     * @param s1 String
     * @param s2 String
     * @param s3 String
     */
    public synchronized void writeLine(String s1, String s2, String s3) {
        try {
            this.out.write(s1);
            this.out.write(s2);
            this.out.write(s3);
            this.out.write("\n");
            noteLine();
        } catch (IOException e) {
            e.printStackTrace();
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
            e.printStackTrace();
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
            e.printStackTrace();
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
    public synchronized void checkpoint(final File checkpointDir, 
            List<RecoverAction> actions) throws IOException {
        if (this.out == null || !this.gzipFile.exists()) {
            return;
        }
        close();
        // Rename gzipFile with the checkpoint name as suffix.
        this.gzipFile.renameTo(new File(this.gzipFile.getParentFile(),
                this.gzipFile.getName() + "." + checkpointDir.getName()));
        // Open new gzip file.
        this.out = initialize(this.gzipFile);
    }

}
