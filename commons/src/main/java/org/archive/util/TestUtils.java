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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.ref.SoftReference;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.logging.Logger;

import junit.framework.TestCase;
import junit.framework.TestSuite;


/**
 * Utility methods useful in testing situations.
 * 
 * @author gojomo
 */
public class TestUtils {
    private static final Logger logger =
        Logger.getLogger(TestUtils.class.getName());

    /**
     * Temporarily exhaust memory, forcing weak/soft references to
     * be broken. 
     */
    public static void forceScarceMemory() {
        // force soft references to be broken
        LinkedList<SoftReference<byte[]>> hog = new LinkedList<SoftReference<byte[]>>();
        long blocks = Runtime.getRuntime().maxMemory() / 1000000;
        logger.info("forcing scarce memory via "+blocks+" 1MB blocks");
        for(long l = 0; l <= blocks; l++) {
            try {
                hog.add(new SoftReference<byte[]>(new byte[1000000]));
            } catch (OutOfMemoryError e) {
                hog = null;
                logger.info("OOME triggered");
                break;
            }
        }
    }

    public static void testSerialization(Object proc) throws Exception {
        byte[] first = serialize(proc);
        ByteArrayInputStream binp = new ByteArrayInputStream(first);
        ObjectInputStream oinp = new ObjectInputStream(binp);
        Object o = oinp.readObject();
        oinp.close();
        TestCase.assertEquals(proc.getClass(), o.getClass());
        byte[] second = serialize(o);
        TestCase.assertTrue(Arrays.equals(first, second));
    }


    public static byte[] serialize(Object o) throws Exception {
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        ObjectOutputStream oout = new ObjectOutputStream(bout);    
        oout.writeObject(o);
        oout.close();
        return bout.toByteArray();        
    }

    
    public static TestSuite makePackageSuite(Class<?> c) 
    throws ClassNotFoundException {
        String cname = c.getName();
        int p = cname.lastIndexOf('.');
        String dir = cname.substring(0, p).replace('.', File.separatorChar);
        String root = "heritrix/src/test/java/".replace('/', File.separatorChar);
        File src = new File(root);
        return makeSuite(src, new File(root + dir));
    }
    

    public static TestSuite makeSuite(File srcRoot, File dir) 
    throws ClassNotFoundException {
        TestSuite result = new TestSuite("All Tests");
        if (!dir.exists()) {
            throw new IllegalArgumentException(dir + " does not exist.");
        }
        scanSuite(result, srcRoot, dir);
        return result;
    }
    
    
    private static void scanSuite(TestSuite suite, File start, File dir) 
    throws ClassNotFoundException {
        for (File f: dir.listFiles()) {
            if (f.isDirectory() && !f.getName().startsWith(".")) {
                String prefix = start.getAbsolutePath();
                String full = f.getAbsolutePath();
                TestSuite sub = new TestSuite(full.substring(prefix.length()));
                scanSuite(sub, start, f);
                if (sub.testCount() > 0) {
                    suite.addTest(sub);
                }
            } else {
                if (f.getName().endsWith("Test.java")) {
                    String full = f.getAbsolutePath();
                    String prefix = start.getAbsolutePath();
                    String cname = full.substring(prefix.length());
                    if (cname.startsWith(File.separator)) {
                        cname = cname.substring(1);
                    }
                    cname = cname.replace(File.separatorChar, '.');
                    cname = cname.substring(0, cname.length() - 5);
                    suite.addTestSuite(Class.forName(cname));
                }
            }
        }
    }
    
    /*
     * create a tmp dir for testing; copied nearly verbatim from TmpDirTestCase
     */
    public static File tmpDir() throws IOException {
        String tmpDirStr = System.getProperty(TmpDirTestCase.TEST_TMP_SYSTEM_PROPERTY_NAME);
        tmpDirStr = (tmpDirStr == null)? TmpDirTestCase.DEFAULT_TEST_TMP_DIR: tmpDirStr;
        File tmpDir = new File(tmpDirStr);
        FileUtils.ensureWriteableDirectory(tmpDir);

        if (!tmpDir.canWrite())
        {
            throw new IOException(tmpDir.getAbsolutePath() +
                 " is unwriteable.");
        }
        tmpDir.deleteOnExit();
        return tmpDir;
    }


}
