/* RecoveryJournal
 *
 * $Id$
 *
 * Created on Jul 20, 2004
 *
 * Copyright (C) 2004 Internet Archive.
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
package org.archive.crawler.frontier;

import java.io.BufferedReader;
import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.httpclient.URIException;
import org.archive.checkpointing.Checkpointable;
import org.archive.crawler.datamodel.CrawlURI;
import org.archive.crawler.framework.CrawlController;
import org.archive.crawler.framework.Frontier;
import org.archive.io.CrawlerJournal;
import org.archive.modules.deciderules.DecideRule;
import org.archive.net.UURI;
import org.archive.net.UURIFactory;
import org.json.JSONObject;

/**
 * Helper class for managing a simple Frontier change-events journal which is
 * useful for recovering from crawl problems.
 * 
 * By replaying the journal into a new Frontier, its state (at least with
 * respect to URIs alreadyIncluded and in pending queues) will match that of the
 * original Frontier, allowing a pseudo-resume of a previous crawl, at least as
 * far as URI visitation/coverage is concerned.
 * 
 * @author gojomo
 */
public class FrontierJournal extends CrawlerJournal implements Checkpointable {
    private static final Logger LOGGER = Logger.getLogger(
            FrontierJournal.class.getName());
    
    public static final String LOGNAME_RECOVER = "recover.gz";

    public final static String F_ADD = "F+ ";
    public final static String F_EMIT = "Fe ";
    public final static String F_DISREGARD = "Fd ";
    public final static String F_RESCHEDULE = "Fr ";
    public final static String F_SUCCESS = "Fs ";
    public final static String F_FAILURE = "Ff ";
    
    //  show recovery progress every this many lines
    private final static int PROGRESS_INTERVAL = 1000000; 
    
    // once this many URIs are queued during recovery, allow 
    // crawl to begin, while enqueuing of other URIs from log
    // continues in background
    private static final long ENOUGH_TO_START_CRAWLING = 100000;

    /**
     * Create a new recovery journal at the given location
     * 
     * @param path Directory to make the recovery  journal in.
     * @param filename Name to use for recovery journal file.
     * @throws IOException
     */
    public FrontierJournal(String path, String filename)
    throws IOException {
        super(path,filename);
        timestamp_interval = 10000;
    } 


    public synchronized void added(CrawlURI curi) {
        accumulatingBuffer.length(0);
        this.accumulatingBuffer.append(F_ADD).
            append(curi.toString()).
            append(" "). 
            append(curi.getPathFromSeed()).
            append(" ").
            append(curi.flattenVia());
        writeLine(accumulatingBuffer);
    }

    public void finishedSuccess(CrawlURI curi) {
        finishedSuccess(curi.toString());
    }
    
    public void finishedSuccess(UURI uuri) {
        finishedSuccess(uuri.toString());
    }
    
    protected void finishedSuccess(String uuri) {
        writeLine(F_SUCCESS, uuri);
    }

    public void emitted(CrawlURI curi) {
        writeLine(F_EMIT, curi.toString());

    }

    public void finishedFailure(CrawlURI curi) {
        finishedFailure(curi.toString());
    }
    
    public void finishedDisregard(CrawlURI curi) {
        writeLine(F_DISREGARD, curi.toString());
    }
    
    public void finishedFailure(UURI uuri) {
        finishedFailure(uuri.toString());
    }
    
    public void finishedFailure(String u) {
        writeLine(F_FAILURE, u);
    }

    public void rescheduled(CrawlURI curi) {
        writeLine(F_RESCHEDULE, curi.toString());
    }

    
    /**
     * Utility method for scanning a recovery journal and applying it to
     * a Frontier.
     * 
     * @param params JSONObject of import parameters; see Frontier.importURIS()
     * @param controller CrawlController of crawl to update
     * @throws IOException
     * 
     * @see org.archive.crawler.framework.Frontier#importURIs(String)
     */
    public static void importRecoverLog(final JSONObject params, final CrawlController controller)
    throws IOException {
        String path = params.optString("path");
        if (path == null) {
            throw new IllegalArgumentException("Passed source file is null.");
        }
        final File source = new File(path);
        LOGGER.info("recovering frontier completion state from "+source);
        
        // first, fill alreadyIncluded with successes (and possibly failures),
        // and count the total lines
        final int lines =
            importCompletionInfoFromLog(source, controller, params);
        
        LOGGER.info("finished completion state; recovering queues from " +
            source);

        // now, re-add anything that was in old frontier and not already
        // registered as finished. Do this in a separate thread that signals
        // this thread once ENOUGH_TO_START_CRAWLING URIs have been queued. 
        final CountDownLatch recoveredEnough = new CountDownLatch(1);
        new Thread(new Runnable() {
            public void run() {
                importQueuesFromLog(source, controller, params, lines, 
                        recoveredEnough);
            }
        }, "queuesRecoveryThread").start();
        
        try {
            // wait until at least ENOUGH_TO_START_CRAWLING URIs queued
            recoveredEnough.await();
        } catch (InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }
    
    /**
     * Import just the SUCCESS (and possibly FAILURE) URIs from the given
     * recovery log into the frontier as considered included. 
     * 
     * @param source recovery log file to use
     * @param controller CrawlController of crawl to update
     * @param retainFailures whether failure ('Ff') URIs should count as done
     * @return number of lines in recovery log (for reference)
     * @throws IOException
     */
    private static int importCompletionInfoFromLog(File source, 
            CrawlController controller, JSONObject params) throws IOException {
        // Scan log for 'Fs' (+maybe 'Ff') lines: add as 'alreadyIncluded'
        boolean includeSuccesses = !params.isNull("includeSuccesses");
        boolean includeFailures = !params.isNull("includeFailures");
        boolean includeScheduleds = !params.isNull("includeScheduleds");
        boolean scopeIncludes = !params.isNull("scopeIncludes");
        
        Frontier frontier = controller.getFrontier(); 
        DecideRule scope = (scopeIncludes) ? frontier.getScope() : null;
        FrontierJournal newJournal = frontier.getFrontierJournal();
        
        BufferedReader br = getBufferedReader(source);
        String read;
        int lines = 0; 
        try {
            while ((read = br.readLine())!=null) {
                lines++;
                if(read.length()<4) {
                    continue;
                }
                String lineType = read.substring(0, 3);
                if(includeSuccesses && F_SUCCESS.equals(lineType) 
                        || includeFailures && F_FAILURE.equals(lineType) 
                        || includeScheduleds && F_ADD.equals(lineType)) {
                    String s = read.subSequence(3,read.length()).toString();
                    try {
                        UURI u = UURIFactory.getInstance(s);
                        if(scope!=null) {
                            CrawlURI caUri = CrawlURI.fromHopsViaString(read.substring(3));
                            //TODO:SPRINGY
///                            caUri.setStateProvider(controller.getSheetManager());
                            // skip out-of-scope URIs if so configured
                            if(!scope.accepts(caUri)) {
                                continue;
                            }
                        }
                        frontier.considerIncluded(u);
                        if (newJournal != null) {
                            newJournal.writeLine(lineType, u.toString());
                        }
                    } catch (URIException e) {
                        e.printStackTrace();
                    }
                }
                if((lines%PROGRESS_INTERVAL)==0) {
                    // every 1 million lines, print progress
                    LOGGER.info(
                            "at line " + lines 
                            + " alreadyIncluded count = " +
                            frontier.discoveredUriCount());
                }
            }
        } catch (EOFException e) {
            // expected in some uncleanly-closed recovery logs; ignore
        } finally {
            br.close();
        }
        return lines;
    }

    /**
     * Import all ADDs from given recovery log into the frontier's queues
     * (excepting those the frontier drops as already having been included)
     * 
     * @param source recovery log file to use
     * @param controller CrawlController of crawl to update
     * @param params Map of options to apply
     * @param enough latch signalling 'enough' URIs queued to begin crawling
     */
    private static void importQueuesFromLog(File source, CrawlController controller,
            JSONObject params, int lines, CountDownLatch enough) {
        BufferedReader br;
        String read;
        Frontier frontier = controller.getFrontier(); 
        long queuedAtStart = frontier.queuedUriCount();
        long queuedDuringRecovery = 0;
        int qLines = 0;
        
        boolean scheduleSuccesses = !params.isNull("scheduleSuccesses");
        boolean scheduleFailures = !params.isNull("scheduleFailures");
        boolean scheduleScheduleds = !params.isNull("scheduleScheduleds");
        boolean scopeScheduleds = !params.isNull("scopeScheduleds");
        boolean forceRevisit = !params.isNull("forceRevisit");
        
        DecideRule scope = (scopeScheduleds) ? frontier.getScope() : null;
        
        try {
            // Scan log for all 'F+' lines: if not alreadyIncluded, schedule for
            // visitation
            br = getBufferedReader(source);
            try {
                while ((read = br.readLine())!=null) {
                    qLines++;
                    if(read.length()<4) {
                        continue;
                    }
                    String lineType = read.substring(0, 3);
                    if(scheduleSuccesses && F_SUCCESS.equals(lineType) 
                            || scheduleFailures && F_FAILURE.equals(lineType) 
                            || scheduleScheduleds && F_ADD.equals(lineType)) {
                        
                        try {
                            CrawlURI caUri = CrawlURI.fromHopsViaString(read.substring(3));
                            
                            //TODO:SPRINGY
//                            caUri.setStateProvider(controller.getSheetManager());
                            if(scope!=null) {
                                // skip out-of-scope URIs if so configured
                                if(!scope.accepts(caUri)) {
                                    continue;
                                }
                            }
                            
                            caUri.setForceFetch(forceRevisit);
                            
                            frontier.schedule(caUri);
                            
                            queuedDuringRecovery =
                                frontier.queuedUriCount() - queuedAtStart;
                            if(((queuedDuringRecovery + 1) %
                                    ENOUGH_TO_START_CRAWLING) == 0) {
                                enough.countDown();
                            }
                        } catch (URIException e) {
                            LOGGER.log(Level.WARNING, "bad URI during " +
                                "log-recovery of queue contents ",e);
                            // and continue...
                        } catch (RuntimeException e) {
                            LOGGER.log(Level.SEVERE, "exception during " +
                                    "log-recovery of queue contents ",e);
                            // and continue, though this may be risky
                            // if the exception wasn't a trivial NPE 
                            // or wrapped interrupted-exception...
                        }
                    }
                    if((qLines%PROGRESS_INTERVAL)==0) {
                        // every 1 million lines, print progress
                        LOGGER.info(
                                "through line " 
                                + qLines + "/" + lines 
                                + " queued count = " +
                                frontier.queuedUriCount());
                    }
                }
            } catch (EOFException e) {
                // no problem: untidy end of recovery journal
            } finally {
        	    br.close(); 
            }
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        LOGGER.info("finished recovering frontier from "+source+" "
                +qLines+" lines processed");
        enough.countDown();
    }
}
