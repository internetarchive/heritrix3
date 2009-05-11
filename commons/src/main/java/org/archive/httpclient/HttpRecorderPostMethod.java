/* HttpRecorderPostMethod
 *
 * Created on Feb 24, 2004
 *
 * Copyright (C) 2003 Internet Archive.
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

import org.apache.commons.httpclient.HttpConnection;
import org.apache.commons.httpclient.HttpException;
import org.apache.commons.httpclient.HttpState;
import org.apache.commons.httpclient.methods.PostMethod;
import org.archive.util.Recorder;


/**
 * Override of PostMethod that marks the passed HttpRecorder w/ the transition
 * from HTTP head to body and that forces a close on the responseConnection.
 *
 * This is a copy of {@link HttpRecorderGetMethod}.  Only difference is the
 * parent subclass.
 *
 * @author stack
 * @version $Date$ $Revision$
 */
public class HttpRecorderPostMethod extends PostMethod {
    /**
     * Instance of http recorder method.
     */
    protected HttpRecorderMethod httpRecorderMethod = null;
    

    public HttpRecorderPostMethod(String uri, Recorder recorder) {
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
