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
 
package org.archive.crawler.util;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.logging.Logger;

import junit.framework.TestCase;

import org.apache.commons.httpclient.URIException;
import org.archive.crawler.datamodel.UriUniqFilter;
import org.archive.modules.CrawlURI;
import org.archive.net.UURI;
import org.archive.net.UURIFactory;
import org.archive.util.fingerprint.MemLongFPSet;


/**
 * Test FPUriUniqFilter.
 * @author stack
 */
public class FPUriUniqFilterTest extends TestCase
implements UriUniqFilter.CrawlUriReceiver {
    private Logger logger =
        Logger.getLogger(FPUriUniqFilterTest.class.getName());

    private UriUniqFilter filter = null;
    
    /**
     * Set to true if we visited received.
     */
    private boolean received = false;
    
	protected void setUp() throws Exception {
		super.setUp();
        // 17 makes a MemLongFPSet of one meg of longs (64megs).
		this.filter = new FPUriUniqFilter(new MemLongFPSet(10, 0.75f));
		this.filter.setDestination(this);
    }
    
    public void testAdding() throws URIException {
        this.filter.add(this.getUri(),
            new CrawlURI(UURIFactory.getInstance(this.getUri())));
        this.filter.addNow(this.getUri(),
            new CrawlURI(UURIFactory.getInstance(this.getUri())));
        this.filter.addForce(this.getUri(),
            new CrawlURI(UURIFactory.getInstance(this.getUri())));
        // Should only have add 'this' once.
        assertTrue("Count is off", this.filter.count() == 1);
    }
    
    /**
     * Test inserting and removing.
     * @throws IOException
     * @throws FileNotFoundException
     */
    public void testWriting() throws FileNotFoundException, IOException {
        long start = System.currentTimeMillis();
        ArrayList<UURI> list = new ArrayList<UURI>(1000);
        int count = 0;
        final int MAX_COUNT = 1000;
        for (; count < MAX_COUNT; count++) {
        	UURI u = UURIFactory.getInstance("http://www" +
        			count + ".archive.org/" + count + "/index.html");
        	this.filter.add(u.toString(), new CrawlURI(u));
        	if (count > 0 && ((count % 100) == 0)) {
        		list.add(u);
        	}
        }
        this.logger.info("Added " + count + " in " +
        		(System.currentTimeMillis() - start));
        
        start = System.currentTimeMillis();
        for (Iterator<UURI> i = list.iterator(); i.hasNext();) {
            UURI uuri = i.next();
            this.filter.add(uuri.toString(), new CrawlURI(uuri));
        }
        this.logger.info("Added random " + list.size() + " in " +
        		(System.currentTimeMillis() - start));
        
        start = System.currentTimeMillis();
        for (Iterator<UURI> i = list.iterator(); i.hasNext();) {
            UURI uuri = i.next();
            this.filter.add(uuri.toString(), new CrawlURI(uuri));
        }
        this.logger.info("Deleted random " + list.size() + " in " +
            (System.currentTimeMillis() - start));
        // Looks like delete doesn't work.
        assertTrue("Count is off: " + this.filter.count(),
            this.filter.count() == MAX_COUNT);
    }
    
    public void testNote() {
    	this.filter.note(this.getUri());
        assertFalse("Receiver was called", this.received);
    }
    
    public void testForget() throws URIException {
        this.filter.forget(this.getUri(),
                new CrawlURI(UURIFactory.getInstance(this.getUri())));
        assertTrue("Didn't forget", this.filter.count() == 0);
    }
    
	public void receive(CrawlURI item) {
		this.received = true;
	}

	public String getUri() {
		return "http://www.archive.org";
	}
}
