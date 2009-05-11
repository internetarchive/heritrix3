/* PersistProcessor.java
 * 
 * Created on Feb 17, 2005
 *
 * Copyright (C) 2007 Internet Archive.
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
package org.archive.modules.recrawl;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.SerializationUtils;
import org.archive.bdb.BdbModule;
import org.archive.io.CrawlerJournal;
import org.archive.modules.Processor;
import org.archive.modules.ProcessorURI;
import org.archive.util.ArchiveUtils;
import org.archive.util.SURT;
import org.archive.util.bdbje.EnhancedEnvironment;
import org.archive.util.iterator.LineReadingIterator;

import com.sleepycat.bind.serial.SerialBinding;
import com.sleepycat.bind.serial.StoredClassCatalog;
import com.sleepycat.bind.tuple.StringBinding;
import com.sleepycat.collections.StoredIterator;
import com.sleepycat.collections.StoredSortedMap;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.EnvironmentConfig;



/**
 * Superclass for Processors which utilize BDB-JE for URI state
 * (including most notably history) persistence.
 * 
 * @author gojomo
 */
public abstract class PersistProcessor extends Processor {
    private static final Logger logger =
        Logger.getLogger(PersistProcessor.class.getName());

    /** name of history Database */
    public static final String URI_HISTORY_DBNAME = "uri_history";
    
    /**
     * @return DatabaseConfig for history Database
     */
    public static BdbModule.BdbConfig historyDatabaseConfig() {
        BdbModule.BdbConfig dbConfig = new BdbModule.BdbConfig();
        dbConfig.setTransactional(false);
        dbConfig.setAllowCreate(true);
        return dbConfig;
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
    public static String persistKeyFor(ProcessorURI curi) {
        return persistKeyFor(curi.getUURI().toString());
    }

    public static String persistKeyFor(String uri) {
        // use a case-sensitive SURT for uniqueness and sorting benefits
        return SURT.fromURI(uri,true);
    }

    /**
     * Whether the current CrawlURI's state should be persisted (to log or
     * direct to database)
     * 
     * @param curi CrawlURI
     * @return true if state should be stored; false to skip persistence
     */
    protected boolean shouldStore(ProcessorURI curi) {
        // TODO: don't store some codes, such as 304 unchanged?
        return curi.isSuccess();
    }

    /**
     * Whether the current CrawlURI's state should be loaded
     * 
     * @param curi CrawlURI
     * @return true if state should be loaded; false to skip loading
     */
    protected boolean shouldLoad(ProcessorURI curi) {
        // TODO: don't load some (prereqs?)
        return true;
    }

    /**
     * Utility main for importing a log into a BDB-JE environment or moving a
     * database between environments (2 arguments), or simply dumping a log
     * to stdout in a more readable format (1 argument). 
     * 
     * @param args command-line arguments
     * @throws DatabaseException
     * @throws IOException
     */
    public static void main(String[] args) throws DatabaseException, IOException {
        if(args.length==2) {
            main2args(args);
        } else if (args.length==1) {
            main1arg(args);
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

    /**
     * Move the history information in the first argument (either the path 
     * to a log or to an environment containing a uri_history database) to 
     * the environment in the second environment (path; environment will 
     * be created if it dow not already exist). 
     * 
     * @param args command-line arguments
     * @throws DatabaseException
     * @throws FileNotFoundException
     * @throws UnsupportedEncodingException
     * @throws IOException
     */
    @SuppressWarnings("unchecked")
    private static void main2args(String[] args) throws DatabaseException, FileNotFoundException, UnsupportedEncodingException, IOException {
        File source = new File(args[0]);
        File env = new File(args[1]);
        if(!env.exists()) {
            env.mkdirs();
        }
        
        // setup target environment
        EnhancedEnvironment targetEnv = setupEnvironment(env);
        StoredClassCatalog classCatalog = targetEnv.getClassCatalog();
        Database historyDB = targetEnv.openDatabase(
                null,URI_HISTORY_DBNAME,historyDatabaseConfig().toDatabaseConfig());
        StoredSortedMap historyMap = new StoredSortedMap(historyDB,
                new StringBinding(), new SerialBinding(classCatalog,
                        Map.class), true);
        
        int count = 0;
        
        if(source.isFile()) {
            // scan log, writing to database
            BufferedReader br = CrawlerJournal.getBufferedReader(source);
            Iterator iter = new LineReadingIterator(br);
            while(iter.hasNext()) {
                String line = (String) iter.next(); 
                line = line.trim();
                if(line.length()==0) {
                    continue;
                }
                String[] splits = line.split(" ");
                if(splits.length!=2) {
                    logger.severe("bad line: "+line);
                    continue;
                }
                try {
                    historyMap.put(
                        splits[0], 
                        SerializationUtils.deserialize(
                            Base64.decodeBase64(splits[1].getBytes("UTF8"))));
                } catch (RuntimeException e) {
                    logger.log(Level.SEVERE,"problem with line: "+line, e);
                }
                count++;
            }
            IOUtils.closeQuietly(br);
        } else {
            // open the source env history DB, copying entries to target env
            EnhancedEnvironment sourceEnv = setupEnvironment(source);
            StoredClassCatalog sourceClassCatalog = sourceEnv.getClassCatalog();
            Database sourceHistoryDB = sourceEnv.openDatabase(
                    null,URI_HISTORY_DBNAME,historyDatabaseConfig().toDatabaseConfig());
            StoredSortedMap sourceHistoryMap = new StoredSortedMap(sourceHistoryDB,
                    new StringBinding(), new SerialBinding(sourceClassCatalog,
                            Map.class), true);
            Iterator iter = sourceHistoryMap.entrySet().iterator();
            while(iter.hasNext()) {
                Entry item = (Entry) iter.next(); 
                historyMap.put(item.getKey(), item.getValue());
                count++;
            }
            StoredIterator.close(iter);
            sourceHistoryDB.close();
            sourceEnv.close();
        }
        
        // cleanup
        historyDB.sync();
        historyDB.close();
        targetEnv.close();
        System.out.println(count+" records imported from "+source+" to BDB env "+env);
    }

    /**
     * Dump the contents of the argument (path to a persist log) to stdout
     * in a slightly more readable format. 
     * 
     * @param args command-line arguments
     * @throws DatabaseException
     * @throws FileNotFoundException
     * @throws UnsupportedEncodingException
     * @throws IOException
     */
    @SuppressWarnings("unchecked")
    private static void main1arg(String[] args) throws DatabaseException, FileNotFoundException, UnsupportedEncodingException, IOException {
        File source = new File(args[0]);
        
        int count = 0;
        
        if(source.isFile()) {
            // scan log, writing to database
            BufferedReader br = CrawlerJournal.getBufferedReader(source);
            Iterator iter = new LineReadingIterator(br);
            while(iter.hasNext()) {
                String line = (String) iter.next(); 
                line = line.trim();
                if(line.length()==0) {
                    continue;
                }
                String[] splits = line.split(" ");
                if(splits.length!=2) {
                    logger.severe("bad line: "+line);
                    continue;
                }
                try {
                    Map alist = (Map)SerializationUtils.deserialize(
                        Base64.decodeBase64(splits[1].getBytes("UTF8")));
                    System.out.println(
                        splits[0] + " " + ArchiveUtils.prettyString(alist));
                } catch (RuntimeException e) {
                    logger.log(Level.SEVERE,"problem with line: "+line, e);
                }
                count++;
            }
            IOUtils.closeQuietly(br);
        } else {
            // open the source env history DB, copying entries to target env
            EnhancedEnvironment sourceEnv = setupEnvironment(source);
            StoredClassCatalog sourceClassCatalog = sourceEnv.getClassCatalog();
            Database sourceHistoryDB = sourceEnv.openDatabase(
                    null,URI_HISTORY_DBNAME,historyDatabaseConfig().toDatabaseConfig());
            StoredSortedMap sourceHistoryMap = new StoredSortedMap(sourceHistoryDB,
                    new StringBinding(), new SerialBinding(sourceClassCatalog,
                            Map.class), true);
            Iterator iter = sourceHistoryMap.entrySet().iterator();
            while(iter.hasNext()) {
                Entry item = (Entry) iter.next(); 
//                Map alist = (Map)item.getValue();
                Map alist = (Map) sourceHistoryMap.get(item.getKey());
                System.out.println(item.getKey() + " " + ArchiveUtils.prettyString(alist));
                count++;
            }
            StoredIterator.close(iter);
            sourceHistoryDB.close();
            sourceEnv.close();
        }
        
        System.out.println(count+" records dumped from "+source);
    }
    
    public static EnhancedEnvironment setupEnvironment(File env) throws DatabaseException {
        EnvironmentConfig envConfig = new EnvironmentConfig();
        envConfig.setAllowCreate(true);
        return new EnhancedEnvironment(env, envConfig);
    }
}