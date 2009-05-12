/*
 * AggressiveExtractorHTML
 *
 * $Id$
 *
 * Created on Jan 6, 2004
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

package org.archive.modules.extractor;

import java.util.logging.Logger;

import org.archive.modules.ProcessorURI;

/**
 * Extended version of ExtractorHTML with more aggressive javascript link
 * extraction where javascript code is parsed first with general HTML tags
 * regexp, and than by javascript speculative link regexp.
 *
 * @author Igor Ranitovic
 *
 */
public class AggressiveExtractorHTML
extends ExtractorHTML {
    private static final long serialVersionUID = 3L;

    static Logger logger =
        Logger.getLogger(AggressiveExtractorHTML.class.getName());
    
    public AggressiveExtractorHTML() {
    }

    protected void processScript(ProcessorURI curi, CharSequence sequence,
            int endOfOpenTag) {
        super.processScript(curi, sequence, endOfOpenTag);
        // then, proccess entire javascript code as html code
        // this may cause a lot of false positves
        processGeneralTag(curi, sequence.subSequence(0,6),
            sequence.subSequence(endOfOpenTag, sequence.length()));
    }

    /* (non-Javadoc)
     * @see org.archive.crawler.framework.Processor#report()
     */
    public String report() {
        StringBuffer ret = new StringBuffer(256);
        ret.append("Processor: "+AggressiveExtractorHTML.class.getName()+"\n");
        ret.append("  Function:          Link extraction on HTML documents " +
            "(including embedded CSS)\n");
        ret.append("  ProcessorURRIs handled: " + numberOfCURIsHandled + "\n");
        ret.append("  Links extracted:   " + numberOfLinksExtracted + "\n\n");
        return ret.toString();
    }
}
