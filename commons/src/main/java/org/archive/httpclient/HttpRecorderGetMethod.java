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
import java.util.logging.Logger;

import org.apache.commons.httpclient.HttpConnection;
import org.apache.commons.httpclient.HttpException;
import org.apache.commons.httpclient.HttpState;
import org.apache.commons.httpclient.methods.GetMethod;
import org.archive.util.Recorder;


/**
 * Override of GetMethod that marks the passed HttpRecorder w/ the transition
 * from HTTP head to body and that forces a close on the http connection.
 *
 * The actions done in this subclass used to be done by copying
 * org.apache.commons.HttpMethodBase, overlaying our version in place of the
 * one that came w/ httpclient.  Here is the patch of the difference between
 * shipped httpclient code and our mods:
 * <pre>
 *    -- -1338,6 +1346,12 --
 *
 *        public void releaseConnection() {
 *
 *   +        // HERITRIX always ants the streams closed.
 *   +        if (responseConnection != null)
 *   +        {
 *   +            responseConnection.close();
 *   +        }
 *   +
 *            if (responseStream != null) {
 *                try {
 *                    // FYI - this may indirectly invoke responseBodyConsumed.
 *   -- -1959,6 +1973,11 --
 *                        this.statusLine = null;
 *                    }
 *                }
 *   +            // HERITRIX mark transition from header to content.
 *   +            if (this.httpRecorder != null)
 *   +            {
 *   +                this.httpRecorder.markContentBegin();
 *   +            }
 *                readResponseBody(state, conn);
 *                processResponseBody(state, conn);
 *            } catch (IOException e) {
 * </pre>
 * 
 * <p>We're not supposed to have access to the underlying connection object;
 * am only violating contract because see cases where httpclient is skipping
 * out w/o cleaning up after itself.
 *
 * @author stack
 * @version $Revision$, $Date$
 */
public class HttpRecorderGetMethod extends GetMethod {
    
    protected static Logger logger =
        Logger.getLogger(HttpRecorderGetMethod.class.getName());
    
    /**
     * Instance of http recorder method.
     */
    protected HttpRecorderMethod httpRecorderMethod = null;
    

	public HttpRecorderGetMethod(String uri, Recorder recorder) {
		super(uri);
        this.httpRecorderMethod = new HttpRecorderMethod(recorder);
	}

	protected void readResponseBody(HttpState state, HttpConnection connection)
	throws IOException, HttpException {
        // We're about to read the body.  Mark transition in http recorder.
		this.httpRecorderMethod.markContentBegin(connection);
		super.readResponseBody(state, connection);
	}

    protected boolean shouldCloseConnection(HttpConnection conn) {
        // Always close connection after each request. As best I can tell, this
        // is superfluous -- we've set our client to be HTTP/1.0.  Doing this
        // out of paranoia.
        return true;
    }

    public int execute(HttpState state, HttpConnection conn)
    throws HttpException, IOException {
        // Save off the connection so we can close it on our way out in case
        // httpclient fails to (We're not supposed to have access to the
        // underlying connection object; am only violating contract because
        // see cases where httpclient is skipping out w/o cleaning up
        // after itself).
        this.httpRecorderMethod.setConnection(conn);
        return super.execute(state, conn);
    }
    
    protected void addProxyConnectionHeader(HttpState state, HttpConnection conn)
            throws IOException, HttpException {
        super.addProxyConnectionHeader(state, conn);
        this.httpRecorderMethod.handleAddProxyConnectionHeader(this);
    }
}
