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

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;

import org.apache.commons.io.FileUtils;
import org.archive.bdb.BdbModule;
import org.archive.checkpointing.DefaultCheckpointRecovery;
import org.archive.spring.ConfigPath;
import org.archive.util.TmpDirTestCase;

/**
 * @author pjack
 */
public class BdbModuleTest extends TmpDirTestCase {

    public void testCheckpoint() throws Exception {
        doCheckpoint();
        
        File first = new File(getTmpDir(), "first");
//        File checkpointDir = new File(getTmpDir(), "checkpoint");
        
        File second = new File(getTmpDir(), "second");
        FileUtils.deleteDirectory(second);
        
        DefaultCheckpointRecovery cr = new DefaultCheckpointRecovery("job");
        cr.getFileTranslations().put(first.getAbsolutePath(), 
                second.getAbsolutePath());
//        SheetManager mgr2 = Checkpointer.recover(checkpointDir, cr);
//        BdbModule bdb2 = (BdbModule)mgr2.getRoot().get("module");
//        Map<String,String> testData2 = bdb2.getBigMap("testData", false,
//                String.class, String.class);
        Map<String,String> map1 = new HashMap<String,String>();
        for (int i = 0; i < 1000; i++) {
            map1.put(String.valueOf(i), String.valueOf(i * 2));
        }

//        Map<String,String> map2 = dump(testData2);
//        assertEquals(map1, map2);
    }

    
    private void doCheckpoint() throws Exception {
        File first = new File(getTmpDir(), "first");
        FileUtils.deleteDirectory(first);
        
        File firstState = new File(first, "state");
//        MemorySheetManager mgr = new MemorySheetManager();
        
        BdbModule bdb = new BdbModule();
//        mgr.getRoot().put("module", bdb);
        bdb.setDir(new ConfigPath("test",firstState.getAbsolutePath()));
//        mgr.getGlobalSheet().set(bdb, BdbModule.DIR, firstState.getAbsolutePath());
        bdb.start();
        
        BdbModule.BdbConfig config = new BdbModule.BdbConfig();
        config.setAllowCreate(true);
        bdb.openDatabase("testOpen", config, false);
        
        ConcurrentMap<String,String> testData = bdb.getCBMMap("testData", false, 
                String.class, String.class);
        for (int i = 0; i < 1000; i++) {
            assertNull("unexpected prior entry", testData.putIfAbsent(String.valueOf(i), String.valueOf(i * 2)));
        }
        
        File checkpointDir = new File(getTmpDir(), "checkpoint");
        checkpointDir.mkdirs();
//        Checkpointer.checkpoint(mgr, checkpointDir);        
        bdb.stop();
    }
    
//    private Map<String,String> dump(Map<String,String> src) {
//        HashMap<String,String> dest = new HashMap<String,String>();
//        for (String k: src.keySet()) {
//            dest.put(k, src.get(k));
//        }
//        return dest;
//    }
}
