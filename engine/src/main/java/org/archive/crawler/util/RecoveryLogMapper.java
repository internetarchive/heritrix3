/* SeedUrlNotFoundException
 *
 * $Id$
 *
 * Created on Mar 9, 2005
 *
 * Copyright (C) 2005 Mike Schwartz.
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

/**
 * Parses a Heritrix recovery log file (recover.gz), and builds maps
 * that allow a caller to look up any seed URL and get back an Iterator of all
 * URLs successfully crawled from given seed.
 *
 * Also allows lookup on any crawled
 * URL to find the seed URL from which the crawler reached that URL (through 1
 * or more discovered URL hops, which are collapsed in this lookup).
 * 
 * <p>This code creates some fairly large collections (proprotionate in size to
 * # discovered URLs) so make sure you allocate
 * it a large heap to work in. It also takes a while to process a recover log.
 * <p>See {@link #main()} method at end for test/demo code.
 * @author Mike Schwartz, schwartz at CodeOnTheRoad dot com
 */
package org.archive.crawler.util;

import org.archive.crawler.frontier.FrontierJournal;
import org.archive.util.ArchiveUtils;

import java.io.File;
import java.io.LineNumberReader;
import java.io.PrintWriter;
import java.io.FileOutputStream;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

public class RecoveryLogMapper {
    private static final char LOG_LINE_START_CHAR = FrontierJournal.F_ADD
            .charAt(0);

    private static final Logger logger = Logger
            .getLogger(RecoveryLogMapper.class.getName());

    private PrintWriter seedNotFoundPrintWriter = null;

    /**
     * Tracks seed for each crawled URL
     */
    private Map<String, String> crawledUrlToSeedMap = new HashMap<String, String>();

    /**
     * Maps seed URLs to Set of discovered URLs
     */
    private Map<String, Set<String>> seedUrlToDiscoveredUrlsMap = new HashMap<String, Set<String>>();

    /**
     * Tracks which URLs were successfully crawled
     */
    private Set<String> successfullyCrawledUrls = new HashSet<String>();

    /**
     * Normal constructor - if encounter not-found seeds while loading
     * recoverLogFileName, will throw throw SeedUrlNotFoundException. Use
     * {@link #RecoveryLogMapper(String)} if you want to just log such cases and
     * keep going. (Those should not happen if the recover log is written
     * correctly, but we see them in pratice.)
     * 
     * @param recoverLogFileName
     * @throws java.io.FileNotFoundException
     * @throws java.io.IOException
     * @throws SeedUrlNotFoundException
     */
    public RecoveryLogMapper(String recoverLogFileName)
            throws java.io.FileNotFoundException, java.io.IOException,
            SeedUrlNotFoundException {
        load(recoverLogFileName);
    }

    /**
     * Constructor to use if you want to allow not-found seeds, logging them to
     * seedNotFoundLogFileName. In contrast, {@link #RecoveryLogMapper(String)}
     * will throw SeedUrlNotFoundException when a seed isn't found.
     * 
     * @param recoverLogFileName
     * @param seedNotFoundLogFileName
     */
    public RecoveryLogMapper(String recoverLogFileName,
            String seedNotFoundLogFileName)
            throws java.io.FileNotFoundException, java.io.IOException,
            SeedUrlNotFoundException {
        seedNotFoundPrintWriter = new PrintWriter(new FileOutputStream(
                seedNotFoundLogFileName));
        load(recoverLogFileName);
    }

    protected void load(String recoverLogFileName)
            throws java.io.FileNotFoundException, java.io.IOException,
            SeedUrlNotFoundException {
        LineNumberReader reader = new LineNumberReader(ArchiveUtils
                .getBufferedReader(new File(recoverLogFileName)));
        String curLine = null;
        while ((curLine = reader.readLine()) != null) {
            if (curLine.length() == 0
                    || curLine.charAt(0) != LOG_LINE_START_CHAR) {
                continue;
            }
            String args[] = curLine.split("\\s+");
            int curLineNumWords = args.length;
            String firstUrl = args[1];
            // Ignore DNS log entries
            if (firstUrl.startsWith("dns:")) {
                continue;
            }
            if (curLine.startsWith(FrontierJournal.F_ADD)) {
                // Seed URL
                if (curLineNumWords == 2) {
                    if (logger.isLoggable(Level.FINE)) {
                        logger.fine("F_ADD with 2 words --> seed URL ("
                                + firstUrl + ")");
                    }
                    // Add seed the first time we find it
                    if (seedUrlToDiscoveredUrlsMap.get(firstUrl) == null) {
                        seedUrlToDiscoveredUrlsMap.put(firstUrl,
                                new HashSet<String>());
                    }
                } else {
                    // URL found via an earlier seeded / discovered URL
                    // Look for the seed from which firstUrlString came, so
                    // we can collapse new URLString back to it
                    String viaUrl = args[curLineNumWords - 1];
                    if (logger.isLoggable(Level.FINE)) {
                        logger.fine("F_ADD with 3+ words --> new URL "
                                + firstUrl + " via URL " + viaUrl);
                    }
                    String seedForFirstUrl = (String) crawledUrlToSeedMap
                            .get(viaUrl);
                    // viaUrlString is a seed URL
                    if (seedForFirstUrl == null) {
                        if (logger.isLoggable(Level.FINE)) {
                            logger.fine("\tvia URL is a seed");
                        }
                        crawledUrlToSeedMap.put(firstUrl, viaUrl);
                        seedForFirstUrl = viaUrl;
                    } else {
                        if (logger.isLoggable(Level.FINE)) {
                            logger.fine("\tvia URL discovered via seed URL "
                                    + seedForFirstUrl);
                        }
                        // Collapse
                        crawledUrlToSeedMap.put(firstUrl, seedForFirstUrl);
                    }
                    Set<String> theSeedUrlList = seedUrlToDiscoveredUrlsMap
                            .get(seedForFirstUrl);
                    if (theSeedUrlList == null) {
                        String message = "recover log " + recoverLogFileName
                                + " at line " + reader.getLineNumber()
                                + " listed F+ URL (" + viaUrl
                                + ") for which found no seed list.";
                        if (seedNotFoundPrintWriter != null) {
                            seedNotFoundPrintWriter.println(message);
                        } else {
                            throw new SeedUrlNotFoundException(message);
                        }
                    } else {
                        theSeedUrlList.add(firstUrl);
                    }
                }
            } else if (curLine.startsWith(FrontierJournal.F_SUCCESS)) {
                if (logger.isLoggable(Level.FINE)) {
                    logger.fine("F_SUCCESS for URL " + firstUrl);
                }
                successfullyCrawledUrls.add(firstUrl);
            }
        }
        reader.close();
        if (seedNotFoundPrintWriter != null) {
            seedNotFoundPrintWriter.close();
        }
    }

    /**
     * Returns seed for urlString (null if seed not found).
     * 
     * @param urlString
     * @return Seed.
     */
    public String getSeedForUrl(String urlString) {
        return (seedUrlToDiscoveredUrlsMap.get(urlString) != null) ? urlString
                : crawledUrlToSeedMap.get(urlString);
    }

    /**
     * @return Returns the seedUrlToDiscoveredUrlsMap.
     */
    public Map<String, Set<String>> getSeedUrlToDiscoveredUrlsMap() {
        return this.seedUrlToDiscoveredUrlsMap;
    }

    /**
     * @return Returns the successfullyCrawledUrls.
     */
    public Set<String> getSuccessfullyCrawledUrls() {
        return this.successfullyCrawledUrls;
    }

    /**
     * @return Returns the logger.
     */
    public static Logger getLogger() {
        return logger;
    }

    private class SuccessfullyCrawledURLsIterator implements Iterator<String> {
        private String nextValue = null;

        private Iterator<String> discoveredUrlsIterator;

        public SuccessfullyCrawledURLsIterator(String seedUrlString)
                throws SeedUrlNotFoundException {
            Set<String> discoveredUrlList = (Set<String>) getSeedUrlToDiscoveredUrlsMap()
                    .get(seedUrlString);
            if (discoveredUrlList == null) {
                throw new SeedUrlNotFoundException("Seed URL " + seedUrlString
                        + "  not found in seed list");
            }
            discoveredUrlsIterator = discoveredUrlList.iterator();
        }

        /**
         * Idempotent method (because of null check on nextValue).
         */
        private void populateNextValue() {
            while (nextValue == null & discoveredUrlsIterator.hasNext()) {
                String curDiscoveredUrl = discoveredUrlsIterator.next();
                boolean succCrawled = getSuccessfullyCrawledUrls().contains(
                        curDiscoveredUrl);
                if (getLogger().isLoggable(Level.FINE)) {
                    getLogger().fine(
                            "populateNextValue: curDiscoveredUrl="
                                    + curDiscoveredUrl + ", succCrawled="
                                    + succCrawled);
                }
                if (succCrawled)
                    nextValue = curDiscoveredUrl;
            }
        }

        public boolean hasNext() {
            populateNextValue();
            return (nextValue != null);
        }

        public String next() {
            populateNextValue();
            String returnValue = nextValue;
            nextValue = null;
            return returnValue;
        }

        /**
         * Remove operation is unsupported in this Iterator (will throw
         * UnsupportedOperationException if called).
         */
        public void remove() {
            throw new UnsupportedOperationException(
                    "SuccessfullyCrawledURLsIterator.remove: not supported.");
        }
    }

    public Iterator<String> getIteratorOfURLsSuccessfullyCrawledFromSeedUrl(
            String seedUrlString) throws SeedUrlNotFoundException {
        return new SuccessfullyCrawledURLsIterator(seedUrlString);
    }

    public Collection<String> getSeedCollection() {
        return seedUrlToDiscoveredUrlsMap.keySet();
    }

    public static void main(String args[]) {
        if (args.length < 1) {
            System.out.println("Usage: RecoveryLogMapper recoverLogFileName");
            Runtime.getRuntime().exit(-1);
        }
        String recoverLogFileName = args[0];
        try {
            RecoveryLogMapper myRecoveryLogMapper = new RecoveryLogMapper(
                    recoverLogFileName);
            for (String curSeedUrl : myRecoveryLogMapper.getSeedCollection()) {
                System.out.println("URLs successfully crawled from seed URL "
                        + curSeedUrl);
                Iterator<String> iteratorOfUrlsCrawledFromSeedUrl = myRecoveryLogMapper
                        .getIteratorOfURLsSuccessfullyCrawledFromSeedUrl(curSeedUrl);
                while (iteratorOfUrlsCrawledFromSeedUrl.hasNext()) {
                    String curCrawledUrlString = (String) iteratorOfUrlsCrawledFromSeedUrl
                            .next();
                    System.out.println("    -> " + curCrawledUrlString);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
