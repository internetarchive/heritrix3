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
package org.archive.spring;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;

import org.archive.io.ReadSource;

/**
 * ConfigPath with added implication that it is an individual,
 * readable/writable File. 
 */
public class ConfigFile extends ConfigPath implements ReadSource, WriteTarget {
    private static final long serialVersionUID = 1L;

    public ConfigFile() {
        super();
    }

    public ConfigFile(String name, String path) {
        super(name, path);
    }

    public Reader obtainReader() {
        try {
            if(!getFile().exists()) {
                getFile().createNewFile();
            }
            
            configurer.snapshotToLaunchDir(getFile());

            return new InputStreamReader(
                    new FileInputStream(getFile()),
                    "UTF-8");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public Writer obtainWriter() {
        return obtainWriter(false); 
    }
    
    public Writer obtainWriter(boolean append) {
        try {
            return new OutputStreamWriter(
                    new FileOutputStream(getFile(), append),
                    "UTF-8");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}
