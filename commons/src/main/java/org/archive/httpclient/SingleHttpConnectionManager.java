/* SingleHttpConnectionManager
*
* $Id$
*
* Created on Mar 8, 2004
*
* Copyright (C) 2004 Internet Archive.
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
package org.archive.httpclient;

import java.io.IOException;
import java.io.InputStream;

import org.apache.commons.httpclient.HostConfiguration;
import org.apache.commons.httpclient.HttpConnection;
import org.apache.commons.httpclient.SimpleHttpConnectionManager;

/**
 * An HttpClient-compatible HttpConnection "manager" that actually
 * just gives out a new connection each time -- skipping the overhead
 * of connection management, since we already throttle our crawler
 * with external mechanisms.
 *
 * @author gojomo
 */
public class SingleHttpConnectionManager extends SimpleHttpConnectionManager {

    public SingleHttpConnectionManager() {
        super();
    }

    public HttpConnection getConnectionWithTimeout(
        HostConfiguration hostConfiguration, long timeout) {

        HttpConnection conn = new HttpConnection(hostConfiguration);
        conn.setHttpConnectionManager(this);
        conn.getParams().setDefaults(this.getParams());
        return conn;
    }

    public void releaseConnection(HttpConnection conn) {
        // ensure connection is closed
        conn.close();
        finishLast(conn);
    }

    static void finishLast(HttpConnection conn) {
        // copied from superclass because it wasn't made available to subclasses
        InputStream lastResponse = conn.getLastResponseInputStream();
        if (lastResponse != null) {
            conn.setLastResponseInputStream(null);
            try {
                lastResponse.close();
            } catch (IOException ioe) {
                //FIXME: badness - close to force reconnect.
                conn.close();
            }
        }
    }
}
