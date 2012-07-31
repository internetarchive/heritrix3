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

package org.archive.modules.writer;

import static org.archive.modules.recrawl.RecrawlAttributeConstants.A_FETCH_HISTORY;
import static org.archive.modules.recrawl.RecrawlAttributeConstants.A_WRITE_TAG;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringEscapeUtils;
import org.apache.commons.lang.StringUtils;
import org.archive.io.ArchiveFileConstants;
import org.archive.io.ReplayInputStream;
import org.archive.io.WriterPoolMember;
import org.archive.io.arc.ARCWriter;
import org.archive.io.arc.ARCWriterPool;
import org.archive.modules.CrawlURI;
import org.archive.modules.ProcessResult;
import org.archive.spring.ConfigPath;
import org.archive.util.ArchiveUtils;

/**
 * Processor module for writing the results of successful fetches (and
 * perhaps someday, certain kinds of network failures) to the Internet Archive
 * ARC file format.
 *
 * Assumption is that there is only one of these ARCWriterProcessors per
 * Heritrix instance.
 *
 * @author Parker Thompson
 */
public class ARCWriterProcessor extends WriterPoolProcessor {

    final static private String METADATA_TEMPLATE = readMetadataTemplate();
    
    @SuppressWarnings("unused")
    private static final long serialVersionUID = 3L;

    private static final Logger logger = 
        Logger.getLogger(ARCWriterProcessor.class.getName());

    public long getDefaultMaxFileSize() {
        return 100000000L; // 100 SI mega-bytes (10^8 bytes)
    }
    public List<ConfigPath> getDefaultStorePaths() {
        List<ConfigPath> paths = new ArrayList<ConfigPath>();
        paths.add(new ConfigPath("arcs default store path", "arcs"));
        return paths;
    }

    private transient List<String> cachedMetadata;

    public ARCWriterProcessor() {
    }

    @Override
    protected void setupPool(AtomicInteger serialNo) {
        setPool(new ARCWriterPool(serialNo, this, getPoolMaxActive(), getMaxWaitForIdleMs()));
    }

    /**
     * Writes a CrawlURI and its associated data to store file.
     *
     * Currently this method understands the following uri types: dns, http, 
     * and https.
     *
     * @param curi CrawlURI to process.
     */
    protected ProcessResult innerProcessResult(CrawlURI puri) {
        CrawlURI curi = (CrawlURI)puri;
        
        long recordLength = getRecordedSize(curi);
        
        ReplayInputStream ris = null;
        try {
            if (shouldWrite(curi)) {
                ris = curi.getRecorder().getRecordedInput()
                        .getReplayInputStream();
                return write(curi, recordLength, ris, getHostAddress(curi));
            } else {
                logger.info("does not write " + curi.toString());
                copyForwardWriteTagIfDupe(curi);
            }
         } catch (IOException e) {
            curi.getNonFatalFailures().add(e);
            logger.log(Level.SEVERE, "Failed write of Record: " +
                curi.toString(), e);
        } finally {
            IOUtils.closeQuietly(ris);
        }
        return ProcessResult.PROCEED;
    }
    
    protected ProcessResult write(CrawlURI curi, long recordLength, 
            InputStream in, String ip)
    throws IOException {
        WriterPoolMember writer = getPool().borrowFile();
        long position = writer.getPosition();
        // See if we need to open a new file because we've exceeded maxBytes.
        // Call to checkFileSize will open new file if we're at maximum for
        // current file.
        writer.checkSize();
        if (writer.getPosition() != position) {
            // We just closed the file because it was larger than maxBytes.
            // Add to the totalBytesWritten the size of the first record
            // in the file, if any.
            setTotalBytesWritten(getTotalBytesWritten() +
            	(writer.getPosition() - position));
            position = writer.getPosition();
        }
        
        ARCWriter w = (ARCWriter)writer;
        try {
            if (in instanceof ReplayInputStream) {
                w.write(curi.toString(), curi.getContentType(),
                    ip, curi.getFetchBeginTime(),
                    recordLength, (ReplayInputStream)in);
            } else {
                w.write(curi.toString(), curi.getContentType(),
                    ip, curi.getFetchBeginTime(),
                    recordLength, in);
            }
        } catch (IOException e) {
            // Invalidate this file (It gets a '.invalid' suffix).
            getPool().invalidateFile(writer);
            // Set the writer to null otherwise the pool accounting
            // of how many active writers gets skewed if we subsequently
            // do a returnWriter call on this object in the finally block.
            writer = null;
            throw e;
        } finally {
            if (writer != null) {
            	setTotalBytesWritten(getTotalBytesWritten() +
            	     (writer.getPosition() - position));
                getPool().returnFile(writer);
                
                String filename = writer.getFile().getName();
                if (filename.endsWith(ArchiveFileConstants.OCCUPIED_SUFFIX)) {
                    filename = filename.substring(0, filename.length() - ArchiveFileConstants.OCCUPIED_SUFFIX.length());
                }
                curi.addExtraInfo("arcFilename", filename);
                
                @SuppressWarnings("unchecked")
                Map<String,Object>[] history = (Map<String,Object>[])curi.getData().get(A_FETCH_HISTORY);
                if (history != null && history[0] != null) {
                    history[0].put(A_WRITE_TAG, filename);
                }
            }
        }
        return checkBytesWritten();
    }

    public List<String> getMetadata() {
        if (METADATA_TEMPLATE == null) {
            return null;
        }
        
        if (cachedMetadata != null) {
            return cachedMetadata;
        }
                
        String meta = METADATA_TEMPLATE;
        meta = replace(meta, "${VERSION}", ArchiveUtils.VERSION);
        meta = replace(meta, "${HOST}", getHostName());
        meta = replace(meta, "${IP}", getHostAddress());
        
        if (meta != null) {
            meta = replace(meta, "${JOB_NAME}", getMetadataProvider().getJobName());
            meta = replace(meta, "${DESCRIPTION}", getMetadataProvider().getDescription());
            meta = replace(meta, "${OPERATOR}", getMetadataProvider().getOperator());
            // TODO: fix this to match job-start-date (from UI or operator setting)
            // in the meantime, don't include a slightly-off date
            // meta = replace(meta, "${DATE}", GMT());
            meta = replace(meta, "${USER_AGENT}", getMetadataProvider().getUserAgent());
            meta = replace(meta, "${FROM}", getMetadataProvider().getOperatorFrom());
            meta = replace(meta, "${ROBOTS}", getMetadataProvider().getRobotsPolicyName());
        }

        this.cachedMetadata = Collections.singletonList(meta);
        return this.cachedMetadata;
        // ${VERSION}
        // ${HOST}
        // ${IP}
        // ${JOB_NAME}
        // ${DESCRIPTION}
        // ${OPERATOR}
        // ${DATE}
        // ${USER_AGENT}
        // ${FROM}
        // ${ROBOTS}

    }

    private static String replace(String meta, String find, String replace) {
        replace = StringUtils.defaultString(replace);
        replace = StringEscapeUtils.escapeXml(replace);
        return meta.replace(find, replace);
    }
    
    private static String getHostName() {
        try {
            return InetAddress.getLocalHost().getCanonicalHostName();
        } catch (UnknownHostException e) {
            logger.log(Level.SEVERE, "Could not get local host name.", e);
            return "localhost";
        }
    }
    
    private static String getHostAddress() {
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            logger.log(Level.SEVERE, "Could not get local host address.", e);
            return "localhost";
        }        
    }

    private static String readMetadataTemplate() {
        InputStream input = ARCWriterProcessor.class.getResourceAsStream(
                "arc_metadata_template.xml");
        if (input == null) {
            logger.severe("No metadata template.");
            return null;
        }
        try {
            return IOUtils.toString(input);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        } finally {
            IOUtils.closeQuietly(input);
        }
    }
}
