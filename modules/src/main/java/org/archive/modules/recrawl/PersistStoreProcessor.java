/* PersistStoreProcessor
 * 
 * Created on Feb 12, 2005
 *
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
 */
package org.archive.modules.recrawl;

import org.archive.modules.ProcessorURI;
/**
 * Store CrawlURI attributes from latest fetch to persistent storage for
 * consultation by a later recrawl. 
 * 
 * @author gojomo
 * @version $Date: 2006-09-25 20:19:54 +0000 (Mon, 25 Sep 2006) $, $Revision: 4654 $
 */ 
public class PersistStoreProcessor extends PersistOnlineProcessor 
{
    private static final long serialVersionUID = -8308356194337303758L;

//    class description: "PersistStoreProcessor. Stores CrawlURI attributes " +
//    "from latest fetch for consultation by a later recrawl."

    public PersistStoreProcessor() {
    }
    
    @SuppressWarnings("unchecked")
    @Override
    protected void innerProcess(ProcessorURI curi) throws InterruptedException {
        store.put(persistKeyFor(curi),curi.getPersistentDataMap());
    }

    @Override
    protected boolean shouldProcess(ProcessorURI uri) {
        return shouldStore(uri);
    }
}
