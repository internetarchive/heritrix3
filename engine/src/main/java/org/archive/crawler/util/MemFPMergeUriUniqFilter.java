/* MemFPMergeUriUniqFilter
*
* $Id$
*
* Created on Dec 14, 2005
*
* Copyright (C) 2005 Internet Archive.
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
package org.archive.crawler.util;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongIterator;

/**
 * Crude all-in-memory FP-merging UriUniqFilter. 
 * 
 * @author gojomo
 */
public class MemFPMergeUriUniqFilter extends FPMergeUriUniqFilter {
    protected LongArrayList allFps = new LongArrayList();
    protected LongArrayList newFps;
    
    /* (non-Javadoc)
     * @see org.archive.crawler.util.FPMergeUriUniqFilter#beginFpMerge()
     */
    protected LongIterator beginFpMerge() {
        newFps = new LongArrayList((int) (allFps.size()+(pending()/2)));
        return allFps.iterator();
    }

    /* (non-Javadoc)
     * @see org.archive.crawler.util.FPMergeUriUniqFilter#addNewFp(java.lang.Long)
     */
    protected void addNewFp(long currFp) {
        newFps.add(currFp);
    }

    /* (non-Javadoc)
     * @see org.archive.crawler.util.FPMergeUriUniqFilter#finishFpMerge()
     */
    protected void finishFpMerge() {
        allFps = newFps;
        newFps = null; 
    }

    /* (non-Javadoc)
     * @see org.archive.crawler.datamodel.UriUniqFilter#count()
     */
    public long count() {
        return allFps.size();
    }

}
