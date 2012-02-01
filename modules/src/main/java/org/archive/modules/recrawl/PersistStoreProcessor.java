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

package org.archive.modules.recrawl;

import org.archive.modules.CrawlURI;
/**
 * Store CrawlURI attributes from latest fetch to persistent storage for
 * consultation by a later recrawl. 
 * 
 * @author gojomo
 * @version $Date: 2006-09-25 20:19:54 +0000 (Mon, 25 Sep 2006) $, $Revision: 4654 $
 */ 
public class PersistStoreProcessor extends PersistOnlineProcessor 
{
    @SuppressWarnings("unused")
    private static final long serialVersionUID = -8308356194337303758L;

//    class description: "PersistStoreProcessor. Stores CrawlURI attributes " +
//    "from latest fetch for consultation by a later recrawl."

    public PersistStoreProcessor() {
    }
    
    @Override
    protected void innerProcess(CrawlURI curi) throws InterruptedException {
        store.put(persistKeyFor(curi),curi.getPersistentDataMap());
    }

    @Override
    protected boolean shouldProcess(CrawlURI uri) {
        return shouldStore(uri);
    }
}
