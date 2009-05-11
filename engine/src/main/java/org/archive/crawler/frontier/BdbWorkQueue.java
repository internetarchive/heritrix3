/* BdbWorkQueue
 * 
 * Created on Dec 24, 2004
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

import java.io.IOException;
import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.archive.crawler.datamodel.CrawlURI;
import org.archive.util.ArchiveUtils;
import org.archive.util.IoUtils;

import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.DatabaseException;


/**
 * One independent queue of items with the same 'classKey' (eg host).
 * @author gojomo
 */
public class BdbWorkQueue extends WorkQueue
implements Serializable {
    private static final long serialVersionUID = 1L;
    private static Logger LOGGER =
        Logger.getLogger(BdbWorkQueue.class.getName());


    /**
     * All items in this queue have this same 'origin'
     * prefix to their keys.
     */
    private byte[] origin;

    /**
     * Create a virtual queue inside the given BdbMultipleWorkQueues 
     * 
     * @param classKey
     */
    public BdbWorkQueue(String classKey, BdbFrontier frontier) {
        super(classKey);
        this.origin = BdbMultipleWorkQueues.calculateOriginKey(classKey);
        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.fine(getPrefixClassKey(this.origin) + " " + classKey);
        }
        // add the queue-front 'cap' entry; see...
        // http://sourceforge.net/tracker/index.php?func=detail&aid=1262665&group_id=73833&atid=539102
        frontier.getWorkQueues().addCap(origin);
    }

    protected long deleteMatchingFromQueue(final WorkQueueFrontier frontier,
            final String match) throws IOException {
        try {
            final BdbMultipleWorkQueues queues = ((BdbFrontier) frontier)
                .getWorkQueues();
            return queues.deleteMatchingFromQueue(match, classKey,
                new DatabaseEntry(origin));
        } catch (DatabaseException e) {
            throw IoUtils.wrapAsIOException(e);
        }
    }

    protected void deleteItem(final WorkQueueFrontier frontier,
            final CrawlURI peekItem) throws IOException {
        try {
            final BdbMultipleWorkQueues queues = ((BdbFrontier) frontier)
                .getWorkQueues();
             queues.delete(peekItem);
        } catch (DatabaseException e) {
            e.printStackTrace();
            throw IoUtils.wrapAsIOException(e);
        }
    }

    protected CrawlURI peekItem(final WorkQueueFrontier frontier)
    throws IOException {
        final BdbMultipleWorkQueues queues = ((BdbFrontier) frontier)
            .getWorkQueues();
        DatabaseEntry key = new DatabaseEntry(origin);
        CrawlURI curi = null;
        int tries = 1;
        while(true) {
            try {
                curi = queues.get(key);
            } catch (DatabaseException e) {
                LOGGER.log(Level.SEVERE,"peekItem failure; retrying",e);
            }
            
            // ensure CrawlURI, if any,  came from acceptable range: 
            if(!ArchiveUtils.startsWith(key.getData(),origin)) {
                LOGGER.severe(
                    "inconsistency: "+classKey+"("+
                    getPrefixClassKey(origin)+") with " + getCount() + " items gave "
                    + curi +"("+getPrefixClassKey(key.getData()));
                // clear curi to allow retry
                curi = null; 
                // reset key to original origin for retry
                key.setData(origin);
            }
            
            if (curi!=null) {
                // success
                break;
            }
            
            if (tries>3) {
                LOGGER.severe("no item where expected in queue "+classKey);
                break;
            }
            tries++;
            LOGGER.severe("Trying get #" + Integer.toString(tries)
                    + " in queue " + classKey + " with " + getCount()
                    + " items using key "
                    + getPrefixClassKey(key.getData()));
        }
 
        return curi;
    }

    protected void insertItem(final WorkQueueFrontier frontier,
            final CrawlURI curi, boolean overwriteIfPresent) throws IOException {
        try {
            final BdbMultipleWorkQueues queues = ((BdbFrontier) frontier)
                .getWorkQueues();
            queues.put(curi, overwriteIfPresent);
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.fine("Inserted into " + getPrefixClassKey(this.origin) +
                    " (count " + Long.toString(getCount())+ "): " +
                        curi.toString());
            }
        } catch (DatabaseException e) {
            throw IoUtils.wrapAsIOException(e);
        }
    }
    
    /**
     * @param byteArray Byte array to get hex string of.
     * @return Hex string of passed in byte array (Used logging
     * key-prefixes).
     */
    protected static String getPrefixClassKey(final byte [] byteArray) {
        int zeroIndex = 0;
        while(byteArray[zeroIndex]!=0) {
            zeroIndex++;
        }
        try {
            return new String(byteArray,0,zeroIndex,"UTF-8");
        } catch (UnsupportedEncodingException e) {
            // should be impossible; UTF-8 always available
            e.printStackTrace();
            return e.getMessage();
        }
    }
}