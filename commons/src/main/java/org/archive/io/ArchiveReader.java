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


import java.io.BufferedInputStream;
import java.io.BufferedWriter;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.archive.util.MimetypeUtils;

import com.google.common.io.CountingInputStream;


/**
 * Reader for an Archive file of Archive {@link ArchiveRecord}s.
 * @author stack
 * @version $Date$ $Version$
 */
public abstract class ArchiveReader implements ArchiveFileConstants, Iterable<ArchiveRecord> {    
    /**
     * Is this Archive file compressed?
     */
    private boolean compressed = false;
    
    /**
     * Should we digest as we read?
     */
    private boolean digest = true;
    
    /**
     * Should the parse be strict?
     */
    private boolean strict = false;
    
    /**
     * Archive file input stream.
     *
     * Keep it around so we can close it when done.
     *
     * <p>Set in constructor. Must support {@link RepositionableStream}
     * interface.  Make it protected so subclasses have access.
     */
    private InputStream in = null;
    
    /**
     * Maximum amount of recoverable exceptions in a row.
     * If more than this amount in a row, we'll let out the exception rather
     * than go back in for yet another retry.
     */
    public static final int MAX_ALLOWED_RECOVERABLES = 10;
    

    /**
     * The Record currently being read.
     *
     * Keep this ongoing reference so we'll close the record even if the caller
     * doesn't.
     */
    private ArchiveRecord currentRecord = null;
    
    /**
     * Descriptive string for the Archive file we're going against:
     * full path, url, etc. -- depends on context in which file was made.
     */
    private String identifier = null;
    
    /**
     * Archive file version.
     */
    private String version = null;
    
    
    protected ArchiveReader() {
        super();
    }
    
    /**
     * Convenience method used by subclass constructors.
     * @param i Identifier for Archive file this reader goes against.
     */
    protected void initialize(final String i) {
        setReaderIdentifier(i);
    }
    
    /**
     * Convenience method for constructors.
     * 
     * @param f File to read.
     * @param offset Offset at which to start reading.
     * @return InputStream to read from.
     * @throws IOException If failed open or fail to get a memory
     * mapped byte buffer on file.
     */
    protected InputStream getInputStream(final File f, final long offset)
    throws IOException {
        FileInputStream fin = new FileInputStream(f); 
        return new BufferedInputStream(fin);
    }

    public boolean isCompressed() {
        return this.compressed;
    }

    /**
     * Get record at passed <code>offset</code>.
     * 
     * @param offset Byte index into file at which a record starts.
     * @return An Archive Record reference.
     * @throws IOException
     */
    public ArchiveRecord get(long offset) throws IOException {
        cleanupCurrentRecord();
        long posn = positionForRecord(in); 
        if(offset>=posn) {
            in.skip(offset-posn); 
        } else {
            throw new UnsupportedOperationException("no reverse seeking: at "+posn+" requested "+offset); 
        }
        return createArchiveRecord(this.in, offset);
    }
    
    /**
     * @return Return Archive Record created against current offset.
     * @throws IOException
     */
    public ArchiveRecord get() throws IOException {
        return createArchiveRecord(this.in, positionForRecord(in));
    }

    public void close() throws IOException {
        if (this.in != null) {
            this.in.close();
            this.in = null;
        }
    }
    
    /**
     * Cleanout the current record if there is one.
     * @throws IOException
     */
    protected void cleanupCurrentRecord() throws IOException {
        if (this.currentRecord != null) {
            this.currentRecord.close();
            gotoEOR(this.currentRecord);
            this.currentRecord = null;
        }
    }
    
    /**
     * Return an Archive Record homed on <code>offset</code> into
     * <code>is</code>.
     * @param is Stream to read Record from.
     * @param offset Offset to find Record at.
     * @return ArchiveRecord instance.
     * @throws IOException
     */
    protected abstract ArchiveRecord createArchiveRecord(InputStream is,
    	long offset)
    throws IOException;
    
    /**
     * Skip over any trailing new lines at end of the record so we're lined up
     * ready to read the next.
     * @param record
     * @throws IOException
     */
    protected abstract void gotoEOR(ArchiveRecord record) throws IOException;
    
    public abstract String getFileExtension();
    public abstract String getDotFileExtension();

    /**
     * @return Version of this Archive file.
     */
    public String getVersion() {
    	return this.version;
    }

    /**
     * Validate the Archive file.
     *
     * This method iterates over the file throwing exception if it fails
     * to successfully parse any record.
     *
     * <p>Assumes the stream is at the start of the file.
     * @return List of all read Archive Headers.
     *
     * @throws IOException
     */
    public List<ArchiveRecordHeader> validate() throws IOException {
        return validate(-1);
    }

    /**
     * Validate the Archive file.
     *
     * This method iterates over the file throwing exception if it fails
     * to successfully parse.
     *
     * <p>We start validation from wherever we are in the stream.
     *
     * @param numRecords Number of records expected.  Pass -1 if number is
     * unknown.
     *
     * @return List of all read metadatas. As we validate records, we add
     * a reference to the read metadata.
     *
     * @throws IOException
     */
    public List<ArchiveRecordHeader> validate(int numRecords) 
    throws IOException {
        List<ArchiveRecordHeader> hdrList = new ArrayList<ArchiveRecordHeader>();
        int recordCount = 0;
        setStrict(true);
        for (Iterator<ArchiveRecord> i = iterator(); i.hasNext();) {
            recordCount++;
            ArchiveRecord r = i.next();
            if (r.getHeader().getLength() <= 0
                && r.getHeader().getMimetype().
                    equals(MimetypeUtils.NO_TYPE_MIMETYPE)) {
                throw new IOException("record content is empty.");
            }
            r.close();
            hdrList.add(r.getHeader());
        }

        if (numRecords != -1) {
            if (recordCount != numRecords) {
                throw new IOException("Count of records, " 
                        + Integer.toString(recordCount) 
                        + " is not equal to expected " 
                        + Integer.toString(numRecords));
            }
        }

        return hdrList;
    }

    /**
     * Test Archive file is valid.
     * Assumes the stream is at the start of the file.  Be aware that this
     * method makes a pass over the whole file. 
     * @return True if file can be successfully parsed.
     */
    public boolean isValid() {
        boolean valid = false;
        try {
            validate();
            valid = true;
        } catch(Exception e) {
            // File is not valid if exception thrown parsing.
            valid = false;
        }
    
        return valid;
    }

    /**
     * @return Returns the strict.
     */
    public boolean isStrict() {
        return this.strict;
    }

    /**
     * @param s The strict to set.
     */
    public void setStrict(boolean s) {
        this.strict = s;
    }

    /**
     * @param d True if we're to digest.
     */
    public void setDigest(boolean d) {
        this.digest = d;
    }

    /**
     * @return True if we're digesting as we read.
     */
    public boolean isDigest() {
        return this.digest;
    }
 
    protected Logger getLogger() {
        return Logger.getLogger(this.getClass().getName());
    }
    
    protected InputStream getInputStream() {
        return this.in;
    }
    
    /**
     * Returns an ArchiveRecord iterator.
     * Of note, on IOException, especially if ZipException reading compressed
     * ARCs, rather than fail the iteration, try moving to the next record.
     * If {@link ArchiveReader#strict} is not set, this will usually succeed.
     * @return An iterator over ARC records.
     */
    public Iterator<ArchiveRecord> iterator() {
        // Eat up any record outstanding.
        try {
            cleanupCurrentRecord();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return new ArchiveRecordIterator();
    }

	protected void setCompressed(boolean compressed) {
		this.compressed = compressed;
	}

    /**
     * @return The current ARC record or null if none.
     * After construction has the arcfile header record.
     * @see #get()
     */
	protected ArchiveRecord getCurrentRecord() {
		return this.currentRecord;
	}

	protected ArchiveRecord currentRecord(final ArchiveRecord currentRecord) {
		this.currentRecord = currentRecord;
        return currentRecord;
	}

	protected InputStream getIn() {
		return in;
	}

	protected void setIn(InputStream in) {
		this.in = in;
	}

	protected void setVersion(String version) {
		this.version = version;
	}

	public String getReaderIdentifier() {
		return this.identifier;
	}

	protected void setReaderIdentifier(final String i) {
		this.identifier = i;
	}
	
    /**
     * Log on stderr.
     * Logging should go via the logging system.  This method
     * bypasses the logging system going direct to stderr.
     * Should not generally be used.  Its used for rare messages
     * that come of cmdline usage of ARCReader ERRORs and WARNINGs.
     * Override if using ARCReader in a context where no stderr or
     * where you'd like to redirect stderr to other than System.err.
     * @param level Level to log message at.
     * @param message Message to log.
     */
    public void logStdErr(Level level, String message) {
        System.err.println(level.toString() + " " + message);
    }
    
//    /**
//     * Add buffering to RandomAccessInputStream.
//     */
//    protected class RandomAccessBufferedInputStream
//    extends BufferedInputStream implements RepositionableStream {
//
//        public RandomAccessBufferedInputStream(RandomAccessInputStream is)
//        		throws IOException {
//            super(is);
//        }
//
//        public RandomAccessBufferedInputStream(RandomAccessInputStream is, int size)
//        		throws IOException {
//            super(is, size);
//        }
//
//        public long position() throws IOException {
//            // Current position is the underlying files position
//            // minus the amount thats in the buffer yet to be read.
//            return ((RandomAccessInputStream)this.in).position() -
//            	(this.count - this.pos);
//        }
//
//        public void position(long position) throws IOException {
//            // Force refill of buffer whenever there's been a seek.
//            this.pos = 0;
//            this.count = 0;
//            ((RandomAccessInputStream)this.in).position(position);
//        }
//        
//        public int available() throws IOException {
//            // Avoid overflow on large datastreams
//            long amount = (long)in.available() + (long)(count - pos);
//            return (amount >= Integer.MAX_VALUE)? Integer.MAX_VALUE: (int)amount;
//        }
//    }
    
    /**
     * Inner ArchiveRecord Iterator class.
     * Throws RuntimeExceptions in {@link #hasNext()} and {@link #next()} if
     * trouble pulling record from underlying stream.
     * @author stack
     */
    protected class ArchiveRecordIterator implements Iterator<ArchiveRecord> {
        private final Logger logger =
            Logger.getLogger(this.getClass().getName());
        /**
         * @return True if we have more records to read.
         * @exception RuntimeException Can throw an IOException wrapped in a
         * RuntimeException if a problem reading underlying stream (Corrupted
         * gzip, etc.).
         */
        public boolean hasNext() {
            // Call close on any extant record.  This will scoot us past
            // any content not yet read.
            try {
                cleanupCurrentRecord();
            } catch (IOException e) {
                if (isStrict()) {
                    throw new RuntimeException(e);
                }
                if (e instanceof EOFException) {
                    logger.warning("Premature EOF cleaning up " + 
                        currentRecord.getHeader().toString() + ": " +
                        e.getMessage());
                    return false;
                }
                // If not strict, try going again.  We might be able to skip
                // over the bad record.
                logger.log(Level.WARNING,"Trying skip of failed record cleanup of " +
                    currentRecord.getHeader().toString() + ": " +
                    e.getMessage(), e);
            }
            return innerHasNext();
        }
        
        protected boolean innerHasNext() {
            try {
                return getInputStream().available() > 0;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        /**
         * Tries to move to next record if we get
         * {@link RecoverableIOException}. If not <code>strict</code>
         * tries to move to next record if we get an
         * {@link IOException}.
         * @return Next object.
         * @exception RuntimeException Throws a runtime exception,
         * usually a wrapping of an IOException, if trouble getting
         * a record (Throws exception rather than return null).
         */
        public ArchiveRecord next() {
            long offset = -1;
            try {
                offset = positionForRecord(getIn()); 
                return exceptionNext();
            } catch (IOException e) {
                if (!isStrict()) {
                    // Retry though an IOE.  Maybe we will succeed reading
                    // subsequent record.
                    try {
                        if (hasNext()) {
                            getLogger().warning("Bad Record. Trying skip " +
                                "(Record start " +  offset + "): " +
                                e.getMessage());
                            return exceptionNext();
                        }
                        // Else we are at last record.  Iterator#next is
                        // expecting value. We do not have one. Throw exception.
                        throw new RuntimeException("Retried but no next " + 
                            "record (Record start " + offset + ")", e);
                    } catch (IOException e1) {
                        throw new RuntimeException("After retry (Offset " +
                                offset + ")", e1);
                    }
                }
                throw new RuntimeException("(Record start " + offset + ")", e);
            }
        }
        
        /**
         * A next that throws exceptions and has handling of
         * recoverable exceptions moving us to next record. Can call
         * hasNext which itself may throw exceptions.
         * @return Next record.
         * @throws IOException
         * @throws RuntimeException Thrown when we've reached maximum
         * retries.
         */
        protected ArchiveRecord exceptionNext()
        throws IOException, RuntimeException {
            ArchiveRecord result = null;
            IOException ioe = null;
            for (int i = MAX_ALLOWED_RECOVERABLES; i > 0 &&
                    result == null; i--) {
                ioe = null;
                try {
                    result = innerNext();
                } catch (RecoverableIOException e) {
                    ioe = e;
                    getLogger().warning(e.getMessage());
                    if (hasNext()) {
                        continue;
                    }
                    // No records left.  Throw exception rather than
                    // return null.  The caller is expecting to get
                    // back a record since they've just called
                    // hasNext.
                    break;
                }
            }
            if (ioe != null) {
                // Then we did MAX_ALLOWED_RECOVERABLES retries.  Throw
                // the recoverable ioe wrapped in a RuntimeException so
                // it goes out pass checks for IOE.
                throw new RuntimeException("Retried " +
                    MAX_ALLOWED_RECOVERABLES + " times in a row", ioe);
            }
            return result;
        }
        
        protected ArchiveRecord innerNext() throws IOException {
            return get(positionForRecord(getIn()));
        }
        
        public void remove() {
            throw new UnsupportedOperationException();
        }
    }
    
    protected static long positionForRecord(InputStream in) {
        return (in instanceof GZIPMembersInputStream) 
            ? ((GZIPMembersInputStream)in).getCurrentMemberStart()
            : ((CountingInputStream)in).getCount();
    }
    
    protected static String stripExtension(final String name,
    		final String ext) {
        return (!name.endsWith(ext))? name:
            name.substring(0, name.length() - ext.length());
    }
    
    /**
     * @return short name of Archive file.
     */
    public String getFileName() {
        return (new File(getReaderIdentifier())).getName();
    }

    /**
     * @return short name of Archive file.
     */
    public String getStrippedFileName() {
        return getStrippedFileName(getFileName(),
    		getDotFileExtension());
    }
    
    /**
     * @param name Name of ARCFile.
     * @param dotFileExtension '.arc' or '.warc', etc.
     * @return short name of Archive file.
     */
    public static String getStrippedFileName(String name,
    		final String dotFileExtension) {
    	name = stripExtension(name,
    		ArchiveFileConstants.DOT_COMPRESSED_FILE_EXTENSION);
    	return stripExtension(name, dotFileExtension);
    }
    
    /**
     * @param value Value to test.
     * @return True if value is 'true', else false.
     */
    protected static boolean getTrueOrFalse(final String value) {
    	if (value == null || value.length() <= 0) {
    		return false;
    	}
        return Boolean.TRUE.toString().equals(value.toLowerCase());
    }
    
    /**
     * @param format Format to use outputting.
     * @throws IOException
     * @throws java.text.ParseException
     * @return True if handled.
     */
    protected boolean output(final String format)
    throws IOException, java.text.ParseException {
    	boolean result = true;
        // long start = System.currentTimeMillis();
    	
        // Write output as pseudo-CDX file.  See
        // http://www.archive.org/web/researcher/cdx_legend.php
        // and http://www.archive.org/web/researcher/example_cdx.php.
        // Hash is hard-coded straight SHA-1 hash of content.
        if (format.equals(DUMP)) {
        	// No point digesting dumping.
        	setDigest(false);
            dump(false);
        } else if (format.equals(GZIP_DUMP)) {
        	// No point digesting dumping.
        	setDigest(false);
            dump(true);
        } else if (format.equals(CDX)) {
        	cdxOutput(false);   
        } else if (format.equals(CDX_FILE)) {
            cdxOutput(true);
        } else {
        	result = false;
        }	
        return result;
    }
    
    protected void cdxOutput(boolean toFile)
    throws IOException {
        BufferedWriter cdxWriter = null;
        if (toFile) {
            String cdxFilename = stripExtension(getReaderIdentifier(),
                DOT_COMPRESSED_FILE_EXTENSION);
            cdxFilename = stripExtension(cdxFilename, getDotFileExtension());
            cdxFilename += ('.' + CDX);
            cdxWriter = new BufferedWriter(new FileWriter(cdxFilename));
        }
        
        String header = "CDX b e a m s c " + ((isCompressed()) ? "V" : "v")
            + " n g";
        if (toFile) {
            cdxWriter.write(header);
            cdxWriter.newLine();
        } else {
            System.out.println(header);
        }
        
        String strippedFileName = getStrippedFileName();
        try {
            for (Iterator<ArchiveRecord> ii = iterator(); ii.hasNext();) {
            	ArchiveRecord r = ii.next();
                if (toFile) {
                    cdxWriter.write(r.outputCdx(strippedFileName));
                    cdxWriter.newLine();
                } else {
                    System.out.println(r.outputCdx(strippedFileName));
                }
            }
        } finally {
            if (toFile) {
                cdxWriter.close();
            }
        }
    }
    
    /**
     * Output passed record using passed format specifier.
     * @param format What format to use outputting.
     * @throws IOException
     * @return True if handled.
     */
    public boolean outputRecord(final String format)
    throws IOException {
    	boolean result = true;
        if (format.equals(CDX)) {
            System.out.println(get().outputCdx(getStrippedFileName()));
        } else if(format.equals(ArchiveFileConstants.DUMP)) {
            // No point digesting if dumping content.
            setDigest(false);
            get().dump();
        } else {
        	result = false;
        }
        return result;
    }

    /**
     * Dump this file on STDOUT
     * @throws compress True if dumped output is compressed.
     * @throws IOException
     * @throws java.text.ParseException
     */
    public abstract void dump(final boolean compress)
    throws IOException, java.text.ParseException;
    
    /**
     * @return an ArchiveReader that will delete a local file on close.  Used
     * when we bring Archive files local and need to clean up afterward.
     */
    public abstract ArchiveReader getDeleteFileOnCloseReader(final File f);
    
    /**
     * Output passed record using passed format specifier.
     * @param r ARCReader instance to output.
     * @param format What format to use outputting.
     * @throws IOException
     */
    protected static void outputRecord(final ArchiveReader r,
        final String format)
    throws IOException {
        if (!r.outputRecord(format)) {
            throw new IOException("Unsupported format" +
                " (or unsupported on a single record): " + format);
        }
    }
    
    /**
     * @return Base Options object filled out with help, digest, strict, etc.
     * options.
     */
    protected static Options getOptions() {
        Options options = new Options();
        options.addOption(new Option("h","help", false,
            "Prints this message and exits."));
        options.addOption(new Option("o","offset", true,
            "Outputs record at this offset into file."));
        options.addOption(new Option("d","digest", true,
            "Pass true|false. Expensive. Default: true (SHA-1)."));
        options.addOption(new Option("s","strict", false,
            "Strict mode. Fails parse if incorrectly formatted file."));
        options.addOption(new Option("f","format", true,
            "Output options: 'cdx', cdxfile', 'dump', 'gzipdump'," +
            "'or 'nohead'. Default: 'cdx'."));
        return options;
    }
}