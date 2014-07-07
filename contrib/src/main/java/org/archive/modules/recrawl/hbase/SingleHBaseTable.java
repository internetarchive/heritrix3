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
package org.archive.modules.recrawl.hbase;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.hbase.HTableDescriptor;
import org.apache.hadoop.hbase.NotServingRegionException;
import org.apache.hadoop.hbase.TableNotFoundException;
import org.apache.hadoop.hbase.client.Get;
import org.apache.hadoop.hbase.client.HConnectionManager;
import org.apache.hadoop.hbase.client.HTable;
import org.apache.hadoop.hbase.client.HTableInterface;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.util.Bytes;

/**
 * simple HTable wrapper that shares single instance of HTable among threads.
 * If you only perform get on HTable, this implementation
 * should be good enough. If multiple threads performs Put, {@link HBaseTable}
 * would be more efficient.
 * <p>when HBase I/O fails due to issue with network/region server/zookeeper, this
 * class waits for preset time (see {@link #setReconnectInterval(int)})
 * before trying to reestablish HBase connection. During this hold-ff period, all
 * {@link #get(Get)} and {@link #put(Put)} calls will fail.
 * 
 * @contributor kenji
 */
public class SingleHBaseTable extends HBaseTableBean {
    private static final Log LOG = LogFactory.getLog(SingleHBaseTable.class);

    private HTableInterface table;
    private volatile long tableError;
    private ReentrantReadWriteLock tableUseLock = new ReentrantReadWriteLock();

    boolean autoReconnect = true;

    public boolean isAutoReconnect() {
        return autoReconnect;
    }
    /**
     * if set to {@code true}, HBaseClient tries to reconnect to the HBase master
     * immediately when Put request failed due to connection loss (note {@link #put(Put)}
     * still throws IOException even if autoReconnect is enabled.)
     * @param autoReconnect true to enable auto-reconnect
     */
    public void setAutoReconnect(boolean autoReconnect) {
        this.autoReconnect = autoReconnect;
    }

    protected boolean autoFlush = true;
    /**
     * passed on to HTable's autoFlush property upon creation.
     * @return true for enabling auto-flush.
     */
    public boolean isAutoFlush() {
        return autoFlush;
    }
    public void setAutoFlush(boolean autoFlush) {
        this.autoFlush = autoFlush;
    }

    // default 3 minutes
    private int reconnectInterval = 1000 * 3 * 60;

    public int getReconnectInterval() {
        return reconnectInterval;
    }
    /**
     * set hold-off interval upon communication errors.
     * @param reconnectInterval hold-off interval in milliseconds.
     */
    public void setReconnectInterval(int reconnectInterval) {
        this.reconnectInterval = reconnectInterval;
    }

    // counters

    protected AtomicLong getCount = new AtomicLong();
    // count of GET/PUT failures (i.e. not counting connection failures).
    protected AtomicLong getErrorCount = new AtomicLong();
    protected AtomicLong getSkipCount = new AtomicLong();

    protected AtomicLong putCount = new AtomicLong();
    protected AtomicLong putErrorCount = new AtomicLong();
    protected AtomicLong putSkipCount = new AtomicLong();

    protected AtomicLong connectCount = new AtomicLong();

    public long getGetCount() { return getCount.get(); }
    public long getGetErrorCount() { return getErrorCount.get(); }
    public long getGetSkipCount() { return getSkipCount.get(); }
    public long getPutCount() { return putCount.get(); }
    public long getConnectCount() { return connectCount.get(); }

    // for diagnosing deadlock situation
    public Map<String, Object> getTableLockState() {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("readLockCount", tableUseLock.getReadLockCount());
        m.put("queueLength", tableUseLock.getQueueLength());
        m.put("writeLocked", tableUseLock.isWriteLocked());
        return m;
    }

    public SingleHBaseTable() {
    }

    /**
     * attempts to reconnect to HBase if table is null.
     * must not be called with read-lock.
     * @return existing or newly opened HTableInterface.
     */
    protected HTableInterface getTable() {
        if (table == null && autoReconnect)
            openTable();
        return table;	
    }
    /**
     * close HTable {@code table}, set current time to tableError if closing because
     * of a communication error. should be called with write lock.
     * @param htable HTable to close.
     * @param byError true if closing because of an error.
     */
    protected void closeTable(HTableInterface htable, boolean byError) {
        if (htable == null) return;
        if (table != htable) {
            // other thread did closeTable on htable. don't close table.
            return;
        }
        try {
            table = null;
            htable.close();
        } catch (IOException ex) {
            LOG.warn("error closing " + htable + " - some commits may have been lost");
        }
        // necessary because HTable.close() does neither release HConnection 
        // resources nor unregister failed HConnection. new HTable will get
        // the same failed HConnection again without this. Apparently CDH3u5 has
        // fixed this issue.
        HConnectionManager.deleteConnection(htable.getConfiguration(), true);
        if (byError) {
            tableError = System.currentTimeMillis();
        }
    }

    public void put(Put p) throws IOException {
        putCount.incrementAndGet();
        // trigger reconnection if necessary. as table can be modified before
        // read lock is acquired, we don't read table variable here.
        getTable();
        boolean htableFailed = false;
        HTableInterface htable = null;
        Lock readLock = tableUseLock.readLock();
        try {
            if (!readLock.tryLock(TRY_READ_LOCK_TIMEOUT, TimeUnit.SECONDS)) {
                putSkipCount.incrementAndGet();
                throw new IOException("could not acquire read lock for HTable.");
            }
        } catch (InterruptedException ex) {
            throw new IOException("interrupted while acquiring read lock", ex);
        }
        try {
            htable = table;
            if (htable == null) {
                putSkipCount.incrementAndGet();
                throw new IOException("HBase connection is unvailable.");
            }
            // HTable.put() buffers Puts and access to the buffer is not
            // synchronized.
            synchronized (htable) {
                try {
                    htable.put(p);
                } catch (NullPointerException ex) {
                    // HTable.put() throws NullPointerException when connection is lost.
                    // It is somewhat weird, so translate it to IOException.
                    putErrorCount.incrementAndGet();
                    htableFailed = true;
                    throw new IOException("hbase connection is lost", ex);
                } catch (NotServingRegionException ex) {
                    putErrorCount.incrementAndGet();
                    // no need to close HTable.
                    throw ex;
                } catch (IOException ex) {
                    putErrorCount.incrementAndGet();
                    htableFailed = true;
                    throw ex;
                }
            }
        } finally {
            readLock.unlock();
            if (htableFailed) {
                closeTable(htable, true);
            }
        }
    }

    public Result get(Get g) throws IOException {
        getCount.incrementAndGet();
        // trigger reconnection if necessary. as table can be modified before
        // read lock is acquired, we don't read table variable here.
        getTable();
        boolean htableFailed = false;
        HTableInterface htable = null;
        Lock readLock = tableUseLock.readLock();
        try {
            if (!readLock.tryLock(TRY_READ_LOCK_TIMEOUT, TimeUnit.SECONDS)) {
                getSkipCount.incrementAndGet();
                throw new IOException("could not acquire read lock for HTable.");
            }
        } catch (InterruptedException ex) {
            throw new IOException("interrupted while acquiring read lock", ex);
        }
        try {
            htable = table;
            if (htable == null) {
                getSkipCount.incrementAndGet();
                throw new IOException("HBase connection is unvailable.");
            }
            try {
                return htable.get(g);
            } catch (NotServingRegionException ex) {
                // caused by disruption to HBase cluster. no need to
                // refresh HBase connection, since connection itself
                // is working okay.
                // TODO: should we need to back-off for a while? other
                // regions may still be accessible.
                getErrorCount.incrementAndGet();
                throw ex;
            } catch (IOException ex) {
                getErrorCount.incrementAndGet();
                htableFailed = true;
                throw ex;
            }
        } finally {
            readLock.unlock();
            if (htableFailed) {
                closeTable(htable, true);
            }
        }
    }
    
    @Override
    public HTableDescriptor getHtableDescriptor() throws IOException {
        HTableInterface table = getTable();
        if (table == null) {
            throw new IOException("HBase connection is unavailable.");
        }
        return table.getTableDescriptor();
    }

    public boolean inBackoffPeriod() {
        return (tableError > 0 && 
                (System.currentTimeMillis() - tableError) < reconnectInterval);
    }

    /**
     * timestamp of the last Put/Get error.
     * @return timestamp in ms.
     */
    public long getTableErrorTime() {
        return tableError;
    }
    /**
     * connect to HBase.
     * it does nothing if table is non-null, or it is in the back-off period since
     * the last error.
     * should be called with write lock.
     */
    protected boolean openTable() {
        if (table != null) return true;
        // fail immediately if we're in back-off period.
        if (inBackoffPeriod()) return false;
        try {
            HTable t = new HTable(hbase.configuration(), Bytes.toBytes(htableName));
            connectCount.incrementAndGet();
            t.setAutoFlush(autoFlush);
            table = t;
            tableError = 0;
            return true;
        } catch (TableNotFoundException ex) {
            // ex.getMessage() only has table name. be a little bit more friendly.
            LOG.warn("failed to connect to HTable \"" + htableName + "\": Table Not Found");
            tableError = System.currentTimeMillis();
            return false;
        } catch (IOException ex) {
            LOG.warn("failed to connect to HTable \"" + htableName + "\" (" + ex.getMessage() + ")");
            tableError = System.currentTimeMillis();
            return false;
        }
    }
    /**
     * number of seconds to wait for acquiring read lock.
     * if read lock is not acquired within this many seconds (probably
     * due to deadlock situation on write-lock side), {@link #get(Get)} will
     * <i>silently</i> fail.
     */
    public final static long TRY_READ_LOCK_TIMEOUT = 5;
    /**
     * number of seconds to wait for acquiring write lock.
     */
    public final static long TRY_WRITE_LOCK_TIMEOUT = 10;

    /**
     * close current connection and establish new connection.
     * fails silently if back-off period is in effect.
     */
    protected void reconnect(boolean onerror) throws IOException, InterruptedException {
        // avoid deadlock situation caused by attempting
        // to acquire write lock while holding read lock.
        // there'd be no real dead-lock now that timeout on write lock is implemented,
        // but it's nice to know there's a bug in locking.
        if (tableUseLock.getReadHoldCount() > 0) {
            LOG.warn("avoiding deadlock: reconnect() called by thread with read lock.");
            return;
        }
        Lock writeLock = tableUseLock.writeLock();
        if (!writeLock.tryLock(TRY_WRITE_LOCK_TIMEOUT, TimeUnit.SECONDS)) {
            LOG.warn("reconnect() could not acquire write lock on tableUseLock for " +
                    TRY_WRITE_LOCK_TIMEOUT + "s, giving up.");
            return;
        }
        try {
            closeTable(table, onerror);
            openTable();
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * close current connection and establish new connection.
     * for refreshing stale connection through scripting.
     * resets tableErrorTime to zero (it will be set to non-zero if
     * reconnection attempt fails).
     * @throws IOException
     * @throws InterruptedException
     */
    public void reconnect() throws IOException, InterruptedException {
        tableError = 0;
        reconnect(false);
    }
    
//    public boolean isRunning() {
//        return table != null;
//    }
    public void start() {
        super.start();
        openTable();
    }
    public void stop() {
        if (table != null) {
            try {
                table.close();
            } catch (IOException ex) {
                LOG.warn("table.close() failed", ex);
            }
        }
        table = null;
        super.stop();
    }
}
