/* $Id$
 *
 * Created Aug 29, 2006
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
package org.archive.io;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.cli.PosixParser;
import org.archive.io.arc.ARCConstants;
import org.archive.io.arc.ARCReader;
import org.archive.io.arc.ARCReaderFactory;
import org.archive.io.arc.ARCRecord;
import org.archive.io.warc.WARCConstants;
import org.archive.io.warc.WARCWriter;
import org.archive.util.FileUtils;
import org.archive.util.anvl.ANVLRecord;
import org.joda.time.DateTimeZone;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.ISODateTimeFormat;


/**
 * Convert ARCs to (sortof) WARCs.
 * @author stack
 * @version $Date$ $Revision$
 */
public class Arc2Warc {
   private static void usage(HelpFormatter formatter, Options options,
           int exitCode) {
       formatter.printHelp("java org.archive.io.arc.Arc2Warc " +
       		"[--force] ARC_INPUT WARC_OUTPUT", options);
       System.exit(exitCode);
   }
   
   private static String getRevision() {
       return Warc2Arc.parseRevision("$Revision$");
   }
   
   public void transform(final File arc, final File warc, final boolean force)
   throws IOException {
       FileUtils.assertReadable(arc);
       if (warc.exists() && !force) {
    	   throw new IOException("Target WARC already exists. " +
    	       "Will not overwrite.");
       }

       ARCReader reader = ARCReaderFactory.get(arc, false, 0);
       transform(reader, warc);
   }
   
   protected void transform(final ARCReader reader, final File warc)
   throws IOException {
	   WARCWriter writer = null;
	   // No point digesting. Digest is available after reading of ARC which
	   // is too late for inclusion in WARC.
	   reader.setDigest(false);
	   try {
		   BufferedOutputStream bos =
			   new BufferedOutputStream(new FileOutputStream(warc));
		   // Get the body of the first ARC record as a String so can dump it
		   // into first record of WARC.
		   final Iterator<ArchiveRecord> i = reader.iterator();
		   ARCRecord firstRecord = (ARCRecord)i.next();
		   ByteArrayOutputStream baos =
			   new ByteArrayOutputStream((int)firstRecord.getHeader().
			       getLength());
		   firstRecord.dump(baos);
	       // Add ARC first record content as an ANVLRecord.
	       ANVLRecord ar = new ANVLRecord(1);
	       ar.addLabelValue("Filedesc", baos.toString());
	       List<String> metadata = new ArrayList<String>(1);
	       metadata.add(ar.toString());
	       // Now create the writer.  If reader was compressed, lets write
	       // a compressed WARC.
		   writer = new WARCWriter(null, bos, warc,
		       reader.isCompressed(), null, metadata);
		   // Write a warcinfo record with description about how this WARC
		   // was made.
		   writer.writeWarcinfoRecord(warc.getName(),
		       "Made from " + reader.getReaderIdentifier() + " by " +
	               this.getClass().getName() + "/" + getRevision());
		   for (; i.hasNext();) {
			   write(writer, (ARCRecord)i.next());
		   }
	   } finally {
		   if (reader != null) {
			   reader.close();
		   }
		   if (writer != null) {
			   // I don't want the close being logged -- least, not w/o log of
			   // an opening (and that'd be a little silly for simple script
			   // like this). Currently, it logs at level INFO so that close
			   // of files gets written to log files.  Up the log level just
			   // for the close.
			   Logger l = Logger.getLogger(writer.getClass().getName());
			   Level oldLevel = l.getLevel();
			   l.setLevel(Level.WARNING);
			   try {
				   writer.close();
			   } finally {
				   l.setLevel(oldLevel);
			   }
		   }
	   }
   }
   
   protected void write(final WARCWriter writer, final ARCRecord r)
   throws IOException {

       // convert ARC date to WARC-Date format
       String arcDateString = r.getHeader().getDate();
       String warcDateString = DateTimeFormat.forPattern("yyyyMMddHHmmss")
       .withZone(DateTimeZone.UTC)
       .parseDateTime(arcDateString)
       .toString(ISODateTimeFormat.dateTimeNoMillis());

       // If contentBody > 0, assume http headers.  Make the mimetype
       // be application/http.  Otherwise, give it ARC mimetype.
       String warcMimeTypeString = (r.getHeader().getContentBegin() > 0) ? 
               WARCConstants.HTTP_RESPONSE_MIMETYPE : 
                   r.getHeader().getMimetype();

       ANVLRecord ar = new ANVLRecord();
       String ip = (String)r.getHeader().
       getHeaderValue((ARCConstants.IP_HEADER_FIELD_KEY));
       if (ip != null && ip.length() > 0) {
           ar.addLabelValue(WARCConstants.NAMED_FIELD_IP_LABEL, ip);
       }

       writer.writeResourceRecord(r.getHeader().getUrl(), warcDateString,
               warcMimeTypeString, ar, r, r.getHeader().getLength());
   }

   /**
    * Command-line interface to Arc2Warc.
    *
    * @param args Command-line arguments.
    * @throws ParseException Failed parse of the command line.
    * @throws IOException
    * @throws java.text.ParseException
    */
   @SuppressWarnings("unchecked")
public static void main(String [] args)
   throws ParseException, IOException, java.text.ParseException {
       Options options = new Options();
       options.addOption(new Option("h","help", false,
           "Prints this message and exits."));
       options.addOption(new Option("f","force", false,
       	   "Force overwrite of target file."));
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
       boolean force = false;
       for (int i = 0; i < cmdlineOptions.length; i++) {
           switch(cmdlineOptions[i].getId()) {
               case 'h':
                   usage(formatter, options, 0);
                   break;
                   
               case 'f':
                   force = true;
                   break;
                   
               default:
                   throw new RuntimeException("Unexpected option: " +
                       + cmdlineOptions[i].getId());
           }
       }
       
       // If no args, print help.
       if (cmdlineArgs.size() != 2) {
           usage(formatter, options, 0);
       }
       (new Arc2Warc()).transform(new File(cmdlineArgs.get(0).toString()),
           new File(cmdlineArgs.get(1).toString()), force);
   }
}
