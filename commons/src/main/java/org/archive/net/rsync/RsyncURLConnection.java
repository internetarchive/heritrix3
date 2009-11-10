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
package org.archive.net.rsync;

import java.io.File;
import java.net.URL;

import org.archive.net.DownloadURLConnection;

/**
 * Rsync URL connection.
 * @author stack
 * @version $Date$, $Revision$
 */
public class RsyncURLConnection extends DownloadURLConnection {
    private final String RSYNC_TIMEOUT =
    	System.getProperty(RsyncURLConnection.class.getName() + ".timeout",
    		"300");

    protected RsyncURLConnection(URL u) {
        super(u);
    }
    
    protected String getScript() {
    	return System.getProperty(this.getClass().getName() + ".path",
    		"rsync");
    }
    
    @Override
    protected String[] getCommand(final URL thisUrl,
    		final File downloadFile) {
    	return new String[] {getScript(), "--timeout=" + RSYNC_TIMEOUT,
    		this.url.getPath(), downloadFile.getAbsolutePath()};  
    }
}
