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

package org.archive.settings.file;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

import org.apache.commons.io.FileUtils;
import org.archive.util.PrefixFinder;
import org.archive.util.TmpDirTestCase;

import com.sleepycat.bind.tuple.StringBinding;
import com.sleepycat.collections.StoredSortedMap;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;

/**
 * Unit test for PrefixFinder.
 * 
 * @author pjack
 */
public class PrefixFinderTest extends TmpDirTestCase {

    public void xtestFind() {
        for (int i = 0; i < 100; i++) {
            doTest();
        }
    }

    public void testNoneFoundSmallSet() {
        SortedSet<String> testData = new TreeSet<String>();
        testData.add("foo");
        List<String> result = PrefixFinder.find(testData, "baz");
        assertTrue(result.isEmpty());
    }
    
    public void testOneFoundSmallSet() {
        SortedSet<String> testData = new TreeSet<String>();
        testData.add("foo");
        List<String> result = PrefixFinder.find(testData, "foobar");
        assertTrue(result.size()==1);
        assertTrue(result.contains("foo"));
    }
    
    public void testSortedMap() {
        TreeMap<String,String> map = new TreeMap<String,String>();
        testUrlsNoMatch(map);
    }
    
    public void testStoredSortedMap() throws Exception {
        EnvironmentConfig config = new EnvironmentConfig();
        config.setAllowCreate(true);      
        config.setCachePercent(5);
        
        File f = new File(getTmpDir(), "PrefixFinderText");
        FileUtils.deleteQuietly(f);
        org.archive.util.FileUtils.ensureWriteableDirectory(f);
        Environment bdbEnvironment = new Environment(f, config);
        DatabaseConfig dbConfig = new DatabaseConfig();
        dbConfig.setAllowCreate(true);
        dbConfig.setDeferredWrite(true);
        Database db = bdbEnvironment.openDatabase(null, "test", dbConfig);
        
        StoredSortedMap<String, String> ssm = new StoredSortedMap<String, String>(db, new StringBinding(), new StringBinding(), true);        
        testUrlsNoMatch(ssm);   
        db.close();
        bdbEnvironment.close();
    }

    private void testUrlsNoMatch(SortedMap<String,String> sm) {
        sm.put("http://(com,ilovepauljack,www,", "foo");
        for (int i = 0; i < 10; i++) {
            sm.put("http://" + Math.random(), "foo");
        }
        Set<String> keys = sm.keySet(); 
        if(!(keys instanceof SortedSet)) {
            keys = new TreeSet<String>(keys);
        }
        List<String> results = PrefixFinder.find((SortedSet<String>)keys, "http://");
        assertTrue(results.isEmpty());
    }

    private void doTest() {
        // Generate test data.
        SortedSet<String> testData = new TreeSet<String>();
        long seed = System.currentTimeMillis();
        System.out.println("Used seed: " + seed);
        Random random = new Random(seed);
        String prefix = "0";
        testData.add(prefix);
        for (int i = 1; i < 10000; i++) {
            if (random.nextInt(1024) == 0) {
                prefix += " " + i;
                testData.add(prefix);
            } else {
                testData.add(prefix + " " + i);
            }
        }

        // Brute-force to get the expected results.
        List<String> expected = new ArrayList<String>();
        for (String value: testData) {
            if (prefix.startsWith(value)) {
                expected.add(value);
            }
        }
        
        // Results go from longest to shortest.
        Collections.reverse(expected);

        final List<String> result = PrefixFinder.find(testData, prefix);

        if (!result.equals(expected)) {
            System.out.println("Expected: " + expected);
            System.out.println("Result:   " + result);
        }
        assertEquals(result, expected);
        
        // Double-check.
        for (String value: result) {
            if (!prefix.startsWith(value)) {
                System.out.println("Result: " + result);                
                fail("Prefix string \"" + prefix 
                        + "\" does not start with result key \"" 
                        + value + "\"");
            }
        }
    }

    
}
