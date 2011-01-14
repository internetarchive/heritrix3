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
package org.archive.io.warc;

import java.io.File;
import java.util.List;

import org.archive.io.arc.WriterPoolSettingsData;
import org.archive.uid.RecordIDGenerator;

public class WARCWriterPoolSettingsData extends WriterPoolSettingsData implements WARCWriterPoolSettings {
    RecordIDGenerator generator;
    
    public WARCWriterPoolSettingsData(String prefix, String template,
            long maxFileSizeBytes, boolean compress, List<File> outputDirs,
            List<String> metadata, RecordIDGenerator generator) {
        super(prefix,template,maxFileSizeBytes,compress,outputDirs,metadata);
        this.generator = generator;
    }
    @Override
    public RecordIDGenerator getRecordIDGenerator() {
        return generator; 
    }
}