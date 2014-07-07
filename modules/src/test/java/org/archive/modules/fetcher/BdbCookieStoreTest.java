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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Iterator;
import java.util.UUID;
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
        assertCookieStoresEquivalent(simpleCookieStore(), bdbCookieStore());
        
        BasicClientCookie cookie = new BasicClientCookie("name1", "value1");
        bdbCookieStore().addCookie(cookie);
        simpleCookieStore().addCookie(cookie);
        assertCookieStoresEquivalent(simpleCookieStore(), bdbCookieStore());
    }

    public void testSimpleReplace() throws IOException {
        bdbCookieStore().clear();
        simpleCookieStore().clear();
        assertCookieStoresEquivalent(simpleCookieStore(), bdbCookieStore());
        
        BasicClientCookie cookie = new BasicClientCookie("name1", "value1");
        bdbCookieStore().addCookie(cookie);
        simpleCookieStore().addCookie(cookie);
        assertCookieStoresEquivalent(simpleCookieStore(), bdbCookieStore());
        
        // should replace existing cookie
        cookie = new BasicClientCookie("name1", "value2");
        bdbCookieStore().addCookie(cookie);
        simpleCookieStore().addCookie(cookie);
        assertCookieStoresEquivalent(simpleCookieStore(), bdbCookieStore());
    }
    
    public void testDomains() throws IOException {
        bdbCookieStore().clear();
        simpleCookieStore().clear();
        assertCookieStoresEquivalent(simpleCookieStore(), bdbCookieStore());

        BasicClientCookie cookie = new BasicClientCookie("name1", "value1");
        cookie.setDomain("example.org");
        bdbCookieStore().addCookie(cookie);
        simpleCookieStore().addCookie(cookie);
        assertCookieStoresEquivalent(simpleCookieStore(), bdbCookieStore());
        
        // add a 2nd cookie, same name, different domain
        cookie = new BasicClientCookie("name1", "value2");
        cookie.setDomain("example.com");
        bdbCookieStore().addCookie(cookie);
        simpleCookieStore().addCookie(cookie);
        assertCookieStoresEquivalent(simpleCookieStore(), bdbCookieStore());
        
        // add a 3rd cookie, same name, different domain
        cookie = new BasicClientCookie("name1", "value3");
        cookie.setDomain("foo.example.com");
        bdbCookieStore().addCookie(cookie);
        simpleCookieStore().addCookie(cookie);
        assertCookieStoresEquivalent(simpleCookieStore(), bdbCookieStore());
        
        // replace 1st cookie
        cookie = new BasicClientCookie("name1", "value4");
        cookie.setDomain("example.org");
        bdbCookieStore().addCookie(cookie);
        simpleCookieStore().addCookie(cookie);
        assertCookieStoresEquivalent(simpleCookieStore(), bdbCookieStore());
        
        // replace 2nd cookie, case-insensitive domain
        cookie = new BasicClientCookie("name1", "value5");
        cookie.setDomain("eXaMpLe.CoM");
        bdbCookieStore().addCookie(cookie);
        simpleCookieStore().addCookie(cookie);
        assertCookieStoresEquivalent(simpleCookieStore(), bdbCookieStore());
    }

    public void testPaths() throws IOException {
        bdbCookieStore().clear();
        simpleCookieStore().clear();
        assertCookieStoresEquivalent(simpleCookieStore(), bdbCookieStore());

        BasicClientCookie cookie = new BasicClientCookie("name1", "value1");
        bdbCookieStore().addCookie(cookie);
        simpleCookieStore().addCookie(cookie);
        assertCookieStoresEquivalent(simpleCookieStore(), bdbCookieStore());
        
        // replace 1st cookie, with explicit path "/", which is the implied path if not specified
        cookie = new BasicClientCookie("name1", "value2");
        cookie.setPath("/");
        bdbCookieStore().addCookie(cookie);
        simpleCookieStore().addCookie(cookie);
        assertEquals(1, simpleCookieStore().getCookies().size());
        assertEquals(cookie, new ArrayList<Cookie>(simpleCookieStore().getCookies()).get(0));
        assertCookieStoresEquivalent(simpleCookieStore(), bdbCookieStore());
        
        // add a 2nd cookie at a subpath
        cookie = new BasicClientCookie("name1", "value3");
        cookie.setPath("/path1");
        bdbCookieStore().addCookie(cookie);
        simpleCookieStore().addCookie(cookie);
        assertEquals(2, simpleCookieStore().getCookies().size());
        assertCookieStoresEquivalent(simpleCookieStore(), bdbCookieStore());
        
        // add a 3rd cookie at a subpath
        cookie = new BasicClientCookie("name1", "value4");
        cookie.setPath("/path2");
        bdbCookieStore().addCookie(cookie);
        simpleCookieStore().addCookie(cookie);
        assertEquals(3, simpleCookieStore().getCookies().size());
        assertCookieStoresEquivalent(simpleCookieStore(), bdbCookieStore());
        
        // replace 2nd cookie
        cookie = new BasicClientCookie("name1", "value5");
        cookie.setPath("/path1");
        bdbCookieStore().addCookie(cookie);
        simpleCookieStore().addCookie(cookie);
        assertEquals(3, simpleCookieStore().getCookies().size());
        assertCookieStoresEquivalent(simpleCookieStore(), bdbCookieStore());

        // add 4th cookie at previously used path
        cookie = new BasicClientCookie("name2", "value6");
        cookie.setPath("/path1");
        bdbCookieStore().addCookie(cookie);
        simpleCookieStore().addCookie(cookie);
        assertEquals(4, simpleCookieStore().getCookies().size());
        assertCookieStoresEquivalent(simpleCookieStore(), bdbCookieStore());
        
        // add 5th cookie at different path (case sensitive)
        cookie = new BasicClientCookie("name1", "value7");
        cookie.setPath("/pAtH1");
        bdbCookieStore().addCookie(cookie);
        simpleCookieStore().addCookie(cookie);
        assertEquals(5, simpleCookieStore().getCookies().size());
        assertCookieStoresEquivalent(simpleCookieStore(), bdbCookieStore());
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
        Collection<Cookie> cookiesBefore = bdbCookieStore().getCookies();
        bdbCookieStore().saveCookies();
        
        bdbCookieStore().clear();
        assertEquals(0, bdbCookieStore().getCookies().size());
        
        bdbCookieStore().loadCookies((ConfigFile) bdbCookieStore().getCookiesSaveFile());
        assertEquals(6, bdbCookieStore().getCookies().size());
        logger.info("before: " + cookiesBefore);
        logger.info(" after: " + bdbCookieStore().getCookies());
        assertCookieListsEquivalent(cookiesBefore, bdbCookieStore().getCookies());
    }
    
    public void testConcurrentLoad() throws IOException, InterruptedException {
        bdbCookieStore().clear();

        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                try {
                    while (!Thread.interrupted()) {
                        Collection<Cookie> cookies = bdbCookieStore().getCookies();
                        new ArrayList<Cookie>(cookies);
                        BasicClientCookie cookie = new BasicClientCookie(UUID.randomUUID().toString(), UUID.randomUUID().toString());
                        bdbCookieStore().addCookie(cookie);
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        };

        Thread[] threads = new Thread[200];
        for (int i = 0; i < threads.length; i++) {
            threads[i] = new Thread(runnable);
            threads[i].setName("cookie-load-test-" + i);
            threads[i].start();
        }

        Thread.sleep(5000);
        
        for (int i = 0; i < threads.length; i++) {
            threads[i].interrupt();
        }
        for (int i = 0; i < threads.length; i++) {
            threads[i].join();
        }
        
        assertTrue(bdbCookieStore().getCookies().size() > 3000);
        assertCookieListsEquivalent(bdbCookieStore().getCookies(),
                bdbCookieStore().getCookiesBypassCache());
    }

    protected void assertCookieListsEquivalent(Collection<Cookie> list1,
            Collection<Cookie> list2) {
        Comparator<Cookie> comparator = new Comparator<Cookie>() {
            @Override
            public int compare(Cookie o1, Cookie o2) {
                return o1.toString().compareTo(o2.toString());
            }
        };
        
        ArrayList<Cookie> sorted1 = new ArrayList<Cookie>(list1);
        Collections.sort(sorted1, comparator);
        ArrayList<Cookie> sorted2 = new ArrayList<Cookie>(list2);
        Collections.sort(sorted2, comparator);
        
        assertEquals(list1.size(), list2.size());
        assertEquals(sorted1.size(), sorted2.size());
        
        Iterator<Cookie> iter1 = sorted1.iterator();
        Iterator<Cookie> iter2 = sorted2.iterator();
        for (int i = 0; i < list1.size(); i++) {
            Cookie c1 = iter1.next();
            Cookie c2 = iter2.next();
            assertCookiesIdentical(c1, c2);
        }
    }
    
    protected void assertCookieStoresEquivalent(SimpleCookieStore simple, BdbCookieStore bdb) {
        assertCookieListsEquivalent(simple.getCookies(), bdb.getCookies());
        assertCookieListsEquivalent(bdb.getCookies(), bdb.getCookiesBypassCache());
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
