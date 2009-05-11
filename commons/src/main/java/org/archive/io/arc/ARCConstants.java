/*
 * ARCConstants
 *
 * $Id$
 *
 * Created on Dec 30, 2003.
 *
 * Copyright (C) 2003 Internet Archive.
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
package org.archive.io.arc;

import java.util.Arrays;
import java.util.List;
import java.util.zip.Deflater;
import java.util.zip.GZIPInputStream;

import org.archive.io.ArchiveFileConstants;
import org.archive.io.GzipHeader;

/**
 * Constants used by ARC files and in ARC file processing.
 * 
 * @author stack
 */
public interface ARCConstants extends ArchiveFileConstants {
    /**
     * Default maximum ARC file size.
     */
    public static final long DEFAULT_MAX_ARC_FILE_SIZE = 100000000;
    
    /**
     * Maximum length for a metadata line.
     */
    public static final int MAX_METADATA_LINE_LENGTH = (4 * 1024);

    /**
     * ARC file extention.
     */
    public static final String ARC_FILE_EXTENSION = "arc";
    
    /**
     * Dot ARC file extension.
     */
    public static final String DOT_ARC_FILE_EXTENSION =
        "." + ARC_FILE_EXTENSION;
    
    public static final String DOT_COMPRESSED_FILE_EXTENSION =
        ArchiveFileConstants.DOT_COMPRESSED_FILE_EXTENSION;

    /**
     * Compressed arc file extension.
     */
    public static final String COMPRESSED_ARC_FILE_EXTENSION =
        ARC_FILE_EXTENSION + DOT_COMPRESSED_FILE_EXTENSION;
    
    /**
     * Compressed dot arc file extension.
     */
    public static final String DOT_COMPRESSED_ARC_FILE_EXTENSION =
        DOT_ARC_FILE_EXTENSION + DOT_COMPRESSED_FILE_EXTENSION;

    /**
     * Encoding to use getting bytes from strings.
     *
     * Specify an encoding rather than leave it to chance: i.e whatever the
     * JVMs encoding.  Use an encoding that gets the stream as bytes, not chars.
     */
    public static final String DEFAULT_ENCODING = "ISO-8859-1";

    /**
     * ARC file line seperator character.
     * 
     * This is what the alexa c-code looks for delimiting lines.
     */
    public static final char LINE_SEPARATOR = '\n';

    /**
     * ARC header field seperator character.
     */
    public static final char HEADER_FIELD_SEPARATOR = ' ';

    /**
     * ARC file *MAGIC NUMBER*.
     * 
     * Every ARC file must begin w/ this.
     */
    public static final String ARC_MAGIC_NUMBER = "filedesc://";

    /**
     * The FLG.FEXTRA field that is added to ARC files. (See RFC1952 to
     * understand FLG.FEXTRA).
     */
    public static final byte[] ARC_GZIP_EXTRA_FIELD = { 8, 0, 'L', 'X', 4, 0,
            0, 0, 0, 0 };

    /**
     * Key for the ARC Header IP field.
     * 
     * Lowercased.
     */
    public static final String IP_HEADER_FIELD_KEY = "ip-address";

    /**
     * Key for the ARC Header Result Code field.
     * 
     * Lowercased.
     */
    public static final String CODE_HEADER_FIELD_KEY = "result-code";

    /**
     * Key for the ARC Header Checksum field.
     * 
     * Lowercased.
     */
    public static final String CHECKSUM_HEADER_FIELD_KEY = "checksum";

    /**
     * Key for the ARC Header Location field.
     * 
     * Lowercased.
     */
    public static final String LOCATION_HEADER_FIELD_KEY = "location";

    /**
     * Key for the ARC Header Offset field.
     * 
     * Lowercased.
     */
    public static final String OFFSET_HEADER_FIELD_KEY = "offset";

    /**
     * Key for the ARC Header filename field.
     * 
     * Lowercased.
     */
    public static final String FILENAME_HEADER_FIELD_KEY = "filename";
    
    /**
     * Key for statuscode field.
     */
    public static final String STATUSCODE_FIELD_KEY = "statuscode";
    
    /**
     * Key for offset field.
     */
    public static final String OFFSET_FIELD_KEY = OFFSET_HEADER_FIELD_KEY;
    
    /**
     * Key for filename field.
     */
    public static final String FILENAME_FIELD_KEY = FILENAME_HEADER_FIELD_KEY;
    
    /**
     * Key for checksum field.
     */
    public static final String CHECKSUM_FIELD_KEY = CHECKSUM_HEADER_FIELD_KEY;
    
    /**
     * Tokenized field prefix.
     * 
     * Use this prefix for tokenized fields  when naming fields in
     * an index.
     */
    public static final String TOKENIZED_PREFIX = "tokenized_";

    /**
     * Assumed maximum size of a record meta header line.
     *
     * This 100k which seems massive but its the same as the LINE_LENGTH from
     * <code>alexa/include/a_arcio.h</code>:
     * <pre>
     * #define LINE_LENGTH     (100*1024)
     * </pre>
     */
    public static final int MAX_HEADER_LINE_LENGTH = 1024 * 100;

    /**
     * Version 1 required metadata fields.
     */
    public static List<String> REQUIRED_VERSION_1_HEADER_FIELDS = Arrays
            .asList(new String[] { URL_FIELD_KEY, IP_HEADER_FIELD_KEY,
                    DATE_FIELD_KEY, MIMETYPE_FIELD_KEY,
                    LENGTH_FIELD_KEY, VERSION_FIELD_KEY,
                    ABSOLUTE_OFFSET_KEY });

    /**
     * Minimum possible record length.
     * 
     * This is a rough calc. When the header is data it will occupy less space.
     */
    public static int MINIMUM_RECORD_LENGTH = 1 + "://".length() + 1
            + ARC_FILE_EXTENSION.length() + " ".length() + +1 + " ".length()
            + 1 + " ".length() + 1 + "/".length() + 1 + " ".length() + 1;

    /**
     * Start of a GZIP header that uses default deflater.
     */
    public static final byte[] GZIP_HEADER_BEGIN = {
            (byte) GZIPInputStream.GZIP_MAGIC, // Magic number (short)
            (byte) (GZIPInputStream.GZIP_MAGIC >> 8), // Magic number (short)
            Deflater.DEFLATED // Compression method (CM)
    };

    /**
     * Length of minimual 'default GZIP header.
     * 
     * See RFC1952 for explaination of value of 10.
     */
    public static final int DEFAULT_GZIP_HEADER_LENGTH =
        GzipHeader.MINIMAL_GZIP_HEADER_LENGTH;
}
