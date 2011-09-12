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
package org.archive.crawler.frontier.precedence;

import static org.archive.modules.CoreAttributeConstants.A_PRECALC_PRECEDENCE;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.archive.modules.recrawl.PersistProcessor;
import org.archive.util.ArchiveUtils;
import org.archive.util.FileUtils;
import org.archive.util.bdbje.EnhancedEnvironment;
import org.archive.util.iterator.LineReadingIterator;

import com.sleepycat.bind.serial.SerialBinding;
import com.sleepycat.bind.serial.StoredClassCatalog;
import com.sleepycat.bind.tuple.StringBinding;
import com.sleepycat.collections.StoredSortedMap;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseException;



/**
 * Utility class for loading externally-created URI-precedence values 
 * into the URI-history database. 
 * 
 * TODO: refactor code relied on in PersistProcessor for easier reuse here
 * (and elsewhere)
 * 
 * @author gojomo
 */
public class PrecedenceLoader {

    public PrecedenceLoader() {
    }

    /**
     * Utility main for importing a text file (first argument) with lines of 
     * the form:
     * 
     *  URI [whitespace] precedence 
     *  
     * into a BDB-JE environment (second argument, created if necessary). 
     * 
     * @param args command-line arguments
     * @throws DatabaseException
     * @throws IOException
     */
    public static void main(String[] args) throws DatabaseException, IOException {
        if(args.length==2) {
            main2args(args);
        } else {
            System.out.println("Arguments: ");
            System.out.println("    source target");
            System.out.println(
                "...where source is a file of lines 'URI precedence' ");
            System.out.println(
                "and target is a BDB env dir (created if necessary). ");
            return;
        }
        
    }

    /**
     * Merge the precalculated precedence information in the first argument 
     * file to the environment in the second environment (path; environment 
     * will be created if it does not already exist). 
     * 
     * @param args command-line arguments
     * @throws DatabaseException
     * @throws FileNotFoundException
     * @throws UnsupportedEncodingException
     * @throws IOException
     */
    @SuppressWarnings("unchecked")
    private static void main2args(String[] args) throws DatabaseException,
            FileNotFoundException, UnsupportedEncodingException, IOException {
        File source = new File(args[0]);
        File env = new File(args[1]);
        FileUtils.ensureWriteableDirectory(env);
        
        // setup target environment
        EnhancedEnvironment targetEnv = PersistProcessor.setupCopyEnvironment(env);
        StoredClassCatalog classCatalog = targetEnv.getClassCatalog();
        Database historyDB = targetEnv.openDatabase(
                null,
                PersistProcessor.URI_HISTORY_DBNAME,
                PersistProcessor.HISTORY_DB_CONFIG.toDatabaseConfig());
        StoredSortedMap historyMap = new StoredSortedMap(historyDB,
                new StringBinding(), new SerialBinding(classCatalog,
                        Map.class), true);
        
        int count = 0;
        
        if(source.isFile()) {
            // scan log, writing to database
            BufferedReader br = ArchiveUtils.getBufferedReader(source);
            Iterator iter = new LineReadingIterator(br);
            while(iter.hasNext()) {
                String line = (String) iter.next(); 
                String[] splits = line.split("\\s");
                String uri = splits[0];
                if(!uri.matches("\\w+:.*")) {
                    // prepend "http://"
                    uri = "http://"+uri;
                }
                String key = PersistProcessor.persistKeyFor(uri);
                int precedence = Integer.parseInt(splits[1]);
                Map<String,Object> map = (Map<String,Object>)historyMap.get(key);
                if (map==null) {
                    map = new HashMap<String,Object>();
                }
                map.put(A_PRECALC_PRECEDENCE, precedence);
                historyMap.put(key,map);
                count++;
                if(count % 100000 == 0) {
                    System.out.print(count+"... ");
                }
            }
            br.close();
            System.out.println();
            System.out.println(count+" entries loaded");
        } else {
            // error
            System.err.println("unacceptable source file");
            return;
        }
        
        // cleanup
        historyDB.sync();
        historyDB.close();
        targetEnv.close();
        System.out.println(count+" records imported from "+source+" to BDB env "+env);
    }
}