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
package org.archive.net;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.archive.util.ProcessUtils;
import org.archive.util.ProcessUtils.ProcessResult;

/**
 * An URL Connection that pre-downloads URL reference before passing back a
 * Stream reference.  When closed, it removes the local download file.
 * @author stack
 * @version $Date$, $Revision$
 */
public abstract class DownloadURLConnection extends URLConnection {
    private final String CLASSNAME = DownloadURLConnection.class.getName();
    private final Logger LOGGER = Logger.getLogger(CLASSNAME);
    private static final File TMPDIR =
        new File(System.getProperty("java.io.tmpdir", "/tmp"));
    private File downloadFile = null;

    protected DownloadURLConnection(URL u) {
        super(u);
    }
    
    protected String getScript() {
    	return System.getProperty(this.getClass().getName() + ".path",
    		"UNDEFINED");
    }
    
    protected String [] getCommand(final URL thisUrl,
    		final File downloadFile) {
    	return new String[] {getScript(), thisUrl.getPath(),
        	downloadFile.getAbsolutePath()};  
    }

    /**
     * Do script copy to local file.
     * File is available via {@link #getFile()}.
     * @throws IOException 
     */
    public void connect() throws IOException {
        if (this.connected) {
            return;
        }
        
        this.downloadFile = File.createTempFile(CLASSNAME, null, TMPDIR);
        try {
            String [] cmd = getCommand(this.url, this.downloadFile);    
            if (LOGGER.isLoggable(Level.FINE)) {
                StringBuffer buffer = new StringBuffer();
                for (int i = 0; i < cmd.length; i++) {
                    if (i > 0) {
                        buffer.append(" ");
                    }
                    buffer.append(cmd[i]);
                }
                LOGGER.fine("Command: " + buffer.toString());
            }
            ProcessResult pr = ProcessUtils.exec(cmd);
            if (pr.getResult() != 0) {
                LOGGER.info(cmd + " returned non-null " + pr.getResult());
            }
            // Assume download went smoothly.
            this.connected = true;
        } catch (IOException ioe) {
            // Clean up my tmp file.
            this.downloadFile.delete();
            this.downloadFile = null;
            // Rethrow.
            throw ioe;
        }
    }
    
    public File getFile() {
        return this.downloadFile;
    }
    
    protected void setFile(final File f) {
        this.downloadFile = f;
    }

    public InputStream getInputStream() throws IOException {
        if (!this.connected) {
            connect();
        }
        
        // Return BufferedInputStream so 'delegation' is done for me, so
        // I don't have to implement all IS methods and pass to my
        // 'delegate' instance.
        final DownloadURLConnection connection = this;
        return new BufferedInputStream(new FileInputStream(this.downloadFile)) {
            private DownloadURLConnection ruc = connection;

            public void close() throws IOException {
                super.close();
                if (this.ruc != null && this.ruc.getFile()!= null &&
                    this.ruc.getFile().exists()) {
                    this.ruc.getFile().delete();
                    this.ruc.setFile(null);
                }
            }
        };
    }
}