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
