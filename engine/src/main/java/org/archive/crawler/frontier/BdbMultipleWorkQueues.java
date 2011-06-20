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
package org.archive.crawler.frontier;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import javax.management.openmbean.CompositeData;
import javax.management.openmbean.CompositeDataSupport;
import javax.management.openmbean.OpenDataException;

import org.apache.commons.collections.Closure;
import org.archive.bdb.KryoBinding;
import org.archive.modules.CrawlURI;
import org.archive.util.ArchiveUtils;

import com.google.common.base.Charsets;
import com.sleepycat.bind.EntryBinding;
import com.sleepycat.bind.serial.StoredClassCatalog;
import com.sleepycat.je.Cursor;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.OperationStatus;
import com.sleepycat.util.RuntimeExceptionWrapper;


/**
 * A BerkeleyDB-database-backed structure for holding ordered
 * groupings of CrawlURIs. Reading the groupings from specific
 * per-grouping (per-classKey/per-Host) starting points allows
 * this to act as a collection of independent queues. 
 * 
 * <p>For how the bdb keys are made, see {@link #calculateInsertKey(CrawlURI)}.
 * 
 * <p>TODO: refactor, improve naming.
 * 
 * @author gojomo
 */
public class BdbMultipleWorkQueues {
	private static final long serialVersionUID = 1L;
	
    private static final Logger LOGGER =
        Logger.getLogger(BdbMultipleWorkQueues.class.getName());
    
    /** Database holding all pending URIs, grouped in virtual queues */
    private Database pendingUrisDB = null;
    
    /**  Supporting bdb serialization of CrawlURIs */
    private EntryBinding<CrawlURI> crawlUriBinding;

    /**
     * Create the multi queue in the given environment. 
     * 
     * @param env bdb environment to use
     * @param classCatalog Class catalog to use.
     * @param recycle True if we are to reuse db content if any.
     * @throws DatabaseException
     */
    public BdbMultipleWorkQueues(Database db,
        StoredClassCatalog classCatalog)
    throws DatabaseException {
        this.pendingUrisDB = db;
        crawlUriBinding =
              new KryoBinding<CrawlURI>(CrawlURI.class);
//            new RecyclingSerialBinding<CrawlURI>(classCatalog, CrawlURI.class);
//            new BenchmarkingBinding<CrawlURI>(new EntryBinding[] {
//                new KryoBinding<CrawlURI>(CrawlURI.class,true),
//                new KryoBinding<CrawlURI>(CrawlURI.class,false),                    
//                new RecyclingSerialBinding<CrawlURI>(classCatalog, CrawlURI.class),
//            });
            
    }

    /**
     * Delete all CrawlURIs matching the given expression.
     * 
     * @param match
     * @param queue
     * @param headKey
     * @return count of deleted items
     * @throws DatabaseException
     * @throws DatabaseException
     */
    public long deleteMatchingFromQueue(String match, String queue,
            DatabaseEntry headKey) throws DatabaseException {
        long deletedCount = 0;
        Pattern pattern = Pattern.compile(match);
        DatabaseEntry key = headKey;
        DatabaseEntry value = new DatabaseEntry();
        Cursor cursor = null;
        try {
            cursor = pendingUrisDB.openCursor(null, null);
            OperationStatus result = cursor.getSearchKeyRange(headKey,
                    value, null);

            while (result == OperationStatus.SUCCESS) {
                if(value.getData().length>0) {
                    CrawlURI curi = (CrawlURI) crawlUriBinding
                            .entryToObject(value);
                    if (!curi.getClassKey().equals(queue)) {
                        // rolled into next queue; finished with this queue
                        break;
                    }
                    if (pattern.matcher(curi.toString()).matches()) {
                        cursor.delete();
                        deletedCount++;
                    }
                }
                result = cursor.getNext(key, value, null);
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        return deletedCount;
    }
    
    /**
     * @param m marker or null to start with first entry
     * @param maxMatches
     * @return list of matches starting from marker position
     * @throws DatabaseException
     */
    public CompositeData getFrom(
            String m, 
            int maxMatches, 
            Pattern pattern, 
            boolean verbose) 
    throws DatabaseException {
        int matches = 0;
        int tries = 0;
        ArrayList<String> results = new ArrayList<String>(maxMatches);
        
        DatabaseEntry key;
        if (m == null) {
            key = getFirstKey();
        } else {
            byte[] marker = m.getBytes(); // = FrontierJMXTypes.fromString(m);
            key = new DatabaseEntry(marker);
        }

        DatabaseEntry value = new DatabaseEntry();
        
        Cursor cursor = null;
        OperationStatus result = null;
        try {
            cursor = pendingUrisDB.openCursor(null,null);
            result = cursor.getSearchKey(key, value, null);
            
            while(matches < maxMatches && result == OperationStatus.SUCCESS) {
                if(value.getData().length>0) {
                    CrawlURI curi = (CrawlURI) crawlUriBinding.entryToObject(value);
                    if(pattern.matcher(curi.toString()).matches()) {
                        if (verbose) {
                            results.add("[" + curi.getClassKey() + "] " 
                                    + curi.shortReportLine());
                        } else {
                            results.add(curi.toString());
                        }
                        matches++;
                    }
                    tries++;
                }
                result = cursor.getNext(key,value,null);
            }
        } finally {
            if (cursor !=null) {
                cursor.close();
            }
        }
        
        if(result != OperationStatus.SUCCESS) {
            // end of scan
            m = null;
        } else {
            m = new String(key.getData()); // = FrontierJMXTypes.toString(key.getData());
        }
        
        String[] arr = results.toArray(new String[results.size()]);
        CompositeData cd;
        try {
            cd = new CompositeDataSupport(
                    /*FrontierJMXTypes.URI_LIST_DATA*/ null,
                    new String[] { "list", "marker" },
                    new Object[] { arr, m });
        } catch (OpenDataException e) {
            throw new IllegalStateException(e);
        }
        return cd;
    }
    
    /**
     * @return the key to the first item in the database
     * @throws DatabaseException
     */
    protected DatabaseEntry getFirstKey() throws DatabaseException {
        DatabaseEntry key = new DatabaseEntry();
        DatabaseEntry value = new DatabaseEntry();
        Cursor cursor = pendingUrisDB.openCursor(null,null);
        OperationStatus status = cursor.getNext(key,value,null);
        cursor.close();
        if(status == OperationStatus.SUCCESS) {
            return key;
        }
        return null;
    }
    
    /**
     * Get the next nearest item after the given key. Relies on 
     * external discipline -- we'll look at the queues count of how many
     * items it has -- to avoid asking for something from a
     * range where there are no associated items --
     * otherwise could get first item of next 'queue' by mistake. 
     * 
     * <p>TODO: hold within a queue's range
     * 
     * @param headKey Key prefix that demarks the beginning of the range
     * in <code>pendingUrisDB</code> we're interested in.
     * @return CrawlURI.
     * @throws DatabaseException
     */
    public CrawlURI get(DatabaseEntry headKey)
    throws DatabaseException {
        DatabaseEntry result = new DatabaseEntry();
        
        // From Linda Lee of sleepycat:
        // "You want to check the status returned from Cursor.getSearchKeyRange
        // to make sure that you have OperationStatus.SUCCESS. In that case,
        // you have found a valid data record, and result.getData()
        // (called by internally by the binding code, in this case) will be
        // non-null. The other possible status return is
        // OperationStatus.NOTFOUND, in which case no data record matched
        // the criteria. "
        OperationStatus status = getNextNearestItem(headKey, result);
        CrawlURI retVal = null;
        if (status != OperationStatus.SUCCESS) {
            LOGGER.severe("See '1219854 NPE je-2.0 "
                    + "entryToObject...'. OperationStatus "
                    + " was not SUCCESS: "
                    + status
                    + ", headKey "
                    + BdbWorkQueue.getPrefixClassKey(headKey.getData()));
            return null;
        }
       
        try {
            retVal = (CrawlURI)crawlUriBinding.entryToObject(result);
        } catch (ClassCastException cce) {
            Object obj = crawlUriBinding.entryToObject(result);
            LOGGER.log(Level.SEVERE,
                    "see [#HER-1283]: deserialized " + obj.getClass() 
                    + " has ClassLoader " 
                    + obj.getClass().getClassLoader().getClass(),
                    cce);
            return null; 
        } catch (RuntimeExceptionWrapper rw) {
            LOGGER.log(
                Level.SEVERE,
                "expected object missing in queue " +
                BdbWorkQueue.getPrefixClassKey(headKey.getData()),
                rw);
            return null; 
        }
        retVal.setHolderKey(headKey);
        return retVal;
    }
    
    protected OperationStatus getNextNearestItem(DatabaseEntry headKey,
            DatabaseEntry result) throws DatabaseException {
        Cursor cursor = null;
        OperationStatus status;
        try {
            cursor = this.pendingUrisDB.openCursor(null, null);
            
            // get cap; headKey at this point should always point to 
            // a queue-beginning cap entry (zero-length value)
            status = cursor.getSearchKey(headKey, result, null);
            if (status != OperationStatus.SUCCESS) {
                LOGGER.severe("bdb queue cap missing: " 
                        + status.toString() + " "  + new String(headKey.getData()));
                return status;
            }
            if (result.getData().length > 0) {
                LOGGER.severe("bdb queue has nonzero size: " 
                        + result.getData().length);
                return OperationStatus.KEYEXIST;
            }
            // get next item (real first item of queue)
            status = cursor.getNext(headKey,result,null);
        } finally { 
            if(cursor!=null) {
                cursor.close();
            }
        }
        return status;
    }


    /**
     * Put the given CrawlURI in at the appropriate place. 
     * 
     * @param curi
     * @throws DatabaseException
     */
    public void put(CrawlURI curi, boolean overwriteIfPresent) 
    throws DatabaseException {
        DatabaseEntry insertKey = (DatabaseEntry)curi.getHolderKey();
        if (insertKey == null) {
            insertKey = calculateInsertKey(curi);
            curi.setHolderKey(insertKey);
        }
        DatabaseEntry value = new DatabaseEntry();
        crawlUriBinding.objectToEntry(curi, value);
        // Output tally on avg. size if level is FINE or greater.
        if (LOGGER.isLoggable(Level.FINE)) {
            tallyAverageEntrySize(curi, value);
        }
        OperationStatus status;
        if(overwriteIfPresent) {
            status = pendingUrisDB.put(null, insertKey, value);
        } else {
            status = pendingUrisDB.putNoOverwrite(null, insertKey, value);
        }
        
        if (status!=OperationStatus.SUCCESS) {
            LOGGER.log(Level.SEVERE,"URI enqueueing failed; "+status+ " "+curi, new RuntimeException());
        }
    }
    
    private long entryCount = 0;
    private long entrySizeSum = 0;
    private int largestEntry = 0;
    
    /**
     * Log average size of database entry.
     * @param curi CrawlURI this entry is for.
     * @param value Database entry value.
     */
    private synchronized void tallyAverageEntrySize(CrawlURI curi,
            DatabaseEntry value) {
        entryCount++;
        int length = value.getData().length;
        entrySizeSum += length;
        int avg = (int) (entrySizeSum/entryCount);
        if(entryCount % 1000 == 0) {
            LOGGER.fine("Average entry size at "+entryCount+": "+avg);
        }
        if (length>largestEntry) {
            largestEntry = length; 
            LOGGER.fine("Largest entry: "+length+" "+curi);
            if(length>(2*avg)) {
                LOGGER.fine("excessive?");
            }
        }
    }

    /**
     * Calculate the 'origin' key for a virtual queue of items
     * with the given classKey. This origin key will be a 
     * prefix of the keys for all items in the queue. 
     * 
     * @param classKey String key to derive origin byte key from 
     * @return a byte array key 
     */
    static byte[] calculateOriginKey(String classKey) {
        byte[] classKeyBytes = null;
        int len = 0;
        try {
            classKeyBytes = classKey.getBytes("UTF-8");
            len = classKeyBytes.length;
        } catch (UnsupportedEncodingException e) {
            // should be impossible; all JVMs must support UTF-8
            e.printStackTrace();
        }
        byte[] keyData = new byte[len+1];
        System.arraycopy(classKeyBytes,0,keyData,0,len);
        keyData[len]=0;
        return keyData;
    }
    
    /**
     * Calculate the insertKey that places a CrawlURI in the
     * desired spot. First bytes are always classKey (usu. host)
     * based -- ensuring grouping by host -- terminated by a zero
     * byte. Then 8 bytes of data ensuring desired ordering 
     * within that 'queue' are used. The first byte of these 8 is
     * priority -- allowing 'immediate' and 'soon' items to 
     * sort above regular. Next 1 byte is 'precedence'. Last 6 bytes 
     * are ordinal serial number, ensuring earlier-discovered 
     * URIs sort before later. 
     * 
     * NOTE: Dangers here are:
     * (1) priorities or precedences over 2^7 (signed byte comparison)
     * (2) ordinals over 2^48
     * 
     * Package access & static for testing purposes. 
     * 
     * @param curi
     * @return a DatabaseEntry key for the CrawlURI
     */
    static DatabaseEntry calculateInsertKey(CrawlURI curi) {
        byte[] classKeyBytes = null;
        int len = 0;
        classKeyBytes = curi.getClassKey().getBytes(Charsets.UTF_8);
        len = classKeyBytes.length;
        byte[] keyData = new byte[len+9];
        System.arraycopy(classKeyBytes,0,keyData,0,len);
        keyData[len]=0;
        long ordinalPlus = curi.getOrdinal() & 0x0000FFFFFFFFFFFFL;
        ordinalPlus = 
        	((long)curi.getSchedulingDirective() << 56) | ordinalPlus;
        long precedence = Math.min(curi.getPrecedence(), 127);
        ordinalPlus = 
        	(((precedence) & 0xFFL) << 48) | ordinalPlus;
        ArchiveUtils.longIntoByteArray(ordinalPlus, keyData, len+1);
        return new DatabaseEntry(keyData);
    }
    
    
    static String insertKeyToString(DatabaseEntry holderKey) {
        StringBuilder result = new StringBuilder();
        byte[] data = holderKey.getData();
        int p = findFirstZero(data);
        result.append(new String(data, 0, p));
        
        java.io.ByteArrayInputStream binp = 
            new java.io.ByteArrayInputStream(data, p + 1, data.length);
        java.io.DataInputStream dinp = new java.io.DataInputStream(binp);
        long l = 0;
        try {
            l = dinp.readLong();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        result.append(" blah=").append(l);
        
        return result.toString();
    }
    
    
    private static int findFirstZero(byte[] b) {
        for (int i = 0; i < b.length; i++) {
            if (b[i] == 0) {
                return i;
            }
        }
        return -1;
    }
    
    /**
     * Delete the given CrawlURI from persistent store. Requires
     * the key under which it was stored be available. 
     * 
     * @param item
     * @throws DatabaseException
     */
    public void delete(CrawlURI item) throws DatabaseException {
        OperationStatus status;
        DatabaseEntry de = (DatabaseEntry)item.getHolderKey();
        status = pendingUrisDB.delete(null, de);
        if (status != OperationStatus.SUCCESS) {
            LOGGER.severe("expected item not present: "
                    + item
                    + "("
                    + (new BigInteger(((DatabaseEntry) item.getHolderKey())
                            .getData())).toString(16) + ")");
        }
    }
    
    /**
     * Method used by BdbFrontier during checkpointing.
     * <p>The backing bdbje database has been marked deferred write so we save
     * on writes to disk.  Means no guarantees disk will have whats in memory
     * unless a sync is called (Calling sync on the bdbje Environment is not
     * sufficent).
     * <p>Package access only because only Frontiers of this package would ever
     * need access.
     * @see <a href="http://www.sleepycat.com/jedocs/GettingStartedGuide/DB.html">Deferred Write Databases</a>
     */
    void sync() {
    	if (this.pendingUrisDB == null) {
    		return;
    	}
        try {
            this.pendingUrisDB.sync();
        } catch (DatabaseException e) {
            e.printStackTrace();
        }
    }
    
    /**
     * clean up 
     *
     */
    public void close() {
/*        try {
            this.pendingUrisDB.close();
        } catch (DatabaseException e) {
            e.printStackTrace();
        } */
    }
    

    /**
     * Add a dummy 'cap' entry at the given insertion key. Prevents
     * 'seeks' to queue heads from holding lock on last item of 
     * 'preceding' queue. See:
     * http://sourceforge.net/tracker/index.php?func=detail&aid=1262665&group_id=73833&atid=539102
     * 
     * @param origin key at which to insert the cap
     */
    public void addCap(byte[] origin) {
        try {
            pendingUrisDB.put(null, new DatabaseEntry(origin),
                    new DatabaseEntry(new byte[0]));
        } catch (DatabaseException e) {
            throw new RuntimeException(e);
        }
    }
    
    /**
     * Utility method to perform action for all pending CrawlURI instances.
     * @param c Closure action to perform
     * @throws DatabaseException
     */
    protected void forAllPendingDo(Closure c) throws DatabaseException {
        DatabaseEntry key = new DatabaseEntry();
        DatabaseEntry value = new DatabaseEntry();
        Cursor cursor = pendingUrisDB.openCursor(null, null);
        while (cursor.getNext(key, value, null) == OperationStatus.SUCCESS) {
            if (value.getData().length == 0) {
                continue;
            }
            CrawlURI item = (CrawlURI) crawlUriBinding.entryToObject(value);
            c.execute(item);
        }
        cursor.close(); 
    }
}
