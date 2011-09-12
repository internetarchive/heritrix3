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

package org.archive.util;

import java.io.File;
import java.io.IOException;

import junit.framework.TestCase;


/**
 * Base class for TestCases that want access to a tmp dir for the writing
 * of files.
 *
 * @author stack
 */
public abstract class TmpDirTestCase extends TestCase
{
    /**
     * Name of the system property that holds pointer to tmp directory into
     * which we can safely write files.
     */
    public static final String TEST_TMP_SYSTEM_PROPERTY_NAME = "testtmpdir";

    /**
     * Default test tmp.
     */
    public static final String DEFAULT_TEST_TMP_DIR = File.separator + "tmp" +
        File.separator + "heritrix-junit-tests";

    /**
     * Directory to write temporary files to.
     */
    private File tmpDir = null;


    public TmpDirTestCase()
    {
        super();
    }

    public TmpDirTestCase(String testName)
    {
        super(testName);
    }

    /*
     * @see TestCase#setUp()
     */
    protected void setUp() throws Exception {
        super.setUp();
        this.tmpDir = tmpDir();
    }

    /**
     * @return Returns the tmpDir.
     */
    public File getTmpDir()
    {
        return this.tmpDir;
    }

    /**
     * Delete any files left over from previous run.
     *
     * @param basename Base name of files we're to clean up.
     */
    public void cleanUpOldFiles(String basename) {
        cleanUpOldFiles(getTmpDir(), basename);
    }

    /**
     * Delete any files left over from previous run.
     *
     * @param prefix Base name of files we're to clean up.
     * @param basedir Directory to start cleaning in.
     */
    public void cleanUpOldFiles(File basedir, String prefix) {
        File [] files = FileUtils.getFilesWithPrefix(basedir, prefix);
        if (files != null) {
            for (int i = 0; i < files.length; i++) {
                org.apache.commons.io.FileUtils.deleteQuietly(files[i]);
            }
        }
    }
    
    
    public static File tmpDir() throws IOException {
        String tmpDirStr = System.getProperty(TEST_TMP_SYSTEM_PROPERTY_NAME);
        tmpDirStr = (tmpDirStr == null)? DEFAULT_TEST_TMP_DIR: tmpDirStr;
        File tmpDir = new File(tmpDirStr);
        FileUtils.ensureWriteableDirectory(tmpDir);

        if (!tmpDir.canWrite())
        {
            throw new IOException(tmpDir.getAbsolutePath() +
                 " is unwriteable.");
        }
        
        return tmpDir;
    }
}
