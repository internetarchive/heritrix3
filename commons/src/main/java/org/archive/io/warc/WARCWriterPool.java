/* $Id: WARCWriterPool.java 4566 2006-08-31 16:51:41Z stack-sf $
 *
 * Created on August 1st, 2006.
 *
 * Copyright (C) 2006 Internet Archive.
 *
 * This file is part of the Heritrix web crawler (crawler.archive.org).
 *
 * Heritrix is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser Public License as published by
 * the Free Software Foundation; either version 2.1 of the License, or
 * any later version.
 *
 * Heritrix is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser Public License for more details.
 *
 * You should have received a copy of the GNU Lesser Public License
 * along with Heritrix; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
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
                        settings.getPrefix(), settings.getSuffix(),
                        settings.isCompressed(), settings.getMaxSize(),
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
