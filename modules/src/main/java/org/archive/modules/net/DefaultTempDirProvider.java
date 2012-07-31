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

package org.archive.modules.net;

import java.io.File;
import java.io.IOException;

import org.archive.modules.extractor.TempDirProvider;

/**
 * @author pjack
 *
 */
public class DefaultTempDirProvider implements TempDirProvider {

    private static final long serialVersionUID = 1L;
    
    final private static File TEMP_DIR = makeTempDir();
    
    protected static File makeTempDir() {
        File f;
        try {
            f = File.createTempFile("xxx", null);
            File r = f.getParentFile();
            f.delete();
            return r;
        } catch (IOException e) {
            return new File("temp");
        }        
    }
    
    
    public File getScratchDisk() {
        return TEMP_DIR;
    }
}
