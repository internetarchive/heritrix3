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


import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.SerializationUtils;
import org.archive.bdb.BdbModule;
import org.archive.modules.CrawlURI;
import org.archive.util.ArchiveUtils;
import org.archive.util.FileUtils;
import org.archive.util.OneLineSimpleLogger;
import org.archive.util.SURT;
import org.archive.util.bdbje.EnhancedEnvironment;
import org.archive.util.iterator.LineReadingIterator;
import org.json.JSONObject;

import com.sleepycat.bind.serial.SerialBinding;
import com.sleepycat.bind.serial.StoredClassCatalog;
import com.sleepycat.bind.tuple.StringBinding;
import com.sleepycat.collections.StoredIterator;
import com.sleepycat.collections.StoredSortedMap;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.EnvironmentConfig;

/**
 * Superclass for Processors which utilize BDB-JE for URI state
 * (including most notably history) persistence.
 * 
 * @author gojomo
 */
public abstract class PersistProcessor extends AbstractPersistProcessor {

    @SuppressWarnings("unused")
    private static final long serialVersionUID = 1L;

    private static final Logger logger =
        Logger.getLogger(PersistProcessor.class.getName());

    /** name of history Database */
    public static final String URI_HISTORY_DBNAME = "uri_history";
    
    public static final BdbModule.BdbConfig HISTORY_DB_CONFIG;
    static {
        BdbModule.BdbConfig dbConfig = new BdbModule.BdbConfig();
        dbConfig.setTransactional(false);
        dbConfig.setAllowCreate(true);
        dbConfig.setDeferredWrite(true);
        HISTORY_DB_CONFIG = dbConfig;
    }

    public PersistProcessor() {
    }
    
    /**
     * Return a preferred String key for persisting the given CrawlURI's
     * AList state. 
     * 
     * @param curi CrawlURI
     * @return String key
     */
    public static String persistKeyFor(CrawlURI curi) {
        // use a case-sensitive SURT for uniqueness and sorting benefits
        return persistKeyFor(curi.getUURI().toString());
    }

    public static String persistKeyFor(String uri) {
        // use a case-sensitive SURT for uniqueness and sorting benefits
        return SURT.fromURI(uri,true);
    }

    /**
     * Copies entries from an existing environment db to a new one. If
     * historyMap is not provided, only logs the entries that would have been 
     * copied.
     * 
     * @param sourceDir existing environment database directory
     * @param historyMap new environment db (or null for a dry run)
     * @return number of records
     * @throws DatabaseException
     */
    private static int copyPersistEnv(File sourceDir, StoredSortedMap<String,Map> historyMap) 
    throws DatabaseException {
        int count = 0;

        // open the source env history DB, copying entries to target env
        EnhancedEnvironment sourceEnv = setupCopyEnvironment(sourceDir, true);
        StoredClassCatalog sourceClassCatalog = sourceEnv.getClassCatalog();
        DatabaseConfig historyDbConfig = HISTORY_DB_CONFIG.toDatabaseConfig();
        historyDbConfig.setReadOnly(true);
        Database sourceHistoryDB = sourceEnv.openDatabase(
                null, URI_HISTORY_DBNAME, historyDbConfig);
        StoredSortedMap<String,Map> sourceHistoryMap = new StoredSortedMap<String,Map>(sourceHistoryDB,
                new StringBinding(), new SerialBinding<Map>(sourceClassCatalog,
                        Map.class), true);

        Iterator<Entry<String,Map>> iter = sourceHistoryMap.entrySet().iterator();
        while (iter.hasNext()) {
            Entry<String,Map> item = iter.next(); 
            if (logger.isLoggable(Level.FINE)) {
                logger.fine(item.getKey() + " " + new JSONObject(item.getValue()));
            }
            
            if (historyMap != null) {
                historyMap.put(item.getKey(), item.getValue());
            }
            count++;
        }
        StoredIterator.close(iter);
        sourceHistoryDB.close();
        sourceEnv.close();
        
        return count;
    }

    /**
     * Populates an environment db from a persist log. If historyMap is
     * not provided, only logs the entries that would have been populated.
     * 
     * @param persistLogReader
     *            persist log
     * @param historyMap
     *            new environment db (or null for a dry run)
     * @return number of records
     * @throws UnsupportedEncodingException
     * @throws DatabaseException
     */
    private static int populatePersistEnvFromLog(BufferedReader persistLogReader, StoredSortedMap<String,Map> historyMap) 
    throws UnsupportedEncodingException, DatabaseException {
        int count = 0;

        Iterator<String> iter = new LineReadingIterator(persistLogReader);
        while (iter.hasNext()) {
            String line = iter.next(); 
            if (line.length() == 0) {
                continue;
            }
            String[] splits = line.split(" ");
            if (splits.length != 2) {
                logger.severe("bad line has " + splits.length + " fields (should be 2): " + line);
                continue;
            }

            Map alist;
            try {
                alist = (Map) SerializationUtils.deserialize(Base64.decodeBase64(splits[1].getBytes("UTF-8")));
            } catch (Exception e) {
                logger.severe("caught exception " + e + " deserializing line: " + line);
                continue;
            }

            if (logger.isLoggable(Level.FINE)) {
                logger.fine(splits[0] + " " + ArchiveUtils.prettyString(alist));
            }

            if (historyMap != null) try {
                historyMap.put(splits[0], alist);
            } catch (Exception e) {
                logger.log(Level.SEVERE, "caught exception after loading " + count + 
                        " urls from the persist log (perhaps crawl was stopped by user?)", e);
                IOUtils.closeQuietly(persistLogReader);

                // seems to finish most cleanly when we return rather than throw something
                return count;
            }

            count++;
        }
        IOUtils.closeQuietly(persistLogReader);
        
        return count;
    }

    /**
     * Populates a new environment db from an old environment db or a persist
     * log. If path to new environment is not provided, only logs the entries 
     * that would have been populated.
     * 
     * @param sourcePath
     *            source of old entries: can be a path to an existing
     *            environment db, or a URL or path to a persist log
     * @param envFile
     *            path to new environment db (or null for a dry run)
     * @return number of records
     * @throws DatabaseException
     * @throws IOException
     */
    public static int populatePersistEnv(String sourcePath, File envFile)
        throws IOException {
        int count = 0;
        StoredSortedMap<String,Map> historyMap = null;
        EnhancedEnvironment targetEnv = null;
        StoredClassCatalog classCatalog = null;
        Database historyDB = null;

        if (envFile != null) {
            // set up target environment
            FileUtils.ensureWriteableDirectory(envFile);
            targetEnv = setupCopyEnvironment(envFile);
            classCatalog = targetEnv.getClassCatalog();
            historyDB = targetEnv.openDatabase(null, URI_HISTORY_DBNAME, 
                    HISTORY_DB_CONFIG.toDatabaseConfig());
            historyMap = new StoredSortedMap<String,Map>(historyDB, 
                    new StringBinding(), new SerialBinding<Map>(classCatalog,
                        Map.class), true);
        }

        try {
            count = copyPersistSourceToHistoryMap(new File(sourcePath), historyMap);
        } finally {
            // in finally block so that we unlock the target env even if we
            // failed to populate it
            if (envFile != null) {
                logger.info(count + " records imported from " + sourcePath + " to BDB env " + envFile);
                historyDB.sync();
                historyDB.close();
                targetEnv.close();
            } else {
                logger.info(count + " records found in " + sourcePath);
            }
        }

        return count;
    }

    /**
     * Populates a given StoredSortedMap (history map) from an old 
     * environment db or a persist log. If a map is not provided, only 
     * logs the entries that would have been populated.
     * 
     * @param sourceFile
     *            source of old entries: can be a path to an existing
     *            environment db or persist log
     * @param historyMap
     *            map to populate (or null for a dry run)
     * @return number of records
     * @throws DatabaseException
     * @throws IOException
     */
    public static int copyPersistSourceToHistoryMap(File sourceFile,
            StoredSortedMap<String, Map> historyMap) throws DatabaseException,
            IOException {
        // delegate depending on the source
        if (sourceFile.isDirectory()) {
            return copyPersistEnv(sourceFile, historyMap);
        } else {
            BufferedReader persistLogReader = ArchiveUtils.getBufferedReader(sourceFile);
            return populatePersistEnvFromLog(persistLogReader, historyMap);
        }
    }

    /**
     * Populates a given StoredSortedMap (history map) from an old persist log.
     * If a map is not provided, only logs the entries that would have been
     * populated.
     * 
     * @param sourceUrl
     *            url of source persist log
     * @param historyMap
     *            map to populate (or null for a dry run)
     * @return number of records
     * @throws DatabaseException
     * @throws IOException
     */
    public static int copyPersistSourceToHistoryMap(URL sourceUrl,
            StoredSortedMap<String, Map> historyMap) throws DatabaseException,
            IOException {
        BufferedReader persistLogReader = ArchiveUtils
                .getBufferedReader(sourceUrl);
        return populatePersistEnvFromLog(persistLogReader, historyMap);
    }
    
    /**
     * Utility main for importing a log into a BDB-JE environment or moving a
     * database between environments (2 arguments), or simply dumping a log
     * to stderr in a more readable format (1 argument). 
     * 
     * @param args command-line arguments
     * @throws DatabaseException
     * @throws IOException
     */
    public static void main(String[] args) throws DatabaseException, IOException {
        Handler handler = new ConsoleHandler();
        handler.setLevel(Level.ALL);
        handler.setFormatter(new OneLineSimpleLogger());
        logger.addHandler(handler);
        logger.setUseParentHandlers(false);

        if (args.length == 2) {
            logger.setLevel(Level.INFO);
            populatePersistEnv(args[0], new File(args[1]));
        } else if (args.length == 1) {
            logger.setLevel(Level.FINE);
            populatePersistEnv(args[0], null);
        } else {
            System.out.println("Arguments: ");
            System.out.println("    source [target]");
            System.out.println(
                "...where source is either a txtser log file or BDB env dir");
            System.out.println(
                "and target, if present, is a BDB env dir. ");
            return;
        }
    }

    public static EnhancedEnvironment setupCopyEnvironment(File env) throws DatabaseException {
        return setupCopyEnvironment(env, false);
    }
    
    public static EnhancedEnvironment setupCopyEnvironment(File env, boolean readOnly) throws DatabaseException {
        EnvironmentConfig envConfig = new EnvironmentConfig();
        envConfig.setAllowCreate(true);
        envConfig.setReadOnly(readOnly); 
        try {
            return new EnhancedEnvironment(env, envConfig);
        } catch (IllegalArgumentException iae) {
            throw new IllegalArgumentException("problem with specified environment "+env+"; is it already open?", iae);
        }
    }
}