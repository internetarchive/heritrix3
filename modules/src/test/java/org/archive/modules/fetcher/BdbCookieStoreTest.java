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
import java.util.Date;
import java.util.List;
import java.util.logging.Logger;

import org.apache.commons.io.FileUtils;
import org.apache.http.cookie.Cookie;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.cookie.BasicClientCookie;
import org.archive.bdb.BdbModule;
import org.archive.spring.ConfigFile;
import org.archive.spring.ConfigPath;
import org.archive.util.TmpDirTestCase;

/**
 * Tests that BdbCookieStore matches behavior of SimpleCookieStore, which uses
 * the reference implementation {@link BasicCookieStore} under the hood.
 * 
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
            ConfigPath basePath = new ConfigPath("testBase", 
                    getTmpDir().getAbsolutePath());
            ConfigFile cookiesSaveFile = new ConfigFile("cookiesSaveFile", "cookies.txt");
            cookiesSaveFile.setBase(basePath);
            bdbCookieStore.setCookiesSaveFile(cookiesSaveFile);
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
    
    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        bdb.close();
    }
    
    public void testBasics() throws IOException {
        bdbCookieStore().clear();
        simpleCookieStore().clear();
        assertCookieListsIdentical(simpleCookieStore().getCookies(), bdbCookieStore().getCookies());
        
        BasicClientCookie cookie = new BasicClientCookie("name1", "value1");
        bdbCookieStore().addCookie(cookie);
        simpleCookieStore().addCookie(cookie);
        assertCookieListsIdentical(simpleCookieStore().getCookies(), bdbCookieStore().getCookies());
    }

    public void testSimpleReplace() throws IOException {
        bdbCookieStore().clear();
        simpleCookieStore().clear();
        assertCookieListsIdentical(simpleCookieStore().getCookies(), bdbCookieStore().getCookies());
        
        BasicClientCookie cookie = new BasicClientCookie("name1", "value1");
        bdbCookieStore().addCookie(cookie);
        simpleCookieStore().addCookie(cookie);
        assertCookieListsIdentical(simpleCookieStore().getCookies(), bdbCookieStore().getCookies());
        
        // should replace existing cookie
        cookie = new BasicClientCookie("name1", "value2");
        bdbCookieStore().addCookie(cookie);
        simpleCookieStore().addCookie(cookie);
        assertCookieListsIdentical(simpleCookieStore().getCookies(), bdbCookieStore().getCookies());
    }
    
    public void testDomains() throws IOException {
        bdbCookieStore().clear();
        simpleCookieStore().clear();
        assertCookieListsIdentical(simpleCookieStore().getCookies(), bdbCookieStore().getCookies());

        BasicClientCookie cookie = new BasicClientCookie("name1", "value1");
        cookie.setDomain("example.org");
        bdbCookieStore().addCookie(cookie);
        simpleCookieStore().addCookie(cookie);
        assertCookieListsIdentical(simpleCookieStore().getCookies(), bdbCookieStore().getCookies());
        
        // add a 2nd cookie, same name, different domain
        cookie = new BasicClientCookie("name1", "value2");
        cookie.setDomain("example.com");
        bdbCookieStore().addCookie(cookie);
        simpleCookieStore().addCookie(cookie);
        assertCookieListsIdentical(simpleCookieStore().getCookies(), bdbCookieStore().getCookies());
        
        // add a 3rd cookie, same name, different domain
        cookie = new BasicClientCookie("name1", "value3");
        cookie.setDomain("foo.example.com");
        bdbCookieStore().addCookie(cookie);
        simpleCookieStore().addCookie(cookie);
        assertCookieListsIdentical(simpleCookieStore().getCookies(), bdbCookieStore().getCookies());
        
        // replace 1st cookie
        cookie = new BasicClientCookie("name1", "value4");
        cookie.setDomain("example.org");
        bdbCookieStore().addCookie(cookie);
        simpleCookieStore().addCookie(cookie);
        assertCookieListsIdentical(simpleCookieStore().getCookies(), bdbCookieStore().getCookies());
        
        // replace 2nd cookie, case-insensitive domain
        cookie = new BasicClientCookie("name1", "value5");
        cookie.setDomain("eXaMpLe.CoM");
        bdbCookieStore().addCookie(cookie);
        simpleCookieStore().addCookie(cookie);
        assertCookieListsIdentical(simpleCookieStore().getCookies(), bdbCookieStore().getCookies());
    }

    public void testPaths() throws IOException {
        bdbCookieStore().clear();
        simpleCookieStore().clear();
        assertCookieListsIdentical(simpleCookieStore().getCookies(), bdbCookieStore().getCookies());

        BasicClientCookie cookie = new BasicClientCookie("name1", "value1");
        bdbCookieStore().addCookie(cookie);
        simpleCookieStore().addCookie(cookie);
        assertCookieListsIdentical(simpleCookieStore().getCookies(), bdbCookieStore().getCookies());
        
        // replace 1st cookie, with explicit path "/", which is the implied path if not specified
        cookie = new BasicClientCookie("name1", "value2");
        cookie.setPath("/");
        bdbCookieStore().addCookie(cookie);
        simpleCookieStore().addCookie(cookie);
        assertEquals(1, simpleCookieStore().getCookies().size());
        assertEquals(cookie, simpleCookieStore().getCookies().get(0));
        assertCookieListsIdentical(simpleCookieStore().getCookies(), bdbCookieStore().getCookies());
        
        // add a 2nd cookie at a subpath
        cookie = new BasicClientCookie("name1", "value3");
        cookie.setPath("/path1");
        bdbCookieStore().addCookie(cookie);
        simpleCookieStore().addCookie(cookie);
        assertEquals(2, simpleCookieStore().getCookies().size());
        assertCookieListsIdentical(simpleCookieStore().getCookies(), bdbCookieStore().getCookies());
        
        // add a 3rd cookie at a subpath
        cookie = new BasicClientCookie("name1", "value4");
        cookie.setPath("/path2");
        bdbCookieStore().addCookie(cookie);
        simpleCookieStore().addCookie(cookie);
        assertEquals(3, simpleCookieStore().getCookies().size());
        assertCookieListsIdentical(simpleCookieStore().getCookies(), bdbCookieStore().getCookies());
        
        // replace 2nd cookie
        cookie = new BasicClientCookie("name1", "value5");
        cookie.setPath("/path1");
        bdbCookieStore().addCookie(cookie);
        simpleCookieStore().addCookie(cookie);
        assertEquals(3, simpleCookieStore().getCookies().size());
        assertCookieListsIdentical(simpleCookieStore().getCookies(), bdbCookieStore().getCookies());

        // add 4th cookie at previously used path
        cookie = new BasicClientCookie("name2", "value6");
        cookie.setPath("/path1");
        bdbCookieStore().addCookie(cookie);
        simpleCookieStore().addCookie(cookie);
        assertEquals(4, simpleCookieStore().getCookies().size());
        assertCookieListsIdentical(simpleCookieStore().getCookies(), bdbCookieStore().getCookies());
        
        // add 5th cookie at different path (case sensitive)
        cookie = new BasicClientCookie("name1", "value7");
        cookie.setPath("/pAtH1");
        bdbCookieStore().addCookie(cookie);
        simpleCookieStore().addCookie(cookie);
        assertEquals(5, simpleCookieStore().getCookies().size());
        assertCookieListsIdentical(simpleCookieStore().getCookies(), bdbCookieStore().getCookies());
    }

    /*
     * saveCookies() expects non-null domain, and real world cookies always have
     * a domain. And only test attributes that are saved in cookies.txt.
     */
    public void testSaveLoadCookies() throws IOException {
        bdbCookieStore().clear();

        BasicClientCookie cookie = new BasicClientCookie("name1", "value1");
        cookie.setDomain("example.com");
        bdbCookieStore().addCookie(cookie);

        cookie = new BasicClientCookie("name2", "value2");
        cookie.setDomain("example.com");
        bdbCookieStore().addCookie(cookie);
        
        cookie = new BasicClientCookie("name3", "value3");
        cookie.setDomain("example.com");
        cookie.setSecure(true);
        bdbCookieStore().addCookie(cookie);
        
        cookie = new BasicClientCookie("name4", "value4");
        cookie.setDomain("example.org");
        bdbCookieStore().addCookie(cookie);
        
        cookie = new BasicClientCookie("name5", "value5");
        cookie.setDomain("example.com");
        // make sure date is in the future so cookie doesn't expire, and has no
        // millisecond value, so save/load loses no info
        long someFutureDateMs = ((System.currentTimeMillis() + 9999999999l)/1000l)*1000l;
        cookie.setExpiryDate(new Date(someFutureDateMs));
        bdbCookieStore().addCookie(cookie);
        
        cookie = new BasicClientCookie("name6", "value6");
        cookie.setDomain("example.com");
        cookie.setPath("/path1");
        bdbCookieStore().addCookie(cookie);
        
        assertEquals(6, bdbCookieStore().getCookies().size());
        List<Cookie> cookiesBefore = bdbCookieStore().getCookies();
        bdbCookieStore().saveCookies();
        
        bdbCookieStore().clear();
        assertEquals(0, bdbCookieStore().getCookies().size());
        
        bdbCookieStore().loadCookies((ConfigFile) bdbCookieStore().getCookiesSaveFile());
        assertEquals(6, bdbCookieStore().getCookies().size());
        logger.info("before: " + cookiesBefore);
        logger.info(" after: " + bdbCookieStore().getCookies());
        assertCookieListsIdentical(cookiesBefore, bdbCookieStore().getCookies());
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
        String p1 = c1.getPath() != null ? c1.getPath() : "/";
        String p2 = c2.getPath() != null ? c2.getPath() : "/";
        assertEquals(p1, p2);
        assertEquals(c1.getValue(), c2.getValue());
        assertEquals(c1.getVersion(), c2.getVersion());
        assertEquals(c1.getExpiryDate(), c2.getExpiryDate());
        assertTrue(Arrays.equals(c1.getPorts(), c2.getPorts()));
    }

}
