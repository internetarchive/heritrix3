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
package org.archive.modules.fetcher;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;

import org.apache.commons.io.FileUtils;
import org.apache.http.cookie.Cookie;
import org.apache.http.impl.cookie.BasicClientCookie;
import org.archive.bdb.BdbModule;
import org.archive.spring.ConfigPath;
import org.archive.util.TmpDirTestCase;

/**
 * @contributor nlevitt
 */
public class BdbCookieStoreTest extends TmpDirTestCase {

    private static Logger logger = Logger.getLogger(BdbCookieStoreTest.class.getName());
    
    protected BdbModule bdb;
    protected BdbCookieStore bdbCookieStore;
    protected SimpleCookieStore simpleCookieStore;
    
    protected BdbModule bdb() throws IOException {
        if (bdb == null) {
            ConfigPath basePath = new ConfigPath("testBase", 
                    getTmpDir().getAbsolutePath());
            ConfigPath bdbDir = new ConfigPath("bdb", "bdb");
            bdbDir.setBase(basePath); 
            FileUtils.deleteDirectory(bdbDir.getFile());

            bdb = new BdbModule();
            bdb.setDir(bdbDir);
            bdb.start();
            logger.info("created " + bdb);
        }
        return bdb;
    }

    protected BdbCookieStore bdbCookieStore() throws IOException {
        if (bdbCookieStore == null) {
            bdbCookieStore = new BdbCookieStore();
            bdbCookieStore.setBdbModule(bdb());
            bdbCookieStore.start();
        }
        return bdbCookieStore;
    }
    
    protected SimpleCookieStore simpleCookieStore() {
        if (simpleCookieStore == null) {
            simpleCookieStore = new SimpleCookieStore();
            simpleCookieStore.start();
        }
        return simpleCookieStore;
    }
    
    public void testCookies() throws IOException {
        bdbCookieStore().clear();
        simpleCookieStore().clear();
        assertEquals(0, bdbCookieStore().getCookies().size());
        assertEquals(0, simpleCookieStore().getCookies().size());
        
        BasicClientCookie cookie1 = new BasicClientCookie("name1", "value1");
        bdbCookieStore().addCookie(cookie1);
        simpleCookieStore().addCookie(cookie1);
        assertEquals(1, bdbCookieStore().getCookies().size());
        assertEquals(1, simpleCookieStore().getCookies().size());
        assertCookieListsIdentical(simpleCookieStore().getCookies(), bdbCookieStore().getCookies());
    }

    protected void assertCookieListsIdentical(List<Cookie> list1,
            List<Cookie> list2) {
        assertEquals(list1.size(), list2.size());
        for (int i = 0; i < list1.size(); i++) {
            Cookie c1 = list1.get(i);
            Cookie c2 = list2.get(i);
            assertCookiesIdentical(c1, c2);
        }
    }

    protected void assertCookiesIdentical(Cookie c1, Cookie c2) {
        assertEquals(c1.getComment(), c2.getComment());
        assertEquals(c1.getCommentURL(), c2.getCommentURL());
        assertEquals(c1.getDomain(), c2.getDomain());
        assertEquals(c1.getName(), c2.getName());
        assertEquals(c1.getPath(), c2.getPath());
        assertEquals(c1.getValue(), c2.getValue());
        assertEquals(c1.getVersion(), c2.getVersion());
        assertEquals(c1.getExpiryDate(), c2.getExpiryDate());
        assertTrue(Arrays.equals(c1.getPorts(), c2.getPorts()));
    }

}
