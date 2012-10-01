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
package org.archive.modules.recrawl;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang.SerializationUtils;
import org.archive.checkpointing.Checkpoint;
import org.archive.checkpointing.Checkpointable;
import org.archive.io.CrawlerJournal;
import org.archive.modules.CrawlURI;
import org.archive.spring.ConfigPath;
import org.springframework.context.Lifecycle;


/**
 * Log CrawlURI attributes from latest fetch for consultation by a later 
 * recrawl. Log must be imported into alternate data structure in order
 * to be consulted. 
 * 
 * @author gojomo
 * @version $Date: 2006-09-25 20:19:54 +0000 (Mon, 25 Sep 2006) $, $Revision: 4654 $
 */
public class PersistLogProcessor extends PersistProcessor 
implements Checkpointable, Lifecycle {

    @SuppressWarnings("unused")
    private static final long serialVersionUID = 1678691994065439346L;
    
    protected CrawlerJournal log;

    //  description: "Filename to which to log URI persistence information. " +
    // "Default is 'logs/persistlog.txtser.gz'. "
    protected ConfigPath logFile = new ConfigPath("URI persistence log file","${launchId}/logs/persistlog.txtser.gz");
    public ConfigPath getLogFile() {
        return this.logFile;
    }
    public void setLogFile(ConfigPath path) {
        this.logFile = path; 
    }
    
//    class description: "PersistLogProcessor. Logs CrawlURI attributes " +
//    "from latest fetch for consultation by a later recrawl."
    
    public PersistLogProcessor() {
    }


    public void start() {
        if (isRunning()) {
            return;
        }
        try {
            File logFile = getLogFile().getFile();
            log = new CrawlerJournal(logFile);
        } catch (IOException e) {
            // TODO Auto-generated catch block
            throw new RuntimeException(e);
        }
    }
    
    public boolean isRunning() {
        return log != null; 
    }

    public void stop() {
        if(!isRunning()) {
            return;
        }
        
        // XXX happens at finish; move to teardown?
        log.close();
        log = null; 
    }

    @Override
    protected void innerProcess(CrawlURI curi) {
        log.writeLine(persistKeyFor(curi), " ", 
                new String(Base64.encodeBase64(
                        SerializationUtils.serialize((Serializable)curi.getPersistentDataMap()))));      
    }
    
    public void startCheckpoint(Checkpoint checkpointInProgress) {}

	public void doCheckpoint(Checkpoint checkpointInProgress) throws IOException {
        // rotate log
        log.rotateForCheckpoint(checkpointInProgress);
    }
    
    public void finishCheckpoint(Checkpoint checkpointInProgress) {}

    @Override
    protected boolean shouldProcess(CrawlURI uri) {
        return shouldStore(uri);
    }
}