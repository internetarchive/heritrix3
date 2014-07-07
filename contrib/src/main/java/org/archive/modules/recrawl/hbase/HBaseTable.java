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
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.hadoop.hbase.HTableDescriptor;
import org.apache.hadoop.hbase.client.Get;
import org.apache.hadoop.hbase.client.HBaseAdmin;
import org.apache.hadoop.hbase.client.HTableInterface;
import org.apache.hadoop.hbase.client.HTablePool;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.Result;

/**
 * @contributor kenji
 * @contributor nlevitt
 */
public class HBaseTable extends HBaseTableBean {

    static final Logger logger =
            Logger.getLogger(HBaseTable.class.getName());

    protected boolean create = false;
    public boolean getCreate() {
        return create;
    }
    /** Create the named table if it doesn't exist. */
    public void setCreate(boolean create) {
        this.create = create;
    }

    protected HTablePool htablePool = null;

    public HBaseTable() {
    }

    protected synchronized HTablePool htablePool() throws IOException {
        if (htablePool == null) {
            // XXX maxSize = number of toe threads?
            htablePool = new HTablePool(hbase.configuration(),
                    Integer.MAX_VALUE);
        }

        return htablePool;
    }

    @Override
    public void put(Put p) throws IOException {
        HTableInterface table = htablePool().getTable(htableName);
        try {
            table.put(p);
        } finally {
            htablePool().putTable(table);
            // table.close(); // XXX hbase 0.92
        }
    }

    @Override
    public Result get(Get g) throws IOException {
        HTableInterface table = htablePool().getTable(htableName);
        try {
            return table.get(g);
        } finally {
            htablePool().putTable(table);
            // table.close(); // XXX hbase 0.92
        }
    }

    public HTableDescriptor getHtableDescriptor() throws IOException {
        HTableInterface table = htablePool().getTable(htableName);
        try {
            return table.getTableDescriptor();
        } finally {
            htablePool().putTable(table);
        }
    }

    @Override
    public void start() {
        try {
            if (getCreate()) {
                HBaseAdmin admin = hbase.admin();
                if (!admin.tableExists(htableName)) {
                    HTableDescriptor desc = new HTableDescriptor(htableName);
                    logger.info("hbase table '" + htableName + "' does not exist, creating it... " + desc);
                    admin.createTable(desc);
                }
            }
        } catch (IOException e) {
            logger.log(Level.SEVERE, "problem creating hbase table " + htableName, e);
        }

        super.start();
    }

    @Override
    public synchronized void stop() {
        super.stop();
        // org.apache.hadoop.io.IOUtils.closeStream(htablePool); // XXX hbase 0.92
        if (htablePool != null) {
            try {
                htablePool.close();
            } catch (IOException e) {
                logger.warning("problem closing HTablePool " + htablePool + " - " + e);
            }
            htablePool = null;
        }
    }
}
