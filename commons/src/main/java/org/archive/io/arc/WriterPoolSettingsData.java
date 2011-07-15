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
package org.archive.io.arc;

import java.io.File;
import java.util.List;

import org.archive.io.WriterPoolSettings;

public class WriterPoolSettingsData implements WriterPoolSettings {
    long maxFileSizeBytes;
    String prefix;
    String template; 
    List<File> outputDirs;
    boolean compress;
    List<String> metadata;
    boolean frequentFlushes = true;
    int writeBufferSize = 16*1024;
    
    public WriterPoolSettingsData(String prefix, String template,
            long maxFileSizeBytes, boolean compress, List<File> outputDirs,
            List<String> metadata) {
        super();
        this.maxFileSizeBytes = maxFileSizeBytes;
        this.prefix = prefix;
        this.template = template;
        this.outputDirs = outputDirs;
        this.compress = compress;
        this.metadata = metadata;
    }
    
    @Override
    public boolean getCompress() {
        return compress;
    }
    @Override
    public long getMaxFileSizeBytes() {
        return maxFileSizeBytes;
    }
    @Override
    public List<String> getMetadata() {
        return metadata;
    }
    @Override
    public List<File> calcOutputDirs() {
        return outputDirs;
    }
    @Override
    public String getPrefix() {
        return prefix;
    }
    @Override
    public String getTemplate() {
        return template;
    }
    @Override
    public boolean getFrequentFlushes() {
        return frequentFlushes;
    }
    @Override
    public int getWriteBufferSize() {
        return writeBufferSize;
    }
}