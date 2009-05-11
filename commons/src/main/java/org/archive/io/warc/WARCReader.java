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
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.cli.PosixParser;
import org.apache.commons.lang.NotImplementedException;
import org.archive.io.ArchiveReader;
import org.archive.io.ArchiveRecord;
import org.archive.io.warc.WARCConstants;

/**
 * WARCReader.
 * Go via {@link WARCReaderFactory} to get instance.
 * @author stack
 * @version $Date: 2006-11-27 18:03:03 -0800 (Mon, 27 Nov 2006) $ $Version$
 */
public class WARCReader extends ArchiveReader implements WARCConstants {
    WARCReader() {
        super();
    }
    
    @Override
    protected void initialize(String i) {
        super.initialize(i);
        setVersion(WARC_VERSION);
    }
    
    /**
     * Skip over any trailing new lines at end of the record so we're lined up
     * ready to read the next.
     * @param record
     * @throws IOException
     */
    protected void gotoEOR(ArchiveRecord record) throws IOException {
        if (record.available() != 0) {
            throw new IOException("Record should be exhausted before coming " +
                "in here");
        }

        // Records end in 2*CRLF.  Suck it up.
        readExpectedChar(getIn(), CRLF.charAt(0));
        readExpectedChar(getIn(), CRLF.charAt(1));
        readExpectedChar(getIn(), CRLF.charAt(0));
        readExpectedChar(getIn(), CRLF.charAt(1));
    }
    
    protected void readExpectedChar(final InputStream is, final int expected)
    throws IOException {
        int c = is.read();
        if (c != expected) {
            throw new IOException("Unexpected character " +
                Integer.toHexString(c) + "(Expecting " +
                Integer.toHexString(expected) + ")");
        }
    }
    
    /**
     * Create new WARC record.
     * Encapsulate housekeeping that has to do w/ creating new Record.
     * @param is InputStream to use.
     * @param offset Absolute offset into WARC file.
     * @return A WARCRecord.
     * @throws IOException
     */
    protected WARCRecord createArchiveRecord(InputStream is, long offset)
    throws IOException {
        return (WARCRecord)currentRecord(new WARCRecord(is,
        	getReaderIdentifier(), offset, isDigest(), isStrict()));
    }
    
	@Override
	public void dump(boolean compress)
	throws IOException, java.text.ParseException {
	    for (final Iterator<ArchiveRecord> i = iterator(); i.hasNext();) {
            ArchiveRecord r = i.next();
            System.out.println(r.getHeader().toString());
            r.dump();
            System.out.println();
        }
	}
    

    @Override
    public ArchiveReader getDeleteFileOnCloseReader(final File f) {
        throw new NotImplementedException("TODO");
    }  

	@Override
	public String getDotFileExtension() {
		return DOT_WARC_FILE_EXTENSION;
	}

	@Override
	public String getFileExtension() {
		return WARC_FILE_EXTENSION;
	} 
    
    // Static methods follow.  Mostly for command-line processing.

    /**
     *
     * @param formatter Help formatter instance.
     * @param options Usage options.
     * @param exitCode Exit code.
     */
    private static void usage(HelpFormatter formatter, Options options,
            int exitCode) {
        formatter.printHelp("java org.archive.io.arc.WARCReader" +
            " [--digest=true|false] \\\n" +
            " [--format=cdx|cdxfile|dump|gzipdump]" +
            " [--offset=#] \\\n[--strict] [--parse] WARC_FILE|WARC_URL",
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
    protected static void output(WARCReader reader, String format)
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
    	WARCReader r = WARCReaderFactory.get(urlOrPath);
    	r.setStrict(false);
    	r.setDigest(true);
    	output(r, CDX_FILE);
    }

    /**
     * Command-line interface to WARCReader.
     *
     * Here is the command-line interface:
     * <pre>
     * usage: java org.archive.io.arc.WARCReader [--offset=#] ARCFILE
     *  -h,--help      Prints this message and exits.
     *  -o,--offset    Outputs record at this offset into arc file.</pre>
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
                    
                case 'd':
                	digest = getTrueOrFalse(cmdlineOptions[i].getValue());
                    break;
                    
                case 'f':
                    format = cmdlineOptions[i].getValue().toLowerCase();
                    boolean match = false;
                    // List of supported formats.
                    final String [] supportedFormats =
                		{CDX, DUMP, GZIP_DUMP, CDX_FILE};
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
            WARCReader r = WARCReaderFactory.get(
            	new File((String)cmdlineArgs.get(0)), offset);
            r.setStrict(strict);
            outputRecord(r, format);
        } else {
            for (Iterator i = cmdlineArgs.iterator(); i.hasNext();) {
                String urlOrPath = (String)i.next();
                try {
                	WARCReader r = WARCReaderFactory.get(urlOrPath);
                	r.setStrict(strict);
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