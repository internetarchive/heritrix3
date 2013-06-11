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
import org.archive.modules.recrawl.PersistOnlineProcessor;
import org.springframework.context.Lifecycle;

/**
 * @contributor kenji
 * @contributor nlevitt
 */
public class HBaseTable implements Lifecycle {

    private static final Logger logger =
            Logger.getLogger(HBaseTable.class.getName());

    // XXX this default doesn't really belong here if this is supposed to be a
    // generic hbase table class
    protected String name = PersistOnlineProcessor.URI_HISTORY_DBNAME;
    public void setName(String name) {
        this.name = name;
    }
    public String getName() {
        return name;
    }

    protected HBase hbase = new HBase();
    public void setHbase(HBase hbase) {
        this.hbase = hbase;
    }
    public HBase getHbase() {
        return hbase;
    }

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

    public void put(Put p) throws IOException {
        HTableInterface table = htablePool().getTable(name);
        try {
            table.put(p);
        } finally {
            htablePool().putTable(table);
            // table.close(); // XXX hbase 0.92
        }
    }

    public Result get(Get g) throws IOException {
        HTableInterface table = htablePool().getTable(name);
        try {
            return table.get(g);
        } finally {
            htablePool().putTable(table);
            // table.close(); // XXX hbase 0.92
        }
    }

    public HTableDescriptor getHtableDescriptor() throws IOException {
        HTableInterface table = htablePool().getTable(name);
        try {
            return table.getTableDescriptor();
        } finally {
            htablePool().putTable(table);
        }
    }

    protected boolean isRunning = false;

    @Override
    public boolean isRunning() {
        return isRunning;
    }

    @Override
    public void start() {
        try {
            if (getCreate()) {
                HBaseAdmin admin = hbase.admin();
                if (!admin.tableExists(name)) {
                    HTableDescriptor desc = new HTableDescriptor(name);
                    logger.info("hbase table '" + name + "' does not exist, creating it... " + desc);
                    admin.createTable(desc);
                }
            }
        } catch (IOException e) {
            logger.log(Level.SEVERE, "problem creating hbase table " + name, e);
        }

        isRunning = true;
    }

    @Override
    public synchronized void stop() {
        isRunning = false;
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
