/* RsyncURLConnection.java
 *
 * $Id$
 *
 * Created Jul 19, 2005
 *
 * Copyright (C) 2005 Internet Archive.
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
