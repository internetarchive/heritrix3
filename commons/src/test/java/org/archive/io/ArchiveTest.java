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

import java.io.IOException;
import java.text.NumberFormat;
import org.archive.io.arc.ARCReader;
import org.archive.io.arc.ARCReaderFactory;
import org.archive.io.arc.ARCRecord;
import org.archive.io.arc.ARCRecordMetaData;
import org.archive.io.warc.WARCReader;
import org.archive.io.warc.WARCReaderFactory;
import org.archive.io.warc.WARCRecord;

import java.util.logging.Logger;

/**
 * useful for determining why ArchiveReader fails for problematic W/ARC files
 * @author siznax
 *
 */
public class ArchiveTest 
{
	/** input W/ARC filename */
    String arcFilename;

    void setArcFile(String arcFile) {
    	this.arcFilename = arcFile;    	
    }
    
    /** one of available modes */
    String mode;
	public void setMode(String mode) { 
		this.mode = mode; 
	}

    /** mimetype to select from input */
    String filter;
    public void setFilter(String filter) { 
    	this.filter = filter; 
    }

    /** byte offset into input file */
    long offset;
	public void setOffset(long offset) { 
		this.offset = offset; 
	}

    /** W/ARC record index to begin output */
	protected int recordStartIndex;

	/** W/ARC record index to end output */
	protected int recordEndIndex;
    void setRecordRange(int start, int end) {
		this.recordStartIndex = start;
		this.recordEndIndex = end;
	}

    /** count of W/ARC records found in input */
	protected int recordCount;

	/** count of selected mimetype found in input */
	protected int filterCount;

	/** logger for errors, warnings */
    private static Logger logger = Logger.getLogger(ArchiveTest.class.getName());

    /** main method modes to scan for errors, filter, 
     * and emulate wayback use cases 
     */
	public static String[] modes = {"index","replay","dump","cdx","filter"};

	/** arbitrary buffer size for replay mode */
	static int BUFFER_SIZE = 1024*16;
    
	/** some typical mimetypes found in W/ARCs */
	static String[] mimeTypes =	{
		"image/gif",
		"image/png",
		"text/css",
		"text/dns",
		"text/html",
		"text/plain"
	};

	public ArchiveTest() throws IOException {
	}
        
	/**
	 * @return true if archive filename ends in "arc" or "arc.gz" 
	 */
	boolean isARCFormat() {
		return this.arcFilename.endsWith(".arc") 
			|| this.arcFilename.endsWith(".arc.gz");
	}

	/**
	 * @return ARCReader if {@link #isARCFormat()}=true, else WARCReader
	 * @throws IOException
	 */
	ArchiveReader getReader() throws IOException {
		if (this.isARCFormat()) {
			return ARCReaderFactory.get(this.arcFilename);
		} else {
			return WARCReaderFactory.get(this.arcFilename);
		}  
	}

	/**
	 * @param  index current record index into arc file
	 * @return true if current index is in range
	 */
	boolean inRecordRange(long index) {
    	if (index >= this.recordStartIndex && index <= this.recordEndIndex)
    		return true;
    	else
    		return false;
    }
    
	/**
	 * @param r ArchiveRecord
	 * @param filter mimetype string, see mimeTypes
	 * @return true if current record mimetype equals mimetype filter field
	 */
	boolean filterMimeType(ArchiveRecord r, String filter) {
		if (r.getHeader().getMimetype().equals(this.filter)) 
			return true;
		else
			return false;
	}

	void logRecordErrors(ArchiveRecord record) {
        Logger logger = Logger.getLogger(this.getClass().getName());
        if (this.isARCFormat()) {
        	ARCRecord arcRecord = (ARCRecord) record;
        	if (arcRecord.hasErrors()) {
        		ArchiveRecordHeader header = record.getHeader();
        		logger.warning("record at offset: " + header.getOffset()
        				+ " has errors: " + arcRecord.getErrors());
        	}
        } else {
        	WARCRecord warcRecord = (WARCRecord) record;
        	warcRecord.getHeader();
        }
	}
	
	/** emulate ArchiveRecord.outputCDX for comparison */
	static void outputCdx(ArchiveRecordHeader h) { 
		Long rl = h.getLength();
		Long ro = h.getOffset();
		String[] hdr = { 
				h.getDate(),
				"-", // Ip
				h.getUrl(),
				h.getMimetype(),
				"-", // status code
				"-", // digest
				ro.toString(),
				rl.toString(),
		};
		for (String fld : hdr) 
			System.out.print(fld + " ");
		System.out.println();
	}

	void printMetadata(ARCRecord record, ArchiveRecordHeader header) {
		System.out.print( "  Date  : " + header.getDate() + "\n"
				+ "  IP    : " + ((ARCRecordMetaData)header).getIp()  + "\n"
				+ "  URL   : " + header.getUrl() + "\n"
				+ "  MIME  : " + header.getMimetype()   + "\n"
				+ "  Status: " + ((ARCRecordMetaData)header).getStatusCode() + "\n"
				+ "  Digest: " + record.getDigestStr() + "\n"
				+ "  Offset: " + header.getOffset() + "\n"
				+ "  Length: " + header.getLength() + "\n");
	}
	
	void printMetadata(WARCRecord record, ArchiveRecordHeader header) {
		System.out.print( "  Date  : " + header.getDate() + "\n"
				+ "  IP    : " + header.getHeaderValue("WARC-IP-Address") + "\n"
				+ "  URL   : " + header.getUrl() + "\n"
				+ "  MIME  : " + header.getMimetype() + "\n"
				+ "  Status: " + "-" + "\n"
				+ "  Digest: " + header.getHeaderValue("WARC-Payload-Digest") + "\n"
				+ "  Offset: " + header.getOffset() + "\n"
				+ "  Length: " + header.getLength() + "\n");
	}
	
	void printInfo() {
		System.out.println(this.getClass().getName());
		System.out.println("  file:    " + this.arcFilename);
		System.out.println("  format:  " + this.getFormat());
		System.out.println("  mode:    " + this.mode);
		if (this.mode.equals("filter"))
			System.out.println("  filter:  " + this.filter);
		if (this.mode.equals("fetch"))
		System.out.println("  offset:  " + this.offset);
		if (this.mode.equals("filter") 
				|| this.mode.equals("cdx") 
				|| this.mode.equals("dump"))
			System.out.println("  range:   " + "[" + this.recordStartIndex 
					+ "," + this.recordEndIndex + "]");
	}

	/**
	 * return W/ARC extension and compression extension 
	 */
	String getFormat() {
		if(this.arcFilename.endsWith(".gz")) {
			return this.arcFilename.substring(this.arcFilename
					.lastIndexOf(".",this.arcFilename.length()-4));
		}
		return this.arcFilename.substring(this.arcFilename.lastIndexOf("."));
	}

	/**
	 * process output by selected mode
	 * @throws IOException
	 */
	void readArchive() throws IOException {

    	ArchiveReader reader = this.getReader();

        if (this.mode.equals("index")) {
        	// parse HTTP header only
			System.out.println("INDEX " + this.getArcType()
					+ " record at offset: " + offset);
			if (this.isARCFormat()) {
				indexRecord((ARCReader)reader);
			} else {
				indexRecord((WARCReader)reader);
			}
        } else if (this.mode.equals("replay")) {
        	// skip header and read  
        	System.out.println("REPLAY " + this.getArcType() 
        			+ " record at offset: " + offset + "");
        	if (this.isARCFormat()) {
        		this.replayRecord((ARCReader)reader);
        	} else {
        		this.replayRecord((WARCReader)reader);
        	}
        } else if (this.mode.equals("dump")) { 
        	this.dumpArchive(reader);
        } else if (this.mode.equals("cdx")) { 
        	this.outputArchiveCDX(reader);
        } else if (this.mode.equals("filter")) { // filter MIME type
        	this.filterArchive(reader);
        } else { // scan; do nothing, but count iterations
        	this.scanArchive(reader);
        }
        if (this.offset == -1) {
        	System.out.println("\n========== found: " 
        			+ this.recordCount + " records. ");
        }
		System.out.println("\n========== Done.");
	}

	/**
	 * get archive type by file extension 
	 * @return arc file extension, e.g. 'warc.gz'
	 */
	private String getArcType() {
		return getFormat().split("\\.")[1];
	}

	/**
	 * scan (read) archive printing "." for each record or errors if they occur
	 * and total number of records found
	 * @param reader and ArchiveReader instance
	 */
	private void scanArchive(ArchiveReader reader) {
    	System.out.println();
    	for (ArchiveRecord record : reader) {
    		this.recordCount++;
    		logRecordErrors(record);
    		System.out.print(".");
    		if ((this.recordCount % 100) == 0) 
    			System.out.print("[" + this.recordCount+ "]\n");
    	}
	}

	/**
	 * filter archive on a mimetype for records in range
	 * @param reader an ArchiveReader instance
	 */
	private void filterArchive(ArchiveReader reader) {
    	for (ArchiveRecord record : reader) {
    		recordCount++;
    		if (inRecordRange(recordCount)) {
    			if (filterMimeType(record,this.filter)==true) {
    				System.out.print(mode + " [" + recordCount + "] ");
    				outputCdx(record.getHeader());
    				filterCount++;
    			}
    		}
    		if (recordCount > this.recordEndIndex)
    			break;
    	}
    	double filterPercent = (double)filterCount/recordCount;
    	NumberFormat filterPercentFmt = NumberFormat.getPercentInstance();
    	filterPercentFmt.setMinimumFractionDigits(2);
    	System.out.println("\n========== found: " 
    			+ filterCount + "/" + recordCount + " = " 
    			+ filterPercentFmt.format(filterPercent)
    			+ " mimetype=" + filter 
    			+ " records. ");
	}

	/**
	 * output CDX-like output for records in range
	 * @param reader an ArchiveReader instance
	 */
	private void outputArchiveCDX(ArchiveReader reader) {
		for (ArchiveRecord record : reader) {
			recordCount++;
			if (inRecordRange(recordCount)) {
				System.out.print(mode + " [" + recordCount + "] ");
				logRecordErrors(record);
				outputCdx(record.getHeader());
			}
    		if (recordCount > this.recordEndIndex) {
    			break;
    		}
		}
	}

	/**
	 * write records in range on STDOUT
	 * @param reader an ArchiveReader instance
	 * @throws IOException
	 */
	private void dumpArchive(ArchiveReader reader) throws IOException {
    	for (ArchiveRecord record : reader) {
			recordCount++;
			if (inRecordRange(recordCount)) {
				System.out.println("\n********** "
						+ mode + " ["+recordCount+"] " 
						+ "**********\n");  
				record.dump();
			} 
    		if (recordCount > this.recordEndIndex) {
    			break;
    		}
		}
	}

	/**
	 * wayback-like replay of ARC record at offset
	 * @param arcReader an ARCReader intance
	 * @throws IOException
	 */
	private void replayRecord(ARCReader arcReader) throws IOException {
    	arcReader.setStrict(true);
    	ARCRecord arcRecord = (ARCRecord) arcReader.get(this.offset);
    	arcRecord.skipHttpHeader();
    	if (arcRecord.hasErrors()) {
    		logger.warning("record has errors: " + arcRecord.getErrors());
    	}
    	byte[] buffer = new byte[BUFFER_SIZE];
    	if (arcRecord.available() > 0) {
    		// for (int r = -1; (r = arcRecord.read(buffer, 0, BUFFER_SIZE)) != -1;) {
    		int r = -1;
    		while((r = arcRecord.read(buffer, 0, BUFFER_SIZE)) != -1) {
    			// os.write(buffer, 0, r);
    			System.out.write(buffer, 0, r);
    		}
    	} else {
    		System.out.println("record bytes available: " 
    				+ arcRecord.available());
    	}
	}
	
	/**
	 * wayback-like replay of WARC record at offset
	 * @param warcReader a WARCReader instance
	 * @throws IOException
	 */
	private void replayRecord(WARCReader warcReader) throws IOException {
		warcReader.setStrict(true);
		WARCRecord warcRecord = (WARCRecord) warcReader.get(this.offset);
    	byte[] buffer = new byte[BUFFER_SIZE];
    	if (warcRecord.available() > 0) {
    		int r = -1;
    		while((r = warcRecord.read(buffer, 0, BUFFER_SIZE)) != -1) {
    			System.out.write(buffer, 0, r);
    		}
    	}
		System.out.println("record bytes available: "
				+ warcRecord.available());
	}

	/**
	 * wayback-like index an ARC record at offset
	 * @param arcReader an ARCReader instance
	 * @throws IOException
	 */
	private void indexRecord(ARCReader arcReader) throws IOException {
		arcReader.setStrict(true);
		arcReader.setParseHttpHeaders(true);
		ARCRecord arcRecord = (ARCRecord) arcReader.get(this.offset);
		ArchiveRecordHeader header = arcRecord.getHeader();
		if (arcRecord.hasErrors()) 
			logger.warning("record has errors: " + arcRecord.getErrors());
		System.out.println("========== dumping HTTP header:");
		arcRecord.dumpHttpHeader();
		System.out.println("========== selected metadata:");
		arcRecord.close(); // must close record to get digest
		printMetadata(arcRecord,header);
		System.out.println("========== getting metadata:");
		System.out.println(arcRecord.getMetaData());
		System.out.println("\n"
				+ "record length declared: " 
				+ header.getLength() + "\n"
				+ "header bytes read     : " 
				+ arcRecord.httpHeaderBytesRead);
	}

	/**
	 * wayback-like index a WARC record at offset
	 * @param warcReader a WARCReader instance
	 * @throws IOException
	 */
	private void indexRecord(WARCReader warcReader) throws IOException {
		warcReader.setStrict(true);
		// warcReader.setParseHttpHeaders(true);
		WARCRecord warcRecord = (WARCRecord)warcReader.get(this.offset);
		ArchiveRecordHeader header = warcRecord.getHeader();
		System.out.println("========== selected metadata:");
		warcRecord.close(); // must close record to get digest
		printMetadata(warcRecord,header);
		System.out.println("========== header: \n" + header);
	}

	
	/**
	 * test (scan|cdx|index|replay|dump) an archive.  
	 * some of these modes are use-cases for wayback indexing mentioned in:
	 * http://webarchive.jira.com/browse/HER-1568
	 * @param arcfile a ARC or WARC archive (possibly .gz) 
	 * @param offset byte offset into archive 
	 * @param mode (default=scan)|cdx|index|replay|dump
	 * @param record_range_start record index start (default=0)
	 * @param record_range_end record index end (default=100)
	 * @param filter mimetype, e.g. "text/html"
	 * @throws IOException
	 */
	public static void main(String[] args) throws IOException {
		new ArchiveTest().instanceMain(args);
	}

	public void instanceMain(String[] args) throws IOException {
		if (args.length > 1) {
			int offset    = Integer.valueOf(args[1]); 
			String mode   = (args.length>2) ? args[2] : "scan";
			int start     = (args.length>3) ? Integer.valueOf(args[3]) : 0;
			int end       = (args.length>4) ? Integer.valueOf(args[4]) : 100;
			String filter = (args.length>5) ? args[5] : null;
			setArcFile(args[0]);
			setOffset(Integer.valueOf(args[1]));
			setOffset(offset);
			setMode(mode);
			setRecordRange(start,end);
			setFilter(filter);
			printInfo();
			readArchive();
		} else {
			String usage = "ArcWarcTests.java arcfile offset " 
				+ "[ [scan|cdx|index|replay|dump] " 
				+ "record_range_start record_range_end filter]";
			System.out.println(usage);
		}
	}
}
