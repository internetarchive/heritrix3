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

package org.archive.io.arc;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Iterator;
import java.util.logging.Level;

import org.archive.io.ArchiveReader;
import org.archive.io.ArchiveReaderFactory;
import org.archive.io.ArchiveRecord;
import org.archive.io.ArchiveRecordHeader;
import org.archive.io.GZIPMembersInputStream;
import org.archive.io.GzipHeader;
import org.archive.io.NoGzipMagicException;
import org.archive.util.FileUtils;

import com.google.common.io.CountingInputStream;


/**
 * Factory that returns an ARCReader.
 * 
 * Can handle compressed and uncompressed ARCs.
 *
 * @author stack
 */
public class ARCReaderFactory extends ArchiveReaderFactory
implements ARCConstants {
    /**
     * This factory instance.
     */
    private static final ARCReaderFactory factory = new ARCReaderFactory();

    /**
     * Shutdown any access to default constructor.
     */
    protected ARCReaderFactory() {
        super();
    }
    
    public static ARCReader get(String arcFileOrUrl)
    throws MalformedURLException, IOException {
    	return (ARCReader)ARCReaderFactory.factory.
    		getArchiveReader(arcFileOrUrl);
    }
    
    public static ARCReader get(String arcFileOrUrl, final long offset)
    throws MalformedURLException, IOException {
    	return (ARCReader)ARCReaderFactory.factory.
    		getArchiveReader(arcFileOrUrl, offset);
    }
    
    public static ARCReader get(final File f) throws IOException {
    	return (ARCReader)ARCReaderFactory.factory.getArchiveReader(f);
    }
    
    public static ARCReader get(final File f, final long offset)
    throws IOException {
    	return (ARCReader)ARCReaderFactory.factory.getArchiveReader(f, offset);
    }
    
    protected ArchiveReader getArchiveReader(final File f, final long offset)
    throws IOException {
    	return getArchiveReader(f, true, offset);
	}
    
    /**
     * @param f An arcfile to read.
     * @param skipSuffixTest Set to true if want to test that ARC has proper
     * suffix. Use this method and pass <code>false</code> to open ARCs
     * with the <code>.open</code> or otherwise suffix.
     * @param offset Have returned ARCReader set to start reading at passed
     * offset.
     * @return An ARCReader.
     * @throws IOException 
     */
    public static ARCReader get(final File f,
            final boolean skipSuffixTest, final long offset)
    throws IOException {
    	return (ARCReader)ARCReaderFactory.factory.getArchiveReader(f,
    		skipSuffixTest, offset);
    }
    
    protected ArchiveReader getArchiveReader(final File arcFile,
            final boolean skipSuffixTest, final long offset)
    throws IOException {
        boolean compressed = testCompressedARCFile(arcFile, skipSuffixTest);
        if (!compressed) {
            if (!FileUtils.isReadableWithExtensionAndMagic(arcFile,
                    ARC_FILE_EXTENSION, ARC_MAGIC_NUMBER)) {
                throw new IOException(arcFile.getAbsolutePath() +
                    " is not an Internet Archive ARC file.");
            }
        }
        return compressed?
            (ARCReader)ARCReaderFactory.factory.
                new CompressedARCReader(arcFile, offset):
            (ARCReader)ARCReaderFactory.factory.
                new UncompressedARCReader(arcFile, offset);
	}
    
    public static ArchiveReader get(final String s, final InputStream is,
            final boolean atFirstRecord)
    throws IOException {
        return ARCReaderFactory.factory.getArchiveReader(s, is,
            atFirstRecord);
    }
    
    protected ArchiveReader getArchiveReader(final String arc,
			final InputStream is, final boolean atFirstRecord)
			throws IOException {
		// For now, assume stream is compressed. Later add test of input
		// stream or handle exception thrown when figure not compressed stream.
		return new CompressedARCReader(arc, is, atFirstRecord);
	}
    
    /**
	 * Get an ARCReader aligned at <code>offset</code>. This version of get
	 * will not bring the ARC local but will try to stream across the net making
	 * an HTTP 1.1 Range request on remote http server (RFC1435 Section 14.35).
	 * 
	 * @param arcUrl HTTP URL for an ARC (All ARCs considered remote).
	 * @param offset Offset into ARC at which to start fetching.
	 * @return An ARCReader aligned at offset.
	 * @throws IOException
	 */
    public static ARCReader get(final URL arcUrl, final long offset)
    throws IOException {
        return (ARCReader)ARCReaderFactory.factory.getArchiveReader(arcUrl,
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
    public static ARCReader get(final URL arcUrl)
    throws IOException {
        return (ARCReader)ARCReaderFactory.factory.getArchiveReader(arcUrl);
    }
    
    /**
     * @param arcFile File to test.
     * @return True if <code>arcFile</code> is compressed ARC.
     * @throws IOException
     */
    public boolean isCompressed(File arcFile) throws IOException {
        return testCompressedARCFile(arcFile);
    }
    
    /**
     * Check file is compressed and in ARC GZIP format.
     *
     * @param arcFile File to test if its Internet Archive ARC file
     * GZIP compressed.
     *
     * @return True if this is an Internet Archive GZIP'd ARC file (It begins
     * w/ the Internet Archive GZIP header and has the
     * COMPRESSED_ARC_FILE_EXTENSION suffix).
     *
     * @exception IOException If file does not exist or is not unreadable.
     */
    public static boolean testCompressedARCFile(File arcFile)
    throws IOException {
        return testCompressedARCFile(arcFile, false);
    }

    /**
     * Check file is compressed and in ARC GZIP format.
     *
     * @param arcFile File to test if its Internet Archive ARC file
     * GZIP compressed.
     * @param skipSuffixCheck Set to true if we're not to test on the
     * '.arc.gz' suffix.
     *
     * @return True if this is an Internet Archive GZIP'd ARC file (It begins
     * w/ the Internet Archive GZIP header).
     *
     * @exception IOException If file does not exist or is not unreadable.
     */
    public static boolean testCompressedARCFile(File arcFile,
            boolean skipSuffixCheck)
    throws IOException {
        boolean compressedARCFile = false;
        FileUtils.assertReadable(arcFile);
        if(!skipSuffixCheck && !arcFile.getName().toLowerCase()
                .endsWith(COMPRESSED_ARC_FILE_EXTENSION)) {
            return compressedARCFile;
        }
        
        final InputStream is = new FileInputStream(arcFile);
        try {
            compressedARCFile = testCompressedARCStream(is);
        } finally {
            is.close();
        }
        return compressedARCFile;
    }
    
    public static boolean isARCSuffix(final String arcName) {
    	return (arcName == null)?
    		false:
    		(arcName.toLowerCase().endsWith(DOT_COMPRESSED_ARC_FILE_EXTENSION))?
    		    true:
    			(arcName.toLowerCase().endsWith(DOT_ARC_FILE_EXTENSION))?
    			true: false;
    }
    
    /**
     * Tests passed stream is gzip stream by reading in the HEAD.
     * Does not reposition the stream.  That is left up to the caller.
     * @param is An InputStream.
     * @return True if compressed stream.
     * @throws IOException
     */
    public static boolean testCompressedARCStream(final InputStream is)
            throws IOException {
        boolean compressedARCFile = false;
        GzipHeader gh = null;
        try {
            gh = new GzipHeader(is);
        } catch (NoGzipMagicException e ) {
            return compressedARCFile;
        }
        
        byte[] fextra = gh.getFextra();
        // Now make sure following bytes are IA GZIP comment.
        // First check length. ARC_GZIP_EXTRA_FIELD includes length
        // so subtract two and start compare to ARC_GZIP_EXTRA_FIELD
        // at +2.
        // some Alexa ARC files gzip extra fields have changed slightly 
        // after the first two bytes, so we'll just look for the 'LX' 
        // extension for valid IA ARC files.
        if (fextra != null) {
        	if (fextra.length >= ARC_GZIP_EXTRA_FIELD.length - 2) {
        		if (fextra[0] == ARC_GZIP_EXTRA_FIELD[2] && 
        				fextra[1] == ARC_GZIP_EXTRA_FIELD[3]) {
        			compressedARCFile = true;
        		}
        	}
        }
        return compressedARCFile;
    }

    /**
     * Uncompressed arc file reader.
     * @author stack
     */
    public class UncompressedARCReader extends ARCReader {
        /**
         * Constructor.
         * @param f Uncompressed arcfile to read.
         * @throws IOException
         */
        public UncompressedARCReader(final File f)
        throws IOException {
            this(f, 0);
        }

        /**
         * Constructor.
         * 
         * @param f Uncompressed arcfile to read.
         * @param offset Offset at which to position ARCReader.
         * @throws IOException
         */
        public UncompressedARCReader(final File f, final long offset)
        throws IOException {
            // Arc file has been tested for existence by time it has come
            // to here.
            setIn(new CountingInputStream(getInputStream(f, offset)));
            getIn().skip(offset); 
            initialize(f.getAbsolutePath());
        }
        
        /**
         * Constructor.
         * 
         * @param f Uncompressed arc to read.
         * @param is InputStream.
         */
        public UncompressedARCReader(final String f, final InputStream is) {
            // Arc file has been tested for existence by time it has come
            // to here.
            setIn(is);
            initialize(f);
        }
    }
    
    /**
     * Compressed arc file reader.
     * 
     * @author stack
     */
    public class CompressedARCReader extends ARCReader {

        /**
         * Constructor.
         * 
         * @param f
         *            Compressed arcfile to read.
         * @throws IOException
         */
        public CompressedARCReader(final File f) throws IOException {
            this(f, 0);
        }

        /**
         * Constructor.
         * 
         * @param f Compressed arcfile to read.
         * @param offset Position at where to start reading file.
         * @throws IOException
         */
        public CompressedARCReader(final File f, final long offset)
                throws IOException {
            // Arc file has been tested for existence by time it has come
            // to here.
            setIn(new GZIPMembersInputStream(getInputStream(f, offset)));
            ((GZIPMembersInputStream)getIn()).compressedSeek(offset); 
            setCompressed((offset == 0)); // TODO: does this make sense???
            initialize(f.getAbsolutePath());
        }
        
        /**
         * Constructor.
         * 
         * @param f Compressed arcfile.
         * @param is InputStream to use.
         * @throws IOException
         */
        public CompressedARCReader(final String f, final InputStream is,
            final boolean atFirstRecord)
        throws IOException {
            // Arc file has been tested for existence by time it has come
            // to here.
            setIn(new GZIPMembersInputStream(is));
            setCompressed(true);
            setAlignedOnFirstRecord(atFirstRecord);
            initialize(f);
        }
        
        /**
         * Get record at passed <code>offset</code>.
         * 
         * @param offset
         *            Byte index into arcfile at which a record starts.
         * @return An ARCRecord reference.
         * @throws IOException
         */
        public ARCRecord get(long offset) throws IOException {
            cleanupCurrentRecord();
            ((GZIPMembersInputStream)getIn()).compressedSeek(offset);
            return createArchiveRecord(getIn(), offset);
        }
        
        public Iterator<ArchiveRecord> iterator() {
            /**
             * Override ARCRecordIterator so can base returned iterator on
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
                    InputStream is = this.gzipIterator.next();
                    return createArchiveRecord(is, Math.max(gis.getCurrentMemberStart(), gis.getCurrentMemberEnd()));
                }
            };
        }
        
        protected void gotoEOR(ArchiveRecord rec) throws IOException {
            int c;
            while ((c = getIn().read())==LINE_SEPARATOR);
            if(c==-1) {
                return; 
            }
            long skipped = 1; 
            while (getIn().read()>-1) {
                skipped++;
            }
            // Report on system error the number of unexpected characters
            // at the end of this record.
            ArchiveRecordHeader meta = (getCurrentRecord() != null)?
                rec.getHeader(): null;
            String message = "Record STARTING at " +
                ((GZIPMembersInputStream)getIn()).getCurrentMemberStart() +
                " has " + skipped + " trailing byte(s): " +
                ((meta != null)? meta.toString(): "");
            if (isStrict()) {
                throw new IOException(message);
            }
            logStdErr(Level.WARNING, message);
        }
    }
}