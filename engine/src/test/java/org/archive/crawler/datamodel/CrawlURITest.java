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
package org.archive.crawler.datamodel;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import org.apache.commons.httpclient.URIException;
import org.archive.modules.CrawlURI;
import org.archive.modules.SchedulingConstants;
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
