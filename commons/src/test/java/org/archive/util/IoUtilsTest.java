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

import junit.framework.TestCase;

/**
 * @author stack
 * @version $Date$, $Revision$
 */
public class IoUtilsTest extends TestCase {

    public void testGetClasspathPath() {
        final String absUnixPath = "/one/two/three";
        File f = new File(absUnixPath);
        assertTrue("Path is wrong abs " + IoUtils.getClasspathPath(f),
            IoUtils.getClasspathPath(f).equals(absUnixPath));
        final String relUnixPath = "one/two/three";
        f = new File(relUnixPath);
        assertTrue("Path is wrong rel " + IoUtils.getClasspathPath(f),
            IoUtils.getClasspathPath(f).equals(relUnixPath));
        final String nameUnixPath = "three";
        f = new File(nameUnixPath);
        assertTrue("Path is wrong name " + IoUtils.getClasspathPath(f),
            IoUtils.getClasspathPath(f).equals(nameUnixPath));     
    }
}
