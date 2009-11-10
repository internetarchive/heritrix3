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
package org.archive.util.ms;

import java.io.IOException;
import java.util.List;

import org.archive.io.SeekInputStream;


public interface Entry {

    
    enum EntryType { ROOT, FILE, DIRECTORY };
    
    String getName();
    int getIndex();
    
    Entry getPrevious() throws IOException;
    Entry getNext() throws IOException;
    Entry getChild() throws IOException;
    EntryType getType() throws IOException;
    List<Entry> list() throws IOException;
    SeekInputStream open() throws IOException;


}
