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
package org.archive.io;

import it.unimi.dsi.fastutil.io.RepositionableStream;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;

import org.archive.io.arc.ARCReaderFactory;
import org.archive.io.warc.WARCReaderFactory;
import org.archive.net.UURI;
import org.archive.net.md5.Md5URLConnection;
import org.archive.net.rsync.RsyncURLConnection;
import org.archive.util.FileUtils;


/**
 * Factory that returns an Archive file Reader.
 * Returns Readers for ARCs or WARCs.
 * @author stack
 * @version $Date$ $Revision$
 */
public class ArchiveReaderFactory implements ArchiveFileConstants {
  // Static block to enable S3 URLs
  static {
    if (System.getProperty("java.protocol.handler.pkgs") != null) {
      System.setProperty("java.protocol.handler.pkgs",
        System.getProperty("java.protocol.handler.pkgs")
        + "|" + "org.archive.net");
    } else {
      System.setProperty("java.protocol.handler.pkgs", "org.archive.net");
    }
  }
  
	/**
	 * Offset value for when we want to stream all.
	 */
	private final static int STREAM_ALL = -1;

	private static final ArchiveReaderFactory factory =
		new ArchiveReaderFactory();
	
    /**
     * Shutdown any public access to default constructor.
     */
    protected ArchiveReaderFactory() {
        super();
    }
    
    /**
     * Get an Archive file Reader on passed path or url.
     * Does primitive heuristic figuring if path or URL.
     * @param arcFileOrUrl File path or URL pointing at an Archive file.
     * @return An Archive file Reader.
     * @throws IOException 
     * @throws MalformedURLException 
     * @throws IOException 
     */
    public static ArchiveReader get(final String arcFileOrUrl)
    throws MalformedURLException, IOException {
    	return ArchiveReaderFactory.factory.getArchiveReader(arcFileOrUrl);
    }
    
    protected ArchiveReader getArchiveReader(final String arcFileOrUrl)
    throws MalformedURLException, IOException {
    	return getArchiveReader(arcFileOrUrl, STREAM_ALL);
    }
    
    protected ArchiveReader getArchiveReader(final String arcFileOrUrl,
    	final long offset)
    throws MalformedURLException, IOException {
    	return UURI.hasScheme(arcFileOrUrl)?
    		get(new URL(arcFileOrUrl), offset):
    			get(new File(arcFileOrUrl), offset);
    }
    
    /**
     * @param f An Archive file to read.
     * @return An ArchiveReader
     * @throws IOException 
     */
    public static ArchiveReader get(final File f) throws IOException {
    	return ArchiveReaderFactory.factory.getArchiveReader(f);
    }
    
    protected ArchiveReader getArchiveReader(final File f)
    throws IOException {
    	return getArchiveReader(f, 0);
    }
    
    /**
     * @param f An Archive file to read.
     * @param offset Have returned Reader set to start reading at this offset.
     * @return An ArchiveReader
     * @throws IOException 
     */
    public static ArchiveReader get(final File f, final long offset)
    throws IOException {
    	return ArchiveReaderFactory.factory.getArchiveReader(f, offset);
	}
    
    protected ArchiveReader getArchiveReader(final File f,
    	final long offset)
    throws IOException {
    	if (ARCReaderFactory.isARCSuffix(f.getName())) {
    		return ARCReaderFactory.get(f, true, offset);
    	} else if (WARCReaderFactory.isWARCSuffix(f.getName())) {
    		return WARCReaderFactory.get(f, offset);
    	}
    	throw new IOException("Unknown file extension (Not ARC nor WARC): "
    		+ f.getName());
    }
    
    /**
     * Wrap a Reader around passed Stream.
     * @param s Identifying String for this Stream used in error messages.
     * Must be a string that ends with the name of the file we're to put
     * an ArchiveReader on.  This code looks at file endings to figure
     * whether to return an ARC or WARC reader.
     * @param is Stream.  Stream will be wrapped with implementation of
     * RepositionableStream unless already supported.
     * @param atFirstRecord Are we at first Record?
     * @return ArchiveReader.
     * @throws IOException
     */
    public static ArchiveReader get(final String s, final InputStream is,
        final boolean atFirstRecord)
    throws IOException {
        return ArchiveReaderFactory.factory.getArchiveReader(s, is,
        	atFirstRecord);
    }
    
    /**
     * @param is
     * @return If passed <code>is</code> is
     * {@link RepositionableInputStream}, returns <code>is</code>, else we
     * wrap <code>is</code> with {@link RepositionableStream}.
     */
    protected InputStream asRepositionable(final InputStream is) {
        if (is instanceof RepositionableStream) {
            return is;
        }
        // RepositionableInputStream calls mark on each read so can back up at
        // least the read amount.  Needed for gzip inflater overinflations
        // reading into the next gzip member.
        return new RepositionableInputStream(is, 16 * 1024);
    }
    
    protected ArchiveReader getArchiveReader(final String id, 
    		final InputStream is, final boolean atFirstRecord)
    throws IOException {
    	final InputStream stream = asRepositionable(is);
        if (ARCReaderFactory.isARCSuffix(id)) {
            return ARCReaderFactory.get(id, stream, atFirstRecord);
        } else if (WARCReaderFactory.isWARCSuffix(id)) {
            return WARCReaderFactory.get(id, stream, atFirstRecord);
        }
        throw new IOException("Unknown extension (Not ARC nor WARC): " + id);
    }
    
    /**
     * Get an Archive Reader aligned at <code>offset</code>.
     * This version of get will not bring the file local but will try to
     * stream across the net making an HTTP 1.1 Range request on remote
     * http server (RFC1435 Section 14.35).
     * @param u HTTP URL for an Archive file.
     * @param offset Offset into file at which to start fetching.
     * @return An ArchiveReader aligned at offset.
     * @throws IOException
     */
    public static ArchiveReader get(final URL u, final long offset)
    throws IOException {
    	return ArchiveReaderFactory.factory.getArchiveReader(u, offset);
    }
    
    protected ArchiveReader getArchiveReader(final URL f, final long offset)
    throws IOException {
        // Get URL connection.
        URLConnection connection = f.openConnection();
        if (connection instanceof HttpURLConnection) {
          addUserAgent((HttpURLConnection)connection);
        }
        if (offset != STREAM_ALL) {
        	// Use a Range request (Assumes HTTP 1.1 on other end). If
        	// length >= 0, add open-ended range header to the request.  Else,
        	// because end-byte is inclusive, subtract 1.
        	connection.addRequestProperty("Range", "bytes=" + offset + "-");
        }
        
        return getArchiveReader(f.toString(), connection.getInputStream(),
            (offset == 0));
    }
    
    /**
     * Get an ARCReader.
     * Pulls the ARC local into whereever the System Property
     * <code>java.io.tmpdir</code> points. It then hands back an ARCReader that
     * points at this local copy.  A close on this ARCReader instance will
     * remove the local copy.
     * @param u An URL that points at an ARC.
     * @return An ARCReader.
     * @throws IOException 
     */
    public static ArchiveReader get(final URL u)
    throws IOException {
    	return ArchiveReaderFactory.factory.getArchiveReader(u);
    }
    
    protected ArchiveReader getArchiveReader(final URL u)
    throws IOException {
        // If url represents a local file then return file it points to.
        if (u.getPath() != null) {
            // TODO: Add scheme check and host check.
            File f = new File(u.getPath());
            if (f.exists()) {
                return get(f, 0);
            }
        }
       
        String scheme = u.getProtocol();
        if (scheme.startsWith("http") || scheme.equals("s3")) {
            // Try streaming if http or s3 URLs rather than copying local
        	// and then reading (Passing an offset will get us an Reader
        	// that wraps a Stream).
            return get(u, STREAM_ALL);
        }
        
        return makeARCLocal(u.openConnection());
    }
    
    protected ArchiveReader makeARCLocal(final URLConnection connection)
    throws IOException {
        File localFile = null;
        if (connection instanceof HttpURLConnection) {
            // If http url connection, bring down the resource local.
            String p = connection.getURL().getPath();
            int index = p.lastIndexOf('/');
            if (index >= 0) {
                // Name file for the file we're making local.
                localFile = File.createTempFile("",p.substring(index + 1));
                if (localFile.exists()) {
                    // If file of same name already exists in TMPDIR, then
                    // clean it up (Assuming only reason a file of same name in
                    // TMPDIR is because we failed a previous download).
                    localFile.delete();
                }
            } else {
                localFile = File.createTempFile(ArchiveReader.class.getName(),
                    ".tmp");
            }
            addUserAgent((HttpURLConnection)connection);
            connection.connect();
            try {
                FileUtils.readFullyToFile(connection.getInputStream(), localFile);
            } catch (IOException ioe) {
                localFile.delete();
                throw ioe;
            }
        } else if (connection instanceof RsyncURLConnection) {
            // Then, connect and this will create a local file.
            // See implementation of the rsync handler.
            connection.connect();
            localFile = ((RsyncURLConnection)connection).getFile();
        } else if (connection instanceof Md5URLConnection) {
            // Then, connect and this will create a local file.
            // See implementation of the md5 handler.
            connection.connect();
            localFile = ((Md5URLConnection)connection).getFile();
        } else {
            throw new UnsupportedOperationException("No support for " +
                connection);
        }
        
        ArchiveReader reader = null;
        try {
            reader = get(localFile, 0);
        } catch (IOException e) {
            localFile.delete();
            throw e;
        }
        
        // Return a delegate that does cleanup of downloaded file on close.
        return reader.getDeleteFileOnCloseReader(localFile);
    }
    
    protected void addUserAgent(final HttpURLConnection connection) {
        connection.addRequestProperty("User-Agent", this.getClass().getName());
    }
    
    /**
     * @param f File to test.
     * @return True if <code>f</code> is compressed.
     * @throws IOException
     */
    protected boolean isCompressed(final File f) throws IOException {
        return f.getName().toLowerCase().
        	endsWith(DOT_COMPRESSED_FILE_EXTENSION);
    }
}