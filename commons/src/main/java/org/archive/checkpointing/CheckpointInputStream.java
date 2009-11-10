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

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.net.URI;


/**
 * Object input stream that provides information useful during checkpoint
 * recovery.
 * 
 * @author pjack
 */
public class CheckpointInputStream extends ObjectInputStream
implements CheckpointRecovery {


    final private CheckpointRecovery recovery;
    
    
    public CheckpointInputStream(InputStream input,
            CheckpointRecovery recovery) throws IOException {
        super(input);
        this.recovery = recovery;
    }

    
    public String getRecoveredJobName() {
        return recovery.getRecoveredJobName();
    }

//    public <T> void setState(Object module, Key<T> key, T value) {
//        recovery.setState(module, key, value);
//    }


    public String translatePath(String path) {
        return recovery.translatePath(path);
    }


    public URI translateURI(URI uri) {
        return recovery.translateURI(uri);
    }


//    public void apply(SingleSheet global) {
//        throw new UnsupportedOperationException();
//    }
}
