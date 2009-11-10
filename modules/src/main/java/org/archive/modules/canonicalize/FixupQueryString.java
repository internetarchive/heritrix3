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
