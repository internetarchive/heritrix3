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

package org.archive.io.warc;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Iterator;

import org.archive.io.ArchiveReader;
import org.archive.io.ArchiveReaderFactory;
import org.archive.io.ArchiveRecord;
import org.archive.io.GZIPMembersInputStream;
import org.archive.io.warc.WARCConstants;
import org.archive.util.ArchiveUtils;
import org.archive.util.FileUtils;

import com.google.common.io.CountingInputStream;

/**
 * Factory for WARC Readers.
 * Figures whether to give out a compressed file Reader or an uncompressed
 * Reader.
 * @author stack
 * @version $Date: 2006-08-23 17:59:04 -0700 (Wed, 23 Aug 2006) $ $Version$
 */
public class WARCReaderFactory extends ArchiveReaderFactory
implements WARCConstants {
    private static final WARCReaderFactory factory = new WARCReaderFactory();

    /**
     * Shutdown any access to default constructor.
     * This factory is Singleton.
     */
    private WARCReaderFactory() {
        super();
    }
    
    public static WARCReader get(String arcFileOrUrl)
    throws MalformedURLException, IOException {
    	return (WARCReader)WARCReaderFactory.factory.
    		getArchiveReader(arcFileOrUrl);
    }
    
    public static WARCReader get(final File f) throws IOException {
    	return (WARCReader)WARCReaderFactory.factory.getArchiveReader(f);
    }
    
    /**
     * @param f An arcfile to read.
     * @param offset Have returned Reader set to start reading at this offset.
     * @return A WARCReader.
     * @throws IOException 
     */
    public static WARCReader get(final File f, final long offset)
    throws IOException {
    	return (WARCReader)WARCReaderFactory.factory.
    		getArchiveReader(f, offset);
    }
    
    protected ArchiveReader getArchiveReader(final File f, final long offset)
    throws IOException {
		boolean compressed = testCompressedWARCFile(f);
		if (!compressed) {
			if (!FileUtils.isReadableWithExtensionAndMagic(f,
					DOT_WARC_FILE_EXTENSION, WARC_MAGIC)) {
				throw new IOException(f.getAbsolutePath()
						+ " is not a WARC file.");
			}
		}
		return (WARCReader)(compressed?
			WARCReaderFactory.factory.new CompressedWARCReader(f, offset):
			WARCReaderFactory.factory.new UncompressedWARCReader(f, offset));
	}
    
    public static ArchiveReader get(final String s, final InputStream is,
            final boolean atFirstRecord)
    throws IOException {
        return WARCReaderFactory.factory.getArchiveReader(s, is,
            atFirstRecord);
    }
    
    protected ArchiveReader getArchiveReader(final String f,
			final InputStream is, final boolean atFirstRecord)
			throws IOException {
		// For now, assume stream is compressed. Later add test of input
		// stream or handle exception thrown when figure not compressed stream.
		return new CompressedWARCReader(f, is, atFirstRecord);
	}
    
    public static WARCReader get(final URL arcUrl, final long offset)
    throws IOException {
        return (WARCReader)WARCReaderFactory.factory.getArchiveReader(arcUrl,
            offset);
    }
    
    /**
     * Get an ARCReader.
     * Pulls the ARC local into whereever the System Property
     * <code>java.io.tmpdir</code> points. It then hands back an ARCReader that
     * points at this local copy.  A close on this ARCReader instance will
     * remove the local copy.
     * @param arcUrl An URL that points at an ARC.
     * @return An ARCReader.
     * @throws IOException 
     */
    public static WARCReader get(final URL arcUrl)
    throws IOException {
        return (WARCReader)WARCReaderFactory.factory.getArchiveReader(arcUrl);
    }
    
    /**
     * Check file is compressed WARC.
     *
     * @param f File to test.
     *
     * @return True if this is compressed WARC (TODO: Just tests if file is
     * GZIP'd file (It begins w/ GZIP MAGIC)).
     *
     * @exception IOException If file does not exist or is not unreadable.
     */
    public static boolean testCompressedWARCFile(final File f)
    throws IOException {
        FileUtils.assertReadable(f);
        boolean compressed = false;
        final InputStream is = new FileInputStream(f);
        try {
            compressed = ArchiveUtils.isGzipped(is);
        } finally {
            is.close();
        }
        return compressed;
    }

    /**
     * Uncompressed WARC file reader.
     * @author stack
     */
    public class UncompressedWARCReader extends WARCReader {
        /**
         * Constructor.
         * @param f Uncompressed arcfile to read.
         * @throws IOException
         */
        public UncompressedWARCReader(final File f)
        throws IOException {
            this(f, 0);
        }

        /**
         * Constructor.
         * 
         * @param f Uncompressed file to read.
         * @param offset Offset at which to position Reader.
         * @throws IOException
         */
        public UncompressedWARCReader(final File f, final long offset)
        throws IOException {
            // File has been tested for existence by time it has come to here.
            setIn(new CountingInputStream(getInputStream(f, offset)));
            getIn().skip(offset);
            initialize(f.getAbsolutePath());
        }
        
        /**
         * Constructor.
         * 
         * @param f Uncompressed file to read.
         * @param is InputStream.
         */
        public UncompressedWARCReader(final String f, final InputStream is) {
            // Arc file has been tested for existence by time it has come
            // to here.
            setIn(new CountingInputStream(is));
            initialize(f);
        }
    }
    
    /**
     * Compressed WARC file reader.
     * 
     * @author stack
     */
    public class CompressedWARCReader extends WARCReader {
        /**
         * Constructor.
         * 
         * @param f Compressed file to read.
         * @throws IOException
         */
        public CompressedWARCReader(final File f) throws IOException {
            this(f, 0);
        }

        /**
         * Constructor.
         * 
         * @param f Compressed arcfile to read.
         * @param offset Position at where to start reading file.
         * @throws IOException
         */
        public CompressedWARCReader(final File f, final long offset)
                throws IOException {
            // File has been tested for existence by time it has come to here.
            setIn(new GZIPMembersInputStream(getInputStream(f, offset)));
            ((GZIPMembersInputStream)getIn()).compressedSeek(offset); 
            setCompressed((offset == 0)); // TODO: does this make sense?!?!
            initialize(f.getAbsolutePath());
        }
        
        /**
         * Constructor.
         * 
         * @param f Compressed arcfile.
         * @param is InputStream to use.
         * @param atFirstRecord
         * @throws IOException
         */
        public CompressedWARCReader(final String f, final InputStream is,
            final boolean atFirstRecord)
        throws IOException {
            // Arc file has been tested for existence by time it has come
            // to here.
            setIn(new GZIPMembersInputStream(is));
            setCompressed(true);
            initialize(f);
            // TODO: Ignore atFirstRecord. Probably doesn't apply in WARC world.
        }
        
        /**
         * Get record at passed <code>offset</code>.
         * 
         * @param offset Byte index into file at which a record starts.
         * @return A WARCRecord reference.
         * @throws IOException
         */
        public WARCRecord get(long offset) throws IOException {
            cleanupCurrentRecord();
            ((GZIPMembersInputStream)getIn()).compressedSeek(offset);
            return (WARCRecord) createArchiveRecord(getIn(), offset);
        }
        
        public Iterator<ArchiveRecord> iterator() {
            /**
             * Override ArchiveRecordIterator so can base returned iterator on
             * GzippedInputStream iterator.
             */
            return new ArchiveRecordIterator() {
                private GZIPMembersInputStream gis =
                    (GZIPMembersInputStream)getIn();

                private Iterator<GZIPMembersInputStream> gzipIterator = this.gis.memberIterator();

                protected boolean innerHasNext() {
                    return this.gzipIterator.hasNext();
                }

                protected ArchiveRecord innerNext() throws IOException {
                    // Get the position before gzipIterator.next moves
                    // it on past the gzip header.
                    InputStream is = (InputStream) this.gzipIterator.next();
                    return createArchiveRecord(is, Math.max(gis.getCurrentMemberStart(), gis.getCurrentMemberEnd()));
                }
            };
        }
        
        protected void gotoEOR(ArchiveRecord rec) throws IOException {
            long skipped = 0; 
            while (getIn().read()>-1) {
                skipped++;
            }
            if(skipped>4) {
                System.err.println("unexpected extra data after record "+rec);
            }
            return;
        }
    }
    
    public static boolean isWARCSuffix(final String f) {
    	return (f == null)?
    		false:
    		(f.toLowerCase().endsWith(DOT_COMPRESSED_WARC_FILE_EXTENSION))?
    		    true:
    			(f.toLowerCase().endsWith(DOT_WARC_FILE_EXTENSION))?
    			true: false;
    }
}