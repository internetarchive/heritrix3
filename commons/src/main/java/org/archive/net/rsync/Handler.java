/* RsyncProtocolHandler.java
 *
 * $Id$
 *
 * Created Jul 15, 2005
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