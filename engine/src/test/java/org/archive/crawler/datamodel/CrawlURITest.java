/* CrawlURITest
 * 
 * Created on Jul 26, 2004
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
package org.archive.crawler.datamodel;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import org.apache.commons.httpclient.URIException;
import org.archive.net.UURI;
import org.archive.net.UURIFactory;
import org.archive.util.TmpDirTestCase;

/**
 * @author stack
 * @version $Revision$, $Date$
 */
public class CrawlURITest extends TmpDirTestCase {
    
    CrawlURI seed = null;
    
    protected void setUp() throws Exception {
        super.setUp();
        final String url = "http://www.dh.gov.uk/Home/fs/en";
        this.seed = new CrawlURI(UURIFactory.getInstance(url));
        this.seed.setSchedulingDirective(SchedulingConstants.MEDIUM);
        this.seed.setSeed(true);
        // Force caching of string.
        this.seed.toString();
        // TODO: should this via really be itself?
        this.seed.setVia(UURIFactory.getInstance(url));
    }

    /**
     * Test serialization/deserialization works.
     * 
     * @throws IOException
     * @throws ClassNotFoundException
     */
    final public void testSerialization()
    		throws IOException, ClassNotFoundException {
        File serialize = new File(getTmpDir(), 
            this.getClass().getName() + ".serialize");
        try {
            FileOutputStream fos = new FileOutputStream(serialize);
            ObjectOutputStream oos = new ObjectOutputStream(fos);
            oos.writeObject(this.seed);
            oos.reset();
            oos.writeObject(this.seed);
            oos.reset();
            oos.writeObject(this.seed);
            oos.close();
            // Read in the object.
            FileInputStream fis = new FileInputStream(serialize);
            ObjectInputStream ois = new ObjectInputStream(fis);
            CrawlURI deserializedCuri = (CrawlURI)ois.readObject();
            deserializedCuri = (CrawlURI)ois.readObject();
            deserializedCuri = (CrawlURI)ois.readObject();
            assertEquals("Deserialized not equal to original",
                this.seed.toString(), deserializedCuri.toString());
            String host = this.seed.getUURI().getHost();
            assertTrue("Deserialized host not null",
                host != null && host.length() >= 0);
        } finally {
            serialize.delete();
        }
    }
    
    public void testCandidateURIWithLoadedAList()
    throws URIException {
        UURI uuri = UURIFactory.getInstance("http://www.archive.org");
        CrawlURI c = new CrawlURI(uuri);
        c.setSeed(true);
        c.getData().put("key", "value");
        CrawlURI curi = new CrawlURI(c, 0);
        assertTrue("Didn't find AList item",
            curi.getData().get("key").equals("value"));
    }
    
// TODO: move to QueueAssignmentPolicies
//    public void testCalculateClassKey() throws URIException {
//        final String uri = "http://mprsrv.agri.gov.cn";
//        CrawlURI curi = new CrawlURI(UURIFactory.getInstance(uri));
//        String key = curi.getClassKey();
//        assertTrue("Key1 is bad " + key,
//            key.equals(curi.getUURI().getAuthorityMinusUserinfo()));
//    	final String baduri = "ftp://pfbuser:pfbuser@mprsrv.agri.gov.cn/clzreceive/";
//        curi = new CrawlURI(UURIFactory.getInstance(baduri));
//        key = curi.getClassKey();
//        assertTrue("Key2 is bad " + key,
//            key.equals(curi.getUURI().getAuthorityMinusUserinfo()));
//	}
}
