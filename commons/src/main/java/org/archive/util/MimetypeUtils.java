/* MimetypeUtils
 * 
 * $Id$
 * 
 * Created on Sep 22, 2004
 *
 * Copyright (C) 2004 Internet Archive.
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
package org.archive.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Class of mimetype utilities.
 * @author stack
 */
public class MimetypeUtils {
    /**
     * The 'no-type' content-type.
     * 
     * Defined in the ARC file spec at
     * http://www.archive.org/web/researcher/ArcFileFormat.php.
     */
    public static final String NO_TYPE_MIMETYPE = "no-type";
    
    /**
     * Truncation regex.
     */
    final static Pattern TRUNCATION_REGEX = Pattern.compile("^([^\\s;,]+).*");


    /**
     * Truncate passed mimetype.
     * 
     * Ensure no spaces.  Strip encoding.  Truncation required by
     * ARC files.
     *
     * <p>Truncate at delimiters [;, ].
     * Truncate multi-part content type header at ';'.
     * Apache httpclient collapses values of multiple instances of the
     * header into one comma-separated value,therefore truncated at ','.
     * Current ia_tools that work with arc files expect 5-column
     * space-separated meta-lines, therefore truncate at ' '.
     *
     * @param contentType Raw content-type.
     *
     * @return Computed content-type made from passed content-type after
     * running it through a set of rules.
     */
    public static String truncate(String contentType) {
        if (contentType == null) {
            contentType = NO_TYPE_MIMETYPE;
        } else {
            Matcher matcher = TRUNCATION_REGEX.matcher(contentType);
            if (matcher.matches()) {
            	contentType = matcher.group(1);
            } else {
            	contentType = NO_TYPE_MIMETYPE;
            }
        }

        return contentType;
    }
}
