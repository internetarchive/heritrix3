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

import org.archive.io.WriterPoolSettings;
import org.archive.uid.RecordIDGenerator;

/**
 * Settings object for a {@link WARCWriterPool}.
 * Used creating {@link WARCWriter}s.
 * 
 * @version $Date: 2010-08-19 17:21:43 -0700 (Thu, 19 Aug 2010) $, $Revision: 6927 $
 */
public interface WARCWriterPoolSettings extends WriterPoolSettings {
    public RecordIDGenerator getRecordIDGenerator();
}