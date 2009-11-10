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

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;

/**
 * A protocol handler that uses native rsync client to do copy.
 * You need to define the system property
 * <code>-Djava.protocol.handler.pkgs=org.archive.net</code> to add this handler
 * to the java.net.URL set.  Assumes rsync is in path.  Define
 * system property
 * <code>-Dorg.archive.net.rsync.RsyncUrlConnection.path=PATH_TO_RSYNC</code> to
 * pass path to rsync. Downloads to <code>java.io.tmpdir</code>.
 * @author stack
 */
public class Handler extends URLStreamHandler {
    protected URLConnection openConnection(URL u) {
        return new RsyncURLConnection(u);
    }

    /**
     * Main dumps rsync file to STDOUT.
     * @param args
     * @throws IOException
     */
    public static void main(String[] args)
    throws IOException {
        if (args.length != 1) {
            System.out.println("Usage: java java " +
                "-Djava.protocol.handler.pkgs=org.archive.net " +
                "org.archive.net.rsync.Handler RSYNC_URL");
            System.exit(1);
        }
        URL u = new URL(args[0]);
        URLConnection connect = u.openConnection();
        // Write download to stdout.
        final int bufferlength = 4096;
        byte [] buffer = new byte [bufferlength];
        InputStream is = connect.getInputStream();
        try {
            for (int count = is.read(buffer, 0, bufferlength);
                    (count = is.read(buffer, 0, bufferlength)) != -1;) {
                System.out.write(buffer, 0, count);
            }
            System.out.flush();
        } finally {
            is.close();
        }
    }
}