/* $Id$
 *
 * Created August 11th, 2006
 *
 * Copyright (C) 2006 Internet Archive.
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
