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

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.cli.PosixParser;
import org.archive.io.ArchiveReader;
import org.archive.io.ArchiveRecord;
import org.archive.io.ArchiveRecordHeader;
import org.archive.io.RecoverableIOException;
import org.archive.io.WriterPoolMember;
import org.archive.util.ArchiveUtils;


/**
 * Get an iterator on an ARC file or get a record by absolute position.
 *
 * ARC files are described here:
 * <a href="http://www.archive.org/web/researcher/ArcFileFormat.php">Arc
 * File Format</a>.
 *
 * <p>This class knows how to parse an ARC file.  Pass it a file path
 * or an URL to an ARC. It can parse ARC Version 1 and 2.
 *
 * <p>Iterator returns <code>ARCRecord</code>
 * though {@link Iterator#next()} is returning
 * java.lang.Object.  Cast the return.
 *
 * <p>Profiling java.io vs. memory-mapped ByteBufferInputStream shows the
 * latter slightly slower -- but not by much.  TODO: Test more.  Just
 * change {@link #getInputStream(File, long)}.
 *
 * @author stack
 * @version $Date$ $Revision$
 */
public abstract class ARCReader extends ArchiveReader
implements ARCConstants, Closeable {
    Logger logger = Logger.getLogger(ARCReader.class.getName());
    
    /**
     * Set to true if we are aligned on first record of Archive file.
     * We used depend on offset. If offset was zero, then we were
     * aligned on first record.  This is no longer necessarily the case when
     * Reader is created at an offset into an Archive file: The offset is zero
     * but its relative to where we started reading.
     */
    private boolean alignedOnFirstRecord = true;
    
    private boolean parseHttpHeaders = true;
    
    ARCReader() {
        super();
    }
    
    /**
     * Skip over any trailing new lines at end of the record so we're lined up
     * ready to read the next.
     * @param record
     * @throws IOException
     */
    protected void gotoEOR(ArchiveRecord record) throws IOException {
        if (getIn().available() <= 0) {
            return;
        }
        
        // Remove any trailing LINE_SEPARATOR
        int c = -1;
        while (getIn().available() > 0) {
            if (getIn().markSupported()) {
                getIn().mark(1);
            }
            c = getIn().read();
            if (c != -1) {
                if (c == LINE_SEPARATOR) {
                    continue;
                }
                if (getIn().markSupported()) {
                    // We've overread.  We're probably in next record.  There is
                    // no way of telling for sure. It may be dross at end of
                    // current record. Backup.
                        getIn().reset();
                    break;
                }
                ArchiveRecordHeader h = (getCurrentRecord() != null)?
                    record.getHeader(): null;
                throw new IOException("Read " + (char)c +
                    " when only " + LINE_SEPARATOR + " expected. " + 
                    getReaderIdentifier() + ((h != null)?
                        h.getHeaderFields().toString(): ""));
            }
        }
    }
    
    /**
     * Create new arc record.
     *
     * Encapsulate housekeeping that has to do w/ creating a new record.
     *
     * <p>Call this method at end of constructor to read in the
     * arcfile header.  Will be problems reading subsequent arc records
     * if you don't since arcfile header has the list of metadata fields for
     * all records that follow.
     * 
     * <p>When parsing through ARCs writing out CDX info, we spend about
     * 38% of CPU in here -- about 30% of which is in getTokenizedHeaderLine
     * -- of which 16% is reading.
     *
     * @param is InputStream to use.
     * @param offset Absolute offset into arc file.
     * @return An arc record.
     * @throws IOException
     */
    protected ARCRecord createArchiveRecord(InputStream is, long offset)
    throws IOException {
        try {
            String version = super.getVersion();
            ARCRecord record = new ARCRecord(is, getReaderIdentifier(), offset,
                    isDigest(), isStrict(), isParseHttpHeaders(),
                    isAlignedOnFirstRecord(), version);
            if (version != null && super.getVersion() == null)
                super.setVersion(version);
            currentRecord(record);
        } catch (IOException e) {
            if (e instanceof RecoverableIOException) {
                // Don't mess with RecoverableIOExceptions.  Let them out.
                throw e;
            }
            IOException newE = new IOException(e.getMessage() + " (Offset " +
                    offset + ").");
            newE.setStackTrace(e.getStackTrace());
            throw newE;
        }
        return (ARCRecord)getCurrentRecord();
    }
    
    /**
     * Returns version of this ARC file.  Usually read from first record of ARC.
     * If we're reading without having first read the first record -- e.g.
     * random access into middle of an ARC -- then version will not have been
     * set.  For now, we return a default, version 1.1.  Later, if more than
     * just one version of ARC, we could look at such as the meta line to see
     * what version of ARC this is.
     * @return Version of this ARC file.
     */
    public String getVersion() {
        return (super.getVersion() == null)? "1.1": super.getVersion();
    }

    protected boolean isAlignedOnFirstRecord() {
        return alignedOnFirstRecord;
    }

    protected void setAlignedOnFirstRecord(boolean alignedOnFirstRecord) {
        this.alignedOnFirstRecord = alignedOnFirstRecord;
    }
        
    /**
     * @return Returns the parseHttpHeaders.
     */
    public boolean isParseHttpHeaders() {
        return this.parseHttpHeaders;
    }
    
    /**
     * @param parse The parseHttpHeaders to set.
     */
    public void setParseHttpHeaders(boolean parse) {
        this.parseHttpHeaders = parse;
    }
    
        public String getFileExtension() {
                return ARC_FILE_EXTENSION;
        }
        
        public String getDotFileExtension() {
                return DOT_ARC_FILE_EXTENSION;
        }
        
        protected boolean output(final String format) 
        throws IOException, java.text.ParseException {
                boolean result = super.output(format);
                if(!result && (format.equals(NOHEAD) || format.equals(HEADER))) {
                        throw new IOException(format +
                                " format only supported for single Records");
                }
                return result;
        }
    
    public boolean outputRecord(final String format) throws IOException {
                boolean result = super.outputRecord(format);
                if (result) {
                        return result;
                }
                if (format.equals(NOHEAD)) {
                        // No point digesting if dumping content.
                        setDigest(false);
                        ARCRecord r = (ARCRecord) get();
                        r.skipHttpHeader();
                        r.dump();
                        result = true;
                } else if (format.equals(HEADER)) {
                        // No point digesting if dumping content.
                        setDigest(false);
                        ARCRecord r = (ARCRecord) get();
                        r.dumpHttpHeader();
                        result = true;
                }

                return result;
        }

    public void dump(final boolean compress)
    throws IOException, java.text.ParseException {
        // No point digesting if we're doing a dump.
        setDigest(false);
        boolean firstRecord = true;
        ARCWriter writer = null;
        for (Iterator<ArchiveRecord> ii = iterator(); ii.hasNext();) {
            ARCRecord r = (ARCRecord)ii.next();
            // We're to dump the arc on stdout.
            // Get the first record's data if any.
            ARCRecordMetaData meta = r.getMetaData();
            if (firstRecord) {
                firstRecord = false;
                // Get an ARCWriter.
                ByteArrayOutputStream baos =
                    new ByteArrayOutputStream(r.available());
                // This is slow but done only once at top of ARC.
                while (r.available() > 0) {
                    baos.write(r.read());
                }
                List<String> listOfMetadata = new ArrayList<String>();
                listOfMetadata.add(baos.toString(WriterPoolMember.UTF8));
                // Assume getArc returns full path to file.  ARCWriter
                // or new File will complain if it is otherwise.
                List<File> outDirs = new ArrayList<File>(); 
                WriterPoolSettingsData settings = 
                    new WriterPoolSettingsData("","",-1L,compress,outDirs,listOfMetadata); 
                writer = new ARCWriter(new AtomicInteger(), System.out,
                    new File(meta.getArc()), settings);
                continue;
            }
            
            writer.write(meta.getUrl(), meta.getMimetype(), meta.getIp(),
                ArchiveUtils.parse14DigitDate(meta.getDate()).getTime(),
                (int)meta.getLength(), r);
        }
        // System.out.println(System.currentTimeMillis() - start);
    }
    
    /**
     * @return an ArchiveReader that will delete a local file on close.  Used
     * when we bring Archive files local and need to clean up afterward.
     */
    public ARCReader getDeleteFileOnCloseReader(final File f) {
        final ARCReader d = this;
        return new ARCReader() {
            private final ARCReader delegate = d;
            private File archiveFile = f;
            
            public void close() throws IOException {
                this.delegate.close();
                if (this.archiveFile != null) {
                    if (archiveFile.exists()) {
                        archiveFile.delete();
                    }
                    this.archiveFile = null;
                }
            }
            
            public ArchiveRecord get(long o) throws IOException {
                return this.delegate.get(o);
            }
            
            public boolean isDigest() {
                return this.delegate.isDigest();
            }
            
            public boolean isStrict() {
                return this.delegate.isStrict();
            }
            
            public Iterator<ArchiveRecord> iterator() {
                return this.delegate.iterator();
            }
            
            public void setDigest(boolean d) {
                this.delegate.setDigest(d);
            }
            
            public void setStrict(boolean s) {
                this.delegate.setStrict(s);
            }
            
            public List<ArchiveRecordHeader> validate() throws IOException {
                return this.delegate.validate();
            }

            @Override
            public ArchiveRecord get() throws IOException {
                return this.delegate.get();
            }

            @Override
            public String getVersion() {
                return this.delegate.getVersion();
            }

            @Override
            public List<ArchiveRecordHeader> validate(int noRecords) throws IOException {
                return this.delegate.validate(noRecords);
            }

            @Override
            protected ARCRecord createArchiveRecord(InputStream is,
                    long offset)
            throws IOException {
                return this.delegate.createArchiveRecord(is, offset);
            }

            @Override
            protected void gotoEOR(ArchiveRecord record) throws IOException {
                this.delegate.gotoEOR(record);
            }

            @Override
            public void dump(boolean compress)
            throws IOException, java.text.ParseException {
                this.delegate.dump(compress);
            }

            @Override
            public String getDotFileExtension() {
                return this.delegate.getDotFileExtension();
            }

            @Override
            public String getFileExtension() {
                return this.delegate.getFileExtension();
            }
        };
    }
    
    // Static methods follow.

    /**
     *
     * @param formatter Help formatter instance.
     * @param options Usage options.
     * @param exitCode Exit code.
     */
    private static void usage(HelpFormatter formatter, Options options,
            int exitCode) {
        formatter.printHelp("java org.archive.io.arc.ARCReader" +
            " [--digest=true|false] \\\n" +
            " [--format=cdx|cdxfile|dump|gzipdump|header|nohead]" +
            " [--offset=#] \\\n[--strict] [--parse] ARC_FILE|ARC_URL",
                options);
        System.exit(exitCode);
    }

    /**
     * Write out the arcfile.
     * 
     * @param reader
     * @param format Format to use outputting.
     * @throws IOException
     * @throws java.text.ParseException
     */
    protected static void output(ARCReader reader, String format)
    throws IOException, java.text.ParseException {
        if (!reader.output(format)) {
            throw new IOException("Unsupported format: " + format);
        }
    }

    /**
     * Generate a CDX index file for an ARC file.
     *
     * @param urlOrPath The ARC file to generate a CDX index for
     * @throws IOException
     * @throws java.text.ParseException
     */
    public static void createCDXIndexFile(String urlOrPath)
    throws IOException, java.text.ParseException {
        ARCReader r = ARCReaderFactory.get(urlOrPath);
        r.setStrict(false);
        r.setParseHttpHeaders(true);
        r.setDigest(true);
        output(r, CDX_FILE);
    }

    /**
     * Command-line interface to ARCReader.
     *
     * Here is the command-line interface:
     * <pre>
     * usage: java org.archive.io.arc.ARCReader [--offset=#] ARCFILE
     *  -h,--help      Prints this message and exits.
     *  -o,--offset    Outputs record at this offset into arc file.</pre>
     *
     * <p>See in <code>$HERITRIX_HOME/bin/arcreader</code> for a script that'll
     * take care of classpaths and the calling of ARCReader.
     *
     * <p>Outputs using a pseudo-CDX format as described here:
     * <a href="http://www.archive.org/web/researcher/cdx_legend.php">CDX
     * Legent</a> and here
     * <a href="http://www.archive.org/web/researcher/example_cdx.php">Example</a>.
     * Legend used in below is: 'CDX b e a m s c V (or v if uncompressed) n g'.
     * Hash is hard-coded straight SHA-1 hash of content.
     *
     * @param args Command-line arguments.
     * @throws ParseException Failed parse of the command line.
     * @throws IOException
     * @throws java.text.ParseException
     */
    @SuppressWarnings("unchecked")
    public static void main(String [] args)
    throws ParseException, IOException, java.text.ParseException {
        Options options = getOptions();
        options.addOption(new Option("p","parse", false, "Parse headers."));
        PosixParser parser = new PosixParser();
        CommandLine cmdline = parser.parse(options, args, false);
        List cmdlineArgs = cmdline.getArgList();
        Option [] cmdlineOptions = cmdline.getOptions();
        HelpFormatter formatter = new HelpFormatter();

        // If no args, print help.
        if (cmdlineArgs.size() <= 0) {
            usage(formatter, options, 0);
        }

        // Now look at options passed.
        long offset = -1;
        boolean digest = false;
        boolean strict = false;
        boolean parse = false;
        String format = CDX;
        for (int i = 0; i < cmdlineOptions.length; i++) {
            switch(cmdlineOptions[i].getId()) {
                case 'h':
                    usage(formatter, options, 0);
                    break;

                case 'o':
                    offset =
                        Long.parseLong(cmdlineOptions[i].getValue());
                    break;
                    
                case 's':
                    strict = true;
                    break;
                    
                case 'p':
                        parse = true;
                    break;
                    
                case 'd':
                        digest = getTrueOrFalse(cmdlineOptions[i].getValue());
                    break;
                    
                case 'f':
                    format = cmdlineOptions[i].getValue().toLowerCase();
                    boolean match = false;
                    // List of supported formats.
                    final String [] supportedFormats =
                                {CDX, DUMP, GZIP_DUMP, HEADER, NOHEAD, CDX_FILE};
                    for (int ii = 0; ii < supportedFormats.length; ii++) {
                        if (supportedFormats[ii].equals(format)) {
                            match = true;
                            break;
                        }
                    }
                    if (!match) {
                        usage(formatter, options, 1);
                    }
                    break;

                default:
                    throw new RuntimeException("Unexpected option: " +
                        + cmdlineOptions[i].getId());
            }
        }
        
        if (offset >= 0) {
            if (cmdlineArgs.size() != 1) {
                System.out.println("Error: Pass one arcfile only.");
                usage(formatter, options, 1);
            }
            ARCReader arc = ARCReaderFactory.get((String)cmdlineArgs.get(0),
                offset);
            arc.setStrict(strict);
            // We must parse headers if we need to skip them.
            if (format.equals(NOHEAD) || format.equals(HEADER)) {
                parse = true;
            }
            arc.setParseHttpHeaders(parse);
            outputRecord(arc, format);
        } else {
            for (Iterator i = cmdlineArgs.iterator(); i.hasNext();) {
                String urlOrPath = (String)i.next();
                try {
                        ARCReader r = ARCReaderFactory.get(urlOrPath);
                        r.setStrict(strict);
                        r.setParseHttpHeaders(parse);
                        r.setDigest(digest);
                    output(r, format);
                } catch (RuntimeException e) {
                    // Write out name of file we failed on to help with
                    // debugging.  Then print stack trace and try to keep
                    // going.  We do this for case where we're being fed
                    // a bunch of ARCs; just note the bad one and move
                    // on to the next.
                    System.err.println("Exception processing " + urlOrPath +
                        ": " + e.getMessage());
                    e.printStackTrace(System.err);
                    System.exit(1);
                }
            }
        }
    }
}
