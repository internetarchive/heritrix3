/* EnhancedEnvironment.java
 *
 * Created on February 18. 2007
 *
 * Copyright (C) 2007 Internet Archive.
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
    StoredClassCatalog classCatalog; 
    Database classCatalogDB;
    
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
