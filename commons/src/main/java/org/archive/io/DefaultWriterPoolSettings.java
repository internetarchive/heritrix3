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

package org.archive.io;

import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * @author pjack
 */
public class DefaultWriterPoolSettings 
implements WriterPoolSettings, Serializable {
    
    private static final long serialVersionUID = 1L;

    private long maxSize;
    private List<String> metadata = new ArrayList<String>();;
    transient private List<File> outputDirs = new ArrayList<File>();
    private String prefix;
    private String suffix;
    private boolean compressed;

    
    public DefaultWriterPoolSettings() {
    }
    
    
    public boolean isCompressed() {
        return compressed;
    }
    
    
    public void setCompressed(boolean compressed) {
        this.compressed = compressed;
    }
    
    
    public long getMaxSize() {
        return maxSize;
    }
    
    
    public void setMaxSize(long maxSize) {
        this.maxSize = maxSize;
    }
    
    
    public List<String> getMetadata() {
        return metadata;
    }
    
    
    public void setMetadata(List<String> metadata) {
        this.metadata = metadata;
    }
    
    
    public List<File> getOutputDirs() {
        return outputDirs;
    }
    
    
    public void setOutputDirs(List<File> outputDirs) {
        this.outputDirs = outputDirs;
    }
    
    
    public String getPrefix() {
        return prefix;
    }
    
    
    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }
    
    
    public String getSuffix() {
        return suffix;
    }
    
    
    public void setSuffix(String suffix) {
        this.suffix = suffix;
    }
}
