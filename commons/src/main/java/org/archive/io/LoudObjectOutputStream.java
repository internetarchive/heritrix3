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

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;

/**
 * ObjectOutputStream that logs class name of each object that is written
 * to the stream.  Useful for tracking down sources of NotSerializableException. 
 * 
 * @author pjack
 *
 */
public class LoudObjectOutputStream extends ObjectOutputStream {

    
    final private static Logger LOGGER = Logger.getLogger(
            LoudObjectOutputStream.class.getName()); 
    
    // Only log each class name once
    private Set<String> alreadyLogged = new HashSet<String>();
    
    public LoudObjectOutputStream(OutputStream out) throws IOException {
        super(out);
        this.enableReplaceObject(true);
    }


    @Override
    protected Object replaceObject(Object obj) throws IOException {
        if (obj != null) {
            String name = obj.getClass().getName();
            if (alreadyLogged.add(name)) {
                LOGGER.info("WROTE: " + name);
            }
        }
        return obj;
    }    
    

}
