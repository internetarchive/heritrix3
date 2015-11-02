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
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
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
 * Tests that {@link BdbCookieStore} matches behavior of
 * {@link BasicCookieStore}.
 * 
 * @contributor nlevitt
 */
public class CookieStoreTest extends TmpDirTestCase {

    private static Logger logger = Logger.getLogger(CookieStoreTest.class.getName());

    protected BdbModule bdb;
    protected BdbCookieStore bdbCookieStore;
    protected BasicCookieStore basicCookieStore;

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

    protected AbstractCookieStore bdbCookieStore() throws IOException {
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

    protected BasicCookieStore basicCookieStore() {
        if (basicCookieStore == null) {
            basicCookieStore = new BasicCookieStore();
        }
        return basicCookieStore;
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        bdb.close();
    }

    public void testBasics() throws IOException {
        bdbCookieStore().clear();
        basicCookieStore().clear();
        assertCookieStoresEquivalent(basicCookieStore(), bdbCookieStore());

        BasicClientCookie cookie = new BasicClientCookie("name1", "value1");
        bdbCookieStore().addCookie(cookie);
        basicCookieStore().addCookie(cookie);
        assertCookieStoresEquivalent(basicCookieStore(), bdbCookieStore());
    }

    public void testSimpleReplace() throws IOException {
        bdbCookieStore().clear();
        basicCookieStore().clear();
        assertCookieStoresEquivalent(basicCookieStore(), bdbCookieStore());

        BasicClientCookie cookie = new BasicClientCookie("name1", "value1");
        bdbCookieStore().addCookie(cookie);
        basicCookieStore().addCookie(cookie);
        assertCookieStoresEquivalent(basicCookieStore(), bdbCookieStore());

        // should replace existing cookie
        cookie = new BasicClientCookie("name1", "value2");
        bdbCookieStore().addCookie(cookie);
        basicCookieStore().addCookie(cookie);
        assertCookieStoresEquivalent(basicCookieStore(), bdbCookieStore());
    }

    public void testDomains() throws IOException {
        bdbCookieStore().clear();
        basicCookieStore().clear();
        assertCookieStoresEquivalent(basicCookieStore(), bdbCookieStore());

        BasicClientCookie cookie = new BasicClientCookie("name1", "value1");
        cookie.setDomain("example.org");
        bdbCookieStore().addCookie(cookie);
        basicCookieStore().addCookie(cookie);
        assertCookieStoresEquivalent(basicCookieStore(), bdbCookieStore());

        // add a 2nd cookie, same name, different domain
        cookie = new BasicClientCookie("name1", "value2");
        cookie.setDomain("example.com");
        bdbCookieStore().addCookie(cookie);
        basicCookieStore().addCookie(cookie);
        assertCookieStoresEquivalent(basicCookieStore(), bdbCookieStore());

        // add a 3rd cookie, same name, different domain
        cookie = new BasicClientCookie("name1", "value3");
        cookie.setDomain("foo.example.com");
        bdbCookieStore().addCookie(cookie);
        basicCookieStore().addCookie(cookie);
        assertCookieStoresEquivalent(basicCookieStore(), bdbCookieStore());

        // replace 1st cookie
        cookie = new BasicClientCookie("name1", "value4");
        cookie.setDomain("example.org");
        bdbCookieStore().addCookie(cookie);
        basicCookieStore().addCookie(cookie);
        assertCookieStoresEquivalent(basicCookieStore(), bdbCookieStore());

        // replace 2nd cookie, case-insensitive domain
        cookie = new BasicClientCookie("name1", "value5");
        cookie.setDomain("eXaMpLe.CoM");
        bdbCookieStore().addCookie(cookie);
        basicCookieStore().addCookie(cookie);
        assertCookieStoresEquivalent(basicCookieStore(), bdbCookieStore());
    }

    public void testMaxCookieDomain() throws IOException {
        bdbCookieStore().clear();

        for (int i = 1; i <= BdbCookieStore.MAX_COOKIES_FOR_DOMAIN; ++i) {
            BasicClientCookie cookie = new BasicClientCookie("name" + i, "value" + i);
            bdbCookieStore().addCookie(cookie);
            
            assertCookieStoreCountEquals(bdbCookieStore, i);
        }
        
        BasicClientCookie cookie = new BasicClientCookie("nametoomany1", "valuetoomany1");
        bdbCookieStore().addCookie(cookie);
        assertCookieStoreCountEquals(bdbCookieStore, BdbCookieStore.MAX_COOKIES_FOR_DOMAIN);
        
        cookie = new BasicClientCookie("nametoomany2", "valuetoomany2");
        bdbCookieStore().addCookie(cookie);
        assertCookieStoreCountEquals(bdbCookieStore, BdbCookieStore.MAX_COOKIES_FOR_DOMAIN);
    }
    
    public void testPaths() throws IOException {
        bdbCookieStore().clear();
        basicCookieStore().clear();
        assertCookieStoresEquivalent(basicCookieStore(), bdbCookieStore());

        BasicClientCookie cookie = new BasicClientCookie("name1", "value1");
        bdbCookieStore().addCookie(cookie);
        basicCookieStore().addCookie(cookie);
        assertCookieStoresEquivalent(basicCookieStore(), bdbCookieStore());

        // replace 1st cookie, with explicit path "/", which is the implied path if not specified
        cookie = new BasicClientCookie("name1", "value2");
        cookie.setPath("/");
        bdbCookieStore().addCookie(cookie);
        basicCookieStore().addCookie(cookie);
        assertEquals(1, basicCookieStore().getCookies().size());
        assertEquals(cookie, new ArrayList<Cookie>(basicCookieStore().getCookies()).get(0));
        assertCookieStoresEquivalent(basicCookieStore(), bdbCookieStore());

        // add a 2nd cookie at a subpath
        cookie = new BasicClientCookie("name1", "value3");
        cookie.setPath("/path1");
        bdbCookieStore().addCookie(cookie);
        basicCookieStore().addCookie(cookie);
        assertEquals(2, basicCookieStore().getCookies().size());
        assertCookieStoresEquivalent(basicCookieStore(), bdbCookieStore());

        // add a 3rd cookie at a subpath
        cookie = new BasicClientCookie("name1", "value4");
        cookie.setPath("/path2");
        bdbCookieStore().addCookie(cookie);
        basicCookieStore().addCookie(cookie);
        assertEquals(3, basicCookieStore().getCookies().size());
        assertCookieStoresEquivalent(basicCookieStore(), bdbCookieStore());

        // replace 2nd cookie
        cookie = new BasicClientCookie("name1", "value5");
        cookie.setPath("/path1");
        bdbCookieStore().addCookie(cookie);
        basicCookieStore().addCookie(cookie);
        assertEquals(3, basicCookieStore().getCookies().size());
        assertCookieStoresEquivalent(basicCookieStore(), bdbCookieStore());

        // add 4th cookie at previously used path
        cookie = new BasicClientCookie("name2", "value6");
        cookie.setPath("/path1");
        bdbCookieStore().addCookie(cookie);
        basicCookieStore().addCookie(cookie);
        assertEquals(4, basicCookieStore().getCookies().size());
        assertCookieStoresEquivalent(basicCookieStore(), bdbCookieStore());

        // add 5th cookie at different path (case sensitive)
        cookie = new BasicClientCookie("name1", "value7");
        cookie.setPath("/pAtH1");
        bdbCookieStore().addCookie(cookie);
        basicCookieStore().addCookie(cookie);
        assertEquals(5, basicCookieStore().getCookies().size());
        assertCookieStoresEquivalent(basicCookieStore(), bdbCookieStore());
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

        List<Cookie> cookiesBefore = new ArrayList<Cookie>(bdbCookieStore().getCookies()); 
        assertEquals(6, cookiesBefore.size());
        bdbCookieStore().saveCookies();

        bdbCookieStore().clear();
        assertEquals(0, bdbCookieStore().getCookies().size());

        bdbCookieStore().loadCookies((ConfigFile) bdbCookieStore().getCookiesSaveFile());
        List<Cookie> cookiesAfter = new ArrayList<Cookie>(bdbCookieStore().getCookies());
        assertEquals(6, cookiesAfter.size());
        logger.info("before: " + cookiesBefore);
        logger.info(" after: " + cookiesAfter);
        assertCookieListsEquivalent(cookiesBefore, cookiesAfter);
    }

    public void testConcurrentLoadNoDomainCookieLimitBreach() throws IOException, InterruptedException {
        bdbCookieStore().clear();
        basicCookieStore().clear();
        final Random rand = new Random();

        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                try {
                    while (!Thread.interrupted()) {
                        BasicClientCookie cookie = new BasicClientCookie(UUID.randomUUID().toString(), UUID.randomUUID().toString());
                        cookie.setDomain("d" + rand.nextInt() + ".example.com");
                        bdbCookieStore().addCookie(cookie);
                        basicCookieStore().addCookie(cookie);
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

        List<Cookie> bdbCookieList = bdbCookieStore().getCookies();
        assertTrue(bdbCookieList.size() > 3000);
        assertCookieListsEquivalent(bdbCookieList, basicCookieStore().getCookies());        
    }
    
    public void testConcurrentLoad() throws IOException, InterruptedException {
        bdbCookieStore().clear();
        basicCookieStore().clear();
        final Random rand = new Random();

        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                try {
                    while (!Thread.interrupted()) {
                        BasicClientCookie cookie = new BasicClientCookie(UUID.randomUUID().toString(), UUID.randomUUID().toString());
                        cookie.setDomain("d" + rand.nextInt(20) + ".example.com");
                        bdbCookieStore().addCookie(cookie);
                        basicCookieStore().addCookie(cookie);
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

        Thread.sleep(1000);

        for (int i = 0; i < threads.length; i++) {
            threads[i].interrupt();
        }
        for (int i = 0; i < threads.length; i++) {
            threads[i].join();
        }

        ArrayList<Cookie> bdbCookieArrayList = new ArrayList<Cookie>(bdbCookieStore().getCookies());
        Map<String, Integer> domainCounts = new HashMap<String, Integer>();
        for (Cookie cookie : bdbCookieArrayList) {
            if (domainCounts.get(cookie.getDomain()) == null) {
                domainCounts.put(cookie.getDomain(), 1);
            }
            else {
                domainCounts.put(cookie.getDomain(), domainCounts.get(cookie.getDomain()) + 1);
            }
        }
        
        for (String domain: domainCounts.keySet()) {
            assertTrue(domainCounts.get(domain) <= BdbCookieStore.MAX_COOKIES_FOR_DOMAIN + 25);
        }        
    }
    
    protected void assertCookieStoreCountEquals(BdbCookieStore bdb, int count) {
        assertEquals(bdb.getCookies().size(), count);
    }    

    protected void assertCookieListsEquivalent(List<Cookie> list1,
            List<Cookie> list2) {
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
        for (int i = 0; i < sorted1.size(); i++) {
            Cookie c1 = iter1.next();
            Cookie c2 = iter2.next();
            assertCookiesIdentical(c1, c2);
        }
    }

    protected void assertCookieStoresEquivalent(BasicCookieStore simple, AbstractCookieStore bdb) {
        assertCookieListsEquivalent(simple.getCookies(), bdb.getCookies());
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
