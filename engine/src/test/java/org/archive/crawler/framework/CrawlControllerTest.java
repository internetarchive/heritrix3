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

package org.archive.crawler.framework;

import static org.archive.util.TmpDirTestCase.DEFAULT_TEST_TMP_DIR;
import static org.archive.util.TmpDirTestCase.TEST_TMP_SYSTEM_PROPERTY_NAME;

import java.io.File;
import java.io.FileWriter;

import org.archive.bdb.BdbModule;
import org.archive.modules.net.BdbServerCache;
import org.archive.spring.ConfigPath;
import org.archive.state.ModuleTestBase;
import org.archive.util.ArchiveUtils;
import org.archive.util.FileUtils;

/**
 * 
 * @author pjack
 */
public class CrawlControllerTest extends ModuleTestBase {
 
    // TODO TESTME

    public static CrawlController makeTempCrawlController() throws Exception {
        String tmpPath = System.getProperty(TEST_TMP_SYSTEM_PROPERTY_NAME);
        if (tmpPath == null) {
            tmpPath = DEFAULT_TEST_TMP_DIR;
        }
        File tmp = new File(tmpPath);
        FileUtils.ensureWriteableDirectory(tmp);
        
        FileWriter fileWriter = null;
        try {
            fileWriter = new FileWriter(new File(tmp, "seeds.txt"));
            fileWriter.write("http://www.pandemoniummovie.com");
            fileWriter.close();
        } finally {
            ArchiveUtils.closeQuietly(fileWriter);
        }
        
        File state = new File(tmp, "state");
        FileUtils.ensureWriteableDirectory(state);
        
        File checkpoints = new File(tmp, "checkpoints");
        FileUtils.ensureWriteableDirectory(checkpoints);
        
        BdbModule bdb = new BdbModule();
        bdb.setDir(new ConfigPath("test",state.getAbsolutePath()));
//        def.set(bdb, BdbModule.DIR, state.getAbsolutePath());
        bdb.start();
        
        CrawlController controller = new CrawlController();
        controller.setServerCache(new BdbServerCache());
        controller.start();
        return controller;
    }

    
    @Override
    protected void verifySerialization(Object first, byte[] firstBytes, 
            Object second, byte[] secondBytes) throws Exception {
        // TODO TESTME
    }

    
    
}
