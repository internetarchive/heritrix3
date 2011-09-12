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

package org.archive.crawler;

import java.io.File;

import org.apache.commons.io.FileUtils;
import org.archive.spring.PathSharingContext;
import org.archive.util.TmpDirTestCase;
import org.springframework.beans.BeansException;

/**
 * Test all bundled job directories -- that they build, but have 
 * exactly one validation error (need to enter contact URL).
 * 
 * @contributor pjack
 */
public class BasicProfileTest extends TmpDirTestCase {

    
    /**
     * Tests the default profile that gets put in the heritrix tarball.
     */
    public void testBundledProfiles() throws Exception {
        File srcDir = new File("src/main/conf/jobs");
        if (!srcDir.exists()) {
            srcDir = new File("dist/src/main/conf/jobs");
        }
        if (!srcDir.exists()) {
            throw new IllegalStateException("Couldn't find jobs directory");
        }
        for (File f: srcDir.listFiles()) {
            if (f.isDirectory() && !f.getName().startsWith(".")) {
                testProfileDirectory(f);
            }
        }
    }

    protected void testProfileDirectory(File srcDir) throws Exception {
        System.out.println("\nNow testing " + srcDir.getName());
        File tmpDir = new File(getTmpDir(), "validatorTest");
        File configDir = new File(tmpDir, srcDir.getName());
        org.archive.util.FileUtils.ensureWriteableDirectory(configDir);
        FileUtils.copyDirectory(srcDir, configDir);

        PathSharingContext ac = null;
        try {
            File config = new File(configDir,"profile-crawler-beans.cxml");
            ac = new PathSharingContext("file:"+config.getAbsolutePath());
        } catch (BeansException be){
            be.printStackTrace(System.err);
        } finally {
            assertNotNull("profile not buildable",ac);
            ac.validate();
            assertEquals("did not get the expected one error",1,ac.getAllErrors().size());
            ac.destroy();
        }
    }
}
