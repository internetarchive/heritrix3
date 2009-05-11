/* FixupQueryStr
 * 
 * Created on Oct 5, 2004
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
package org.archive.modules.canonicalize;

/**
 * Strip any trailing question mark.
 * @author stack
 * @version $Date$, $Revision$
 */
public class FixupQueryString
extends BaseRule {

    private static final long serialVersionUID = 3L;

    /*
    private static final String DESCRIPTION =
        "Fixup the question mark that leads off the query string. " +
        "This rule returns 'http://www.archive.org/index.html' if passed" +
        " 'http://www.archive.org/index.html?'.  It will also strip '?&'" +
        " if '?&' is all that comprises the query string.  Also strips" +
        " extraneous leading '&': Returns 'http://archive.org/index.html?x=y" +
        " if passed 'http://archive.org/index.html?&x=y." +
        " Will also strip '&' if last thing in query string." +
        " Operates on all schemes.  This is a good rule to run toward the" +
        " end of canonicalization processing.";
        */
    
    public FixupQueryString() {
    }

    public String canonicalize(String url) {
        if (url == null || url.length() <= 0) {
            return url;
        }
        
        int index = url.lastIndexOf('?');
        if (index > 0) {
            if (index == (url.length() - 1)) {
                // '?' is last char in url.  Strip it.
                url = url.substring(0, url.length() - 1);
            } else if (url.charAt(index + 1) == '&') {
                // Next char is '&'. Strip it.
                if (url.length() == (index + 2)) {
                    // Then url ends with '?&'.  Strip them.
                    url = url.substring(0, url.length() - 2);
                } else {
                    // The '&' is redundant.  Strip it.
                    url = url.substring(0, index + 1) +
                    url.substring(index + 2);
                }
            } else if (url.charAt(url.length() - 1) == '&') {
                // If we have a lone '&' on end of query str,
                // strip it.
                url = url.substring(0, url.length() - 1);
            }
        }
        return url;
    }
}
