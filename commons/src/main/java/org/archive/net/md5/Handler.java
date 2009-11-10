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
package org.archive.net.md5;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;

/**
 * A protocol handler for an 'md5' URI scheme.
 * Md5 URLs look like this: <code>md5:deadbeefdeadbeefdeadbeefdeadbeef</code>
 * When this handler is invoked against an md5 URL, it passes the raw md5 to 
 * the configured script as an argument.  The configured script then does the
 * work to bring the item pointed to by the md5 local so we can open a Stream
 * on the local copy.  Local file is deleted when we finish. Do
 * {@link org.archive.net.DownloadURLConnection#getFile()} to get name of
 * temporary file.
 * 
 * <p>You need to define the system property
 * <code>-Djava.protocol.handler.pkgs=org.archive.net</code> to add this handler
 * to the java.net.URL set. Also define system properties
 * <code>-Dorg.archive.net.md5.Md5URLConnection.path=PATH_TO_SCRIPT</code> to
 * pass path of script to run as well as
 * <code>-Dorg.archive.net.md5.Md5URLConnection.options=OPTIONS</code> for
 * any options you'd like to include.  The pointed-to PATH_TO_SCRIPT
 * will be invoked as follows: <code>PATH_TO_SCRIPT OPTIONS MD5
 * LOCAL_TMP_FILE</code>.  The LOCAL_TMP_FILE file is made in
 * <code>java.io.tmpdir</code> using java tmp name code.
 * @author stack
 */
public class Handler extends URLStreamHandler {
    protected URLConnection openConnection(URL u) {
        return new Md5URLConnection(u);
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
                "org.archive.net.md5.Handler " +
                "md5:deadbeefdeadbeefdeadbeefdeadbeef");
            System.exit(1);
        }
        System.setProperty("org.archive.net.md5.Md5URLConnection.path",
            "/tmp/manifest");
        System.setProperty("java.protocol.handler.pkgs", "org.archive.net");
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
