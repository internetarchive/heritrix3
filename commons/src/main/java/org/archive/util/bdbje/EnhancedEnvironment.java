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
package org.archive.util.bdbje;

import java.io.File;

import com.sleepycat.bind.serial.StoredClassCatalog;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;

/**
 * Version of BDB_JE Environment with additional convenience features, such as
 * a shared, cached StoredClassCatalog. (Additional convenience caching of 
 * Databases and StoredCollections may be added later.)
 * 
 * @author gojomo
 */
public class EnhancedEnvironment extends Environment {
    protected StoredClassCatalog classCatalog; 
    protected Database classCatalogDB;
    
    /**
     * Constructor
     * 
     * @param envHome directory in which to open environment
     * @param envConfig config options
     * @throws DatabaseException
     */
    public EnhancedEnvironment(File envHome, EnvironmentConfig envConfig) throws DatabaseException {
        super(envHome, envConfig);
    }

    /**
     * Return a StoredClassCatalog backed by a Database in this environment,
     * either pre-existing or created (and cached) if necessary.
     * 
     * @return the cached class catalog
     */
    public StoredClassCatalog getClassCatalog() {
        if(classCatalog == null) {
            DatabaseConfig dbConfig = new DatabaseConfig();
            dbConfig.setAllowCreate(true);
            dbConfig.setReadOnly(this.getConfig().getReadOnly());
            try {
                classCatalogDB = openDatabase(null, "classCatalog", dbConfig);
                classCatalog = new StoredClassCatalog(classCatalogDB);
            } catch (DatabaseException e) {
                // TODO Auto-generated catch block
                throw new RuntimeException(e);
            }
        }
        return classCatalog;
    }

    @Override
    public synchronized void close() throws DatabaseException {
        if(classCatalogDB!=null) {
            classCatalogDB.close();
        }
        super.close();
    }

    /**
     * Create a temporary test environment in the given directory.
     * @param dir target directory
     * @return EnhancedEnvironment
     */
    public static EnhancedEnvironment getTestEnvironment(File dir) {
        EnvironmentConfig envConfig = new EnvironmentConfig();
        envConfig.setAllowCreate(true);
        envConfig.setTransactional(false);
        EnhancedEnvironment env;
        try {
            env = new EnhancedEnvironment(dir, envConfig);
        } catch (DatabaseException e) {
            throw new RuntimeException(e);
        } 
        return env;
    }
}
