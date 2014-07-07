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
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Logger;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.MasterNotRunningException;
import org.apache.hadoop.hbase.ZooKeeperConnectionException;
import org.apache.hadoop.hbase.client.HBaseAdmin;
import org.apache.hadoop.hbase.client.HConnectionManager;
import org.springframework.context.Lifecycle;

/**
 * Represents a deployment of HBase. (An instance, a database, an HBase...)
 * 
 * @contributor nlevitt
 */
public class HBase implements Lifecycle {

    private static final Logger logger =
            Logger.getLogger(HBase.class.getName());

    protected Configuration conf = null;

    private Map<String,String> properties;

    public Map<String,String> getProperties() {
        return properties;
    }

    public void setProperties(Map<String,String> properties) {
        this.properties = properties;

        if (conf == null) {
            conf = HBaseConfiguration.create();
        }
        for (Entry<String, String> entry: getProperties().entrySet()) {
            conf.set(entry.getKey(), entry.getValue());
        }
    }

    public synchronized Configuration configuration() {
        if (conf == null) {
            conf = HBaseConfiguration.create();
        }

        return conf;
    }
    
    // convenience setters
    public void setZookeeperQuorum(String value) {
        configuration().set("hbase.zookeeper.quorum", value);
    }
    public void setZookeeperClientPort(int port) {
        configuration().setInt("hbase.zookeeper.property.clientPort", port);
    }

    protected transient HBaseAdmin admin;

    public synchronized HBaseAdmin admin() throws MasterNotRunningException, ZooKeeperConnectionException {
        if (admin == null) {
            admin = new HBaseAdmin(configuration());
        }

        return admin;
    }

    @Override
    public synchronized void stop() {
        isRunning = false;
        if (admin != null) {
            try {
                admin.close();
            } catch (IOException e) {
                logger.warning("problem closing HBaseAdmin " + admin + " - " + e);
            }

            admin = null;
        }
        if (conf != null) {
            HConnectionManager.deleteConnection(conf, true);
        }
    }

    protected transient boolean isRunning = false;
    @Override
    public boolean isRunning() {
        return isRunning;
    }

    @Override
    public void start() {
        isRunning = true;
    }
}
