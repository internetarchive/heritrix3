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

import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.pool.BasePoolableObjectFactory;
import org.archive.io.WriterPool;
import org.archive.io.WriterPoolMember;
import org.archive.io.WriterPoolSettings;


/**
 * A pool of WARCWriters.
 * @author stack
 * @version $Revision: 4566 $ $Date: 2006-08-31 09:51:41 -0700 (Thu, 31 Aug 2006) $
 */
public class WARCWriterPool extends WriterPool {
    /**
     * Constructor
     * @param settings Settings for this pool.
     * @param poolMaximumActive
     * @param poolMaximumWait
     */
    public WARCWriterPool(final WriterPoolSettings settings,
            final int poolMaximumActive, final int poolMaximumWait) {
    	this(new AtomicInteger(), settings, poolMaximumActive, poolMaximumWait);
    }
    
    /**
     * Constructor
     * @param serial  Used to generate unique filename sequences
     * @param settings Settings for this pool.
     * @param poolMaximumActive
     * @param poolMaximumWait
     */
    public WARCWriterPool(final AtomicInteger serial,
    		final WriterPoolSettings settings,
            final int poolMaximumActive, final int poolMaximumWait) {
    	super(serial, new BasePoolableObjectFactory() {
            public Object makeObject() throws Exception {
                return new WARCWriter(serial,
                		settings.getOutputDirs(),
                        settings.getPrefix(), settings.getTemplate(),
                        settings.getCompress(), settings.getMaxFileSizeBytes(),
                        settings.getMetadata());
            }

            public void destroyObject(Object writer)
            throws Exception {
                ((WriterPoolMember)writer).close();
                super.destroyObject(writer);
            }
    	}, settings, poolMaximumActive, poolMaximumWait);
    }
}
