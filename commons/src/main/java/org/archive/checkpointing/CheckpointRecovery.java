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

package org.archive.checkpointing;

import java.net.URI;

/**
 * The information about a checkpoint recovery.
 * 
 * @author pjack
 */
public interface CheckpointRecovery {

    String getRecoveredJobName();
    
    String translatePath(String path);
    
    URI translateURI(URI uri);
    
//    <T> void setState(Object module, Key<T> key, T value);
//
//    void apply(SingleSheet global);
}
