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

import static org.archive.modules.ModuleAttributeConstants.A_DNS_SERVER_IP_LABEL;
import static org.archive.modules.fetcher.FetchStatusCodes.S_DNS_SUCCESS;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import org.archive.checkpointing.Checkpoint;
import org.archive.checkpointing.Checkpointable;
import org.archive.io.WriterPool;
import org.archive.io.WriterPoolMember;
import org.archive.io.WriterPoolSettings;
import org.archive.modules.CrawlMetadata;
import org.archive.modules.CrawlURI;
import org.archive.modules.ProcessResult;
import org.archive.modules.Processor;
import org.archive.modules.deciderules.recrawl.IdenticalDigestDecideRule;
import org.archive.modules.net.CrawlHost;
import org.archive.modules.net.ServerCache;
import org.archive.spring.ConfigPath;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.Lifecycle;

/**
 * Abstract implementation of a file pool processor.
 * Subclass to implement for a particular {@link WriterPoolMember} instance.
 * @author Parker Thompson
 * @author stack
 */
public abstract class WriterPoolProcessor extends Processor 
implements Lifecycle, Checkpointable, WriterPoolSettings {
    private static final long serialVersionUID = 1L;
    @SuppressWarnings("unused")
    private static final Logger logger = 
        Logger.getLogger(WriterPoolProcessor.class.getName());

    /**
     * Whether to gzip-compress files when writing to disk; 
     * by default true, meaning do-compress. 
     */
    boolean compress = true; 
    public boolean getCompress() {
        return compress;
    }
    public void setCompress(boolean compress) {
        this.compress = compress;
    }
    
    /**
     * File prefix. The text supplied here will be used as a prefix naming
     * writer files. For example if the prefix is 'WEB', then file names will
     * look like WEB-20040808101010-0001-PID@HOSTNAME#PORT.arc.gz ...if 
     * writing ARCs (The prefix will be separated from the date by a hyphen).
     */
    String prefix = WriterPoolMember.DEFAULT_PREFIX; 
    public String getPrefix() {
        return prefix;
    }
    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }


    /**
     * Template from which a filename is interpolated. Expressions of the
     * form ${key} will be replaced by values from a local map of useful 
     * values (including 'prefix', 'timestamp17', and 'serialno') or 
     * global system properties (which includes the local hostname/port/pid). 
     *
     * The default pattern will generate unique names under reasonable 
     * assumptions; be sure you know what you're doing before customizing,
     * as you could easily create filename collisions with a poorly-designed
     * filename template.
     * 
     */
    String template = WriterPoolMember.DEFAULT_TEMPLATE; 
    public String getTemplate() {
        return template;
    }
    public void setTemplate(String template) {
        this.template = template;
    }
    
    /**
     * Max size of each file.
     */
    long maxFileSizeBytes = getDefaultMaxFileSize();
    abstract long getDefaultMaxFileSize();
    public long getMaxFileSizeBytes() {
        return maxFileSizeBytes;
    }
    public void setMaxFileSizeBytes(long maxFileSizeBytes) {
        this.maxFileSizeBytes = maxFileSizeBytes;
    }
    
    /**
     * Maximum active files in pool. This setting cannot be varied over the life
     * of a crawl.
     */
    int poolMaxActive = WriterPool.DEFAULT_MAX_ACTIVE;
    public int getPoolMaxActive() {
        return poolMaxActive;
    }
    public void setPoolMaxActive(int poolMaxActive) {
        this.poolMaxActive = poolMaxActive;
    }

    /**
     * Maximum time to wait on pool element (milliseconds). This setting cannot
     * be varied over the life of a crawl.
     */
    int poolMaxWaitMs = WriterPool.DEFAULT_MAXIMUM_WAIT;
    public int getPoolMaxWaitMs() {
        return poolMaxWaitMs;
    }
    public void setPoolMaxWaitMs(int poolMaxWait) {
        this.poolMaxWaitMs = poolMaxWait;
    }
    
    /**
     * Whether to skip the writing of a record when URI history information is
     * available and indicates the prior fetch had an identical content digest.
     * Note that subclass settings may provide more fine-grained control on
     * how identical digest content is handled; for those controls to have
     * effect, this setting must not be 'true' (causing content to be 
     * skipped entirely). 
     * Default is false.
     */
    boolean skipIdenticalDigests = false; 
    public boolean getSkipIdenticalDigests() {
        return skipIdenticalDigests;
    }
    public void setSkipIdenticalDigests(boolean skipIdenticalDigests) {
        this.skipIdenticalDigests = skipIdenticalDigests;
    }

    /**
     * CrawlURI annotation indicating no record was written.
     */
    protected static final String ANNOTATION_UNWRITTEN = "unwritten";

    /**
     * Total file bytes to write to disk. Once the size of all files on disk has
     * exceeded this limit, this processor will stop the crawler. A value of
     * zero means no upper limit.
     */
    long maxTotalBytesToWrite = 0L;
    public long getMaxTotalBytesToWrite() {
        return maxTotalBytesToWrite;
    }
    public void setMaxTotalBytesToWrite(long maxTotalBytesToWrite) {
        this.maxTotalBytesToWrite = maxTotalBytesToWrite;
    }

    public CrawlMetadata getMetadataProvider() {
        return (CrawlMetadata) kp.get("metadataProvider");
    }
    @Autowired
    public void setMetadataProvider(CrawlMetadata provider) {
        kp.put("metadataProvider",provider);
    }

    transient protected ServerCache serverCache;
    public ServerCache getServerCache() {
        return this.serverCache;
    }
    @Autowired
    public void setServerCache(ServerCache serverCache) {
        this.serverCache = serverCache;
    }

    protected ConfigPath directory = new ConfigPath("writer base path", ".");
    public ConfigPath getDirectory() {
        return directory;
    }
    public void setDirectory(ConfigPath directory) {
        this.directory = directory;
    }
    
    /**
     * Where to save files. Supply absolute or relative directory paths. 
     * If relative, paths will be interpreted relative to the local
     * 'directory' property. order.disk-path setting. If more than one
     * path specified, we'll round-robin dropping files to each. This 
     * setting is safe to change midcrawl (You can remove and add new 
     * dirs as the crawler progresses).
     */
    List<String> storePaths = getDefaultStorePaths();
    abstract List<String> getDefaultStorePaths();
    public List<String> getStorePaths() {
        return storePaths;
    }
    public void setStorePaths(List<String> paths) {
        this.storePaths = paths; 
    }
    
    /**
     * Reference to pool.
     */
    transient private WriterPool pool = null;
    
    /**
     * Total number of bytes written to disc.
     */
    private long totalBytesWritten = 0;

    private AtomicInteger serial = new AtomicInteger();
    

    /**
     * @param name Name of this processor.
     * @param description Description for this processor.
     */
    public WriterPoolProcessor() {
        super();
    }


    public synchronized void start() {
        if (isRunning()) {
            return;
        }
        super.start(); 
        setupPool(serial);
    }
    
    public void stop() {
        if (!isRunning()) {
            return;
        }
        super.stop(); 
        this.pool.close();
    }
    
    
    protected AtomicInteger getSerialNo() {
        return ((WriterPool)getPool()).getSerialNo();
    }

    /**
     * Set up pool of files.
     */
    protected abstract void setupPool(final AtomicInteger serial);

    
    protected ProcessResult checkBytesWritten() {
        long max = getMaxTotalBytesToWrite();
        if (max <= 0) {
            return ProcessResult.PROCEED;
        }
        if (max <= this.totalBytesWritten) {
            return ProcessResult.FINISH; // FIXME: Specify reason
//            controller.requestCrawlStop(CrawlStatus.FINISHED_WRITE_LIMIT);
        }
        return ProcessResult.PROCEED;
    }
    
    /**
     * Whether the given CrawlURI should be written to archive files.
     * Annotates CrawlURI with a reason for any negative answer.
     * 
     * @param curi CrawlURI
     * @return true if URI should be written; false otherwise
     */
    protected boolean shouldWrite(CrawlURI curi) {
        if (getSkipIdenticalDigests()
            && IdenticalDigestDecideRule.hasIdenticalDigest(curi)) {
            curi.getAnnotations().add(ANNOTATION_UNWRITTEN 
                    + ":identicalDigest");
            return false;
        }
        
        boolean retVal;
        String scheme = curi.getUURI().getScheme().toLowerCase();
        // TODO: possibly move this sort of isSuccess() test into CrawlURI
        if (scheme.equals("dns")) {
            retVal = curi.getFetchStatus() == S_DNS_SUCCESS;
        } else if (scheme.equals("http") || scheme.equals("https")) {
            retVal = curi.getFetchStatus() > 0 && curi.getHttpMethod() != null;
        } else if (scheme.equals("ftp")) {
            retVal = curi.getFetchStatus() > 0;
        } else {
            curi.getAnnotations().add(ANNOTATION_UNWRITTEN
                    + ":scheme");
            return false;
        }
        
        if (retVal == false) {
            // status not deserving writing
            curi.getAnnotations().add(ANNOTATION_UNWRITTEN + ":status");
            return false;
        }
        
        return true; 
    }
    
    /**
     * Return IP address of given URI suitable for recording (as in a
     * classic ARC 5-field header line).
     * 
     * @param curi CrawlURI
     * @return String of IP address
     */
    protected String getHostAddress(CrawlURI curi) {
        // special handling for DNS URIs: want address of DNS server
        if (curi.getUURI().getScheme().toLowerCase().equals("dns")) {
            return (String)curi.getData().get(A_DNS_SERVER_IP_LABEL);
        }
        // otherwise, host referenced in URI
        // TODO:FIXME: have fetcher insert exact IP contacted into curi,
        // use that rather than inferred by CrawlHost lookup 
        CrawlHost h = getServerCache().getHostFor(curi.getUURI());
        if (h == null) {
            throw new NullPointerException("Crawlhost is null for " +
                curi + " " + curi.getVia());
        }
        InetAddress a = h.getIP();
        if (a == null) {
            throw new NullPointerException("Address is null for " +
                curi + " " + curi.getVia() + ". Address " +
                ((h.getIpFetched() == CrawlHost.IP_NEVER_LOOKED_UP)?
                     "was never looked up.":
                     (System.currentTimeMillis() - h.getIpFetched()) +
                         " ms ago."));
        }
        return h.getIP().getHostAddress();
    }

    // TODO: add non-urgent checkpoint request, that waits for a good
    // moment (when (W)ARCs are already rolling over)? 
    public void doCheckpoint(Checkpoint checkpointInProgress) 
    throws IOException {
        // close all ARCs on checkpoint
        this.pool.close();
        
        super.doCheckpoint(checkpointInProgress);
   
        // reopen post checkpoint
        setupPool(this.serial);
    }
    
    @Override
    protected JSONObject toCheckpointJson() throws JSONException {
        JSONObject json = super.toCheckpointJson();
        json.put("serialNumber", getSerialNo().get());
        return json;
    }
    
    @Override
    protected void fromCheckpointJson(JSONObject json) throws JSONException {
        super.fromCheckpointJson(json);
        serial.set(json.getInt("serialNumber"));
    }
    
    protected WriterPool getPool() {
        return pool;
    }

    protected void setPool(WriterPool pool) {
        this.pool = pool;
    }

    protected long getTotalBytesWritten() {
        return totalBytesWritten;
    }

    protected void setTotalBytesWritten(long totalBytesWritten) {
        this.totalBytesWritten = totalBytesWritten;
    }
	
    public abstract List<String> getMetadata();
    
    public List<File> getOutputDirs() {
        List<String> list = getStorePaths();
        ArrayList<File> results = new ArrayList<File>();
        for (String path: list) {
            File f = new File(
                    path.startsWith("/") ? null : getDirectory().getFile(), 
                    path);
            if (!f.exists()) {
                try {
                    f.mkdirs();
                } catch (Exception e) {
                    e.printStackTrace();
                    continue;
                }
            }
            results.add(f);
        }
        return results;        
    }

    @Override
    protected void innerProcess(CrawlURI puri) {
        throw new AssertionError();
    }

    @Override
    protected abstract ProcessResult innerProcessResult(CrawlURI uri);

    protected boolean shouldProcess(CrawlURI uri) {
        if (!(uri instanceof CrawlURI)) {
            return false;
        }
        
        CrawlURI curi = (CrawlURI)uri;
        // If failure, or we haven't fetched the resource yet, return
        if (curi.getFetchStatus() <= 0) {
            return false;
        }
        
        // If no recorded content at all, don't write record.
        long recordLength = curi.getContentSize();
        if (recordLength <= 0) {
            // getContentSize() should be > 0 if any material (even just
            // HTTP headers with zero-length body is available.
            return false;
        }
        
        return true;
    }
}
