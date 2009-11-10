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