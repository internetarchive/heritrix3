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
