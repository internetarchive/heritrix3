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
package org.archive.modules.extractor;

import java.util.logging.Logger;

import org.archive.modules.CrawlURI;

/**
 * Extended version of ExtractorHTML with more aggressive javascript link
 * extraction where javascript code is parsed first with general HTML tags
 * regex, and than by javascript speculative link regex.
 *
 * @author Igor Ranitovic
 *
 */
public class AggressiveExtractorHTML
extends ExtractorHTML {
    @SuppressWarnings("unused")
    private static final long serialVersionUID = 3L;

    protected static Logger logger =
        Logger.getLogger(AggressiveExtractorHTML.class.getName());
    
    public AggressiveExtractorHTML() {
    }

    protected void processScript(CrawlURI curi, CharSequence sequence,
            int endOfOpenTag) {
        super.processScript(curi, sequence, endOfOpenTag);
        // then, proccess entire javascript code as html code
        // this may cause a lot of false positves
        processGeneralTag(curi, sequence.subSequence(0,6),
            sequence.subSequence(endOfOpenTag, sequence.length()));
    }
}
