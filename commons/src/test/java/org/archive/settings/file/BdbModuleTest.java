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

package org.archive.settings.file;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.math.RandomUtils;
import org.archive.bdb.BdbModule;
import org.archive.crawler.framework.Checkpoint;
import org.archive.spring.ConfigPath;
import org.archive.util.ObjectIdentityBdbCache;
import org.archive.util.Supplier;
import org.archive.util.TmpDirTestCase;

/**
 * Test BdbModule.
 * 
 * @contributor pjack
 * @contributor gojomo
 */
public class BdbModuleTest extends TmpDirTestCase {

    public void testDoCheckpoint() throws Exception {
        ConfigPath basePath = new ConfigPath("testBase",getTmpDir().getAbsolutePath());
        ConfigPath bdbDir = new ConfigPath("bdb","bdb"); 
        bdbDir.setBase(basePath); 
        FileUtils.deleteDirectory(bdbDir.getFile());

        BdbModule bdb = new BdbModule();
        bdb.setDir(bdbDir);
        bdb.start();

        // avoid data from prior runs being mistaken for current run
        int randomFactor = RandomUtils.nextInt();
        
        ObjectIdentityBdbCache<String> testData = 
            bdb.getOIBCCache("testData", false,String.class);
        for (int i1 = 0; i1 < 1000; i1++) {
            String key = String.valueOf(i1);
            final String value = String.valueOf(randomFactor*i1);
            String cached = testData.getOrUse(key, new Supplier<String>(){
                public String get() {
                    return value;
                }
            });
            assertSame("unexpected prior entry",value,cached);  
        }
        
        Checkpoint checkpointInProgress = new Checkpoint();
        ConfigPath checkpointsPath = new ConfigPath("checkpoints","checkpoints");
        checkpointsPath.setBase(basePath); 
        checkpointInProgress.generateFrom(checkpointsPath,998);

        bdb.doCheckpoint(checkpointInProgress);
        String firstCheckpointName = checkpointInProgress.getName();
        
        for (int i2 = 1000; i2 < 2000; i2++) {
            String key = String.valueOf(i2);
            final String value = String.valueOf(randomFactor*i2);
            String cached = testData.getOrUse(key, new Supplier<String>(){
                public String get() {
                    return value;
                }
            });
            assertSame("unexpected prior entry",value,cached);  
        }

        checkpointInProgress = new Checkpoint(); 
        checkpointInProgress.generateFrom(checkpointsPath,999);

        bdb.doCheckpoint(checkpointInProgress);
        
        bdb.stop();
        
        BdbModule bdb2 = new BdbModule();
        bdb2.setDir(bdbDir);
        
        Checkpoint recoveryCheckpoint = new Checkpoint();
        ConfigPath recoverPath = new ConfigPath("recover",firstCheckpointName);
        recoverPath.setBase(basePath);
        recoveryCheckpoint.setCheckpointDir(recoverPath);
        recoveryCheckpoint.afterPropertiesSet();
        
        bdb2.setRecoveryCheckpoint(recoveryCheckpoint);
        
        bdb2.start();
        
        ObjectIdentityBdbCache<String> restoreData = 
            bdb2.getOIBCCache("testData",true,String.class);
        
        assertEquals("unexpected size", 1000, restoreData.size());
        assertEquals("unexpected value",randomFactor*999,Integer.parseInt(restoreData.get(""+999)));

        bdb2.stop(); 
    }
}
