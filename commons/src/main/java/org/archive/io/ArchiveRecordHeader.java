/* $Id$
 *
 * Created on August 21st, 2006
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

import java.util.Map;
import java.util.Set;

/**
 * Archive Record Header.
 * @author stack
 * @version $Date$ $Version$
 */
public interface ArchiveRecordHeader {
    /**
     * Get the time when the record was created.
     * @return Date in 14 digit time format (UTC).
     * @see org.archive.util.ArchiveUtils#parse14DigitDate(String)
     */
    public abstract String getDate();

    /**
     * @return Return length of record.
     */
    public abstract long getLength();

    /**
     * @return Record subject-url.
     */
    public abstract String getUrl();

    /**
     * @return Record mimetype.
     */
    public abstract String getMimetype();

    /**
     * @return Record version.
     */
    public abstract String getVersion();

    /**
     * @return Offset into Archive file at which this record begins.
     */
    public abstract long getOffset();

    /**
     * @param key Key to use looking up field value.
     * @return value for passed key of null if no such entry.
     */
    public abstract Object getHeaderValue(final String key);

    /**
     * @return Header field name keys.
     */
    public abstract Set<String> getHeaderFieldKeys();

    /**
     * @return Map of header fields.
     */
    public abstract Map<String,Object> getHeaderFields();

    /**
     * @return Returns identifier for current Archive file.  Be aware this
     * may not be a file name or file path.  It may just be an URL.  Depends
     * on how Archive file was made.
     */
    public abstract String getReaderIdentifier();
    
    /**
     * @return Identifier for the record.  If ARC, the URL + date.  If WARC, 
     * the GUID assigned.
     */
    public abstract String getRecordIdentifier();
    
    /**
     * @return Returns digest as String for this record. Only available after
     * the record has been read in totality.
     */
    public abstract String getDigest();

    /**
     * Offset at which the content begins.
     * For ARCs, its used to delimit where http headers end and content begins.
     * For WARCs, its end of Named Fields before payload starts.
     */
    public int getContentBegin();

    public abstract String toString();
}