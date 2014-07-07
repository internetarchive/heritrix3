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
import java.util.logging.Logger;

import org.apache.hadoop.hbase.HColumnDescriptor;
import org.apache.hadoop.hbase.HTableDescriptor;
import org.apache.hadoop.hbase.client.HBaseAdmin;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.RetriesExhaustedWithDetailsException;
import org.apache.hadoop.hbase.regionserver.NoSuchColumnFamilyException;
import org.apache.hadoop.hbase.util.Bytes;
import org.archive.modules.CrawlURI;
import org.archive.modules.fetcher.FetchStatusCodes;
import org.archive.modules.recrawl.RecrawlAttributeConstants;

/**
 * @contributor kenji
 */
public class HBasePersistStoreProcessor extends HBasePersistProcessor implements FetchStatusCodes, RecrawlAttributeConstants {
    private static final Logger logger = Logger.getLogger(HBasePersistStoreProcessor.class.getName());

    protected boolean addColumnFamily = false;
    public boolean getAddColumnFamily() {
        return addColumnFamily;
    }
    /**
     * Add the expected column family
     * {@link HBasePersistProcessor#COLUMN_FAMILY} to the HBase table if the
     * table doesn't already have it.
     */
    public void setAddColumnFamily(boolean addColumnFamily) {
        this.addColumnFamily = addColumnFamily;
    }

    protected int retryIntervalMs = 10*1000;
    public int getRetryIntervalMs() {
        return retryIntervalMs;
    }
    public void setRetryIntervalMs(int retryIntervalMs) {
        this.retryIntervalMs = retryIntervalMs;
    }

    protected int maxTries = 1;
    public int getMaxTries() {
        return maxTries;
    }
    public void setMaxTries(int maxTries) {
        this.maxTries = maxTries;
    }

    protected synchronized void addColumnFamily() {
        try {
            HTableDescriptor oldDesc = table.getHtableDescriptor();
            byte[] columnFamily = Bytes.toBytes(schema.getColumnFamily());
            if (oldDesc.getFamily(columnFamily) == null) {
                HTableDescriptor newDesc = new HTableDescriptor(oldDesc);
                newDesc.addFamily(new HColumnDescriptor(columnFamily));
                logger.info("table does not yet have expected column family, modifying descriptor to " + newDesc);
                HBaseAdmin hbaseAdmin = table.getHbase().admin();
                hbaseAdmin.disableTable(table.getName());
                hbaseAdmin.modifyTable(Bytes.toBytes(table.getName()), newDesc);
                hbaseAdmin.enableTable(table.getName());
            }
        } catch (IOException e) {
            logger.warning("problem adding column family: " + e);
        }
    }

    @Override
    protected void innerProcess(CrawlURI uri) {
        Put p = schema.createPut(uri);
        int tryCount = 0;
        do {
            tryCount++;
            try {
                table.put(p);
                return;
            } catch (RetriesExhaustedWithDetailsException e) {
                if (e.getCause(0) instanceof NoSuchColumnFamilyException && getAddColumnFamily()) {
                    addColumnFamily();
                    tryCount--;
                } else {
                    logger.warning("put failed " + "(try " + tryCount + " of "
                            + getMaxTries() + ")" + " for " + uri + " - " + e);
                }
            } catch (IOException e) {
                logger.warning("put failed " + "(try " + tryCount + " of "
                        + getMaxTries() + ")" + " for " + uri + " - " + e);
            } catch (NullPointerException e) {
                // HTable.put() throws NullPointerException while connection is lost.
                logger.warning("put failed " + "(try " + tryCount + " of "
                        + getMaxTries() + ")" + " for " + uri + " - " + e);
            }

            if (tryCount > 0 && tryCount < getMaxTries() && isRunning()) {
                try {
                    Thread.sleep(getRetryIntervalMs());
                } catch (InterruptedException ex) {
                    logger.warning("thread interrupted. aborting retry for " + uri);
                    return;
                }
            }
        } while (tryCount < getMaxTries() && isRunning());

        if (isRunning()) {
            logger.warning("giving up after " + tryCount + " tries on put for " + uri);
        }
    }

    @Override
    protected boolean shouldProcess(CrawlURI curi) {
        return super.shouldStore(curi);
    }
}
