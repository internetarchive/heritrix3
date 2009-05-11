/* 
 * Copyright (C) 2007 Internet Archive.
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
 *
 * RecrawlAttributeConstants.java
 *
 * Created on May 16, 2007
 *
 * $Id:$
 */

package org.archive.modules.recrawl;

/**
 * 
 * @author pjack
 *
 */
public interface RecrawlAttributeConstants {

    /* Duplication-reduction / recrawl / history constants */
    
    /** fetch history array */ 
    public static final String A_FETCH_HISTORY = "fetch-history";
    /** content digest */
    public static final String A_CONTENT_DIGEST = "content-digest";
        /** header name (and AList key) for last-modified timestamp */
    public static final String A_LAST_MODIFIED_HEADER = "last-modified";
        /** header name (and AList key) for ETag */
    public static final String A_ETAG_HEADER = "etag"; 
    /** key for status (when in history) */
    public static final String A_STATUS = "status"; 
    /** reference length (content length or virtual length */
    public static final String A_REFERENCE_LENGTH = "reference-length";

}
