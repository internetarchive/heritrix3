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
import java.io.IOException;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.pool.BasePoolableObjectFactory;
import org.apache.commons.pool.impl.FairGenericObjectPool;
import org.apache.commons.pool.impl.GenericObjectPool;

/**
 * Pool of Writers.
 * 
 * Abstract. Override and pass in the Constructor a factory that creates
 * {@link WriterPoolMember} implementations.
 * 
 * @author stack
 */
public abstract class WriterPool {
    final Logger logger =  Logger.getLogger(this.getClass().getName());
   
    /**
     * Used to generate unique filename sequences.
     */
    final private AtomicInteger serialNo;
    
    /**
     * Don't enforce a maximum number of idle instances in pool.
     * To do so means GenericObjectPool will close files prematurely.
     */
    protected static final int NO_MAX_IDLE = -1;
    
    /**
     * Retry getting a file on fail the below arbitrary amount of times.
     * This facility is not configurable.  If we fail this many times
     * getting a file, something is seriously wrong.
     */
    private final int arbitraryRetryMax = 10;
    
	/**
	 * Default maximum active number of files in the pool.
	 */
	public static final int DEFAULT_MAX_ACTIVE = 1;

	/**
	 * Maximum time to wait on a free file..
	 */
	public static final int DEFAULT_MAXIMUM_WAIT = 1000 * 60 * 5;
    
    /**
     * Pool instance.
     */
    private GenericObjectPool pool = null;
    
    /**
     * File settings.
     * Keep in data structure rather than as individual values.
     */
    private final WriterPoolSettings settings;
    
    /**
     * Shutdown default constructor.
     */
    @SuppressWarnings("unused")
    private WriterPool() {
    	this(null, null, null, -1, -1);
    }
    
    /**
     * Constructor
     * @param serial  Used to generate unique filename sequences
     * @param factory Factory that knows how to make a {@link WriterPoolMember}.
     * @param settings Settings for this pool.
     * @param poolMaximumActive
     * @param poolMaximumWait
     */
    public WriterPool(final AtomicInteger serial,
    		final BasePoolableObjectFactory factory,
    		final WriterPoolSettings settings,
            final int poolMaximumActive, final int poolMaximumWait) {
        logger.info("Initial configuration:" +
                " prefix=" + settings.getPrefix() +
                ", suffix=" + settings.getSuffix() +
                ", compress=" + settings.isCompressed() +
                ", maxSize=" + settings.getMaxSize() +
                ", maxActive=" + poolMaximumActive +
                ", maxWait=" + poolMaximumWait);
        this.settings = settings;
        this.pool = new FairGenericObjectPool(factory, poolMaximumActive,
            GenericObjectPool.WHEN_EXHAUSTED_BLOCK, poolMaximumWait,
            NO_MAX_IDLE);
        this.serialNo = serial;
    }

	/**
	 * Check out a {@link WriterPoolMember}.
	 * 
	 * This method must be answered by a call to
	 * {@link #returnFile(WriterPoolMember)} else pool starts leaking.
	 * 
	 * @return Writer checked out of a pool of files.
	 * @throws IOException Problem getting Writer from pool (Converted
	 * from Exception to IOException so this pool can live as a good citizen
	 * down in depths of ARCSocketFactory).
	 * @throws NoSuchElementException If we time out waiting on a pool member.
	 */
    public WriterPoolMember borrowFile()
    throws IOException {
        WriterPoolMember f = null;
        for (int i = 0; f == null; i++) {
            long waitStart = System.currentTimeMillis();
            try {
                f = (WriterPoolMember)this.pool.borrowObject();
                if (logger.getLevel() == Level.FINE) {
                    logger.fine("Borrowed " + f + " (Pool State: "
                        + getPoolState(waitStart) + ").");
                }
            } catch (NoSuchElementException e) {
                // Let this exception out. Unit test at least depends on it.
                // Log current state of the pool.
                logger.warning(e.getMessage() + ": Retry #" + i + " of "
                    + " max of " + arbitraryRetryMax
                    + ": NSEE Pool State: " + getPoolState(waitStart));
                if (i >= arbitraryRetryMax) {
                    logger.log(Level.SEVERE,
                    	"maximum retries exceeded; rethrowing",e);
                    throw e;
                }
            } catch (Exception e) {
                // Convert.
                logger.log(Level.SEVERE,"E Pool State: " +
                    getPoolState(waitStart), e);
                throw new IOException("Failed getting writer from pool: " +
                    e.getMessage());
            }
        }
        return f;
    }

	/**
	 * @param writer Writer to return to the pool.
	 * @throws IOException Problem returning File to pool.
	 */
    public void returnFile(WriterPoolMember writer)
    throws IOException {
        try {
            if (logger.getLevel() == Level.FINE) {
                logger.fine("Returned " + writer);
            }
            this.pool.returnObject(writer);
        }
        catch(Exception e)
        {
            throw new IOException("Failed restoring writer to pool: " +
                    e.getMessage());
        }
    }

    public void invalidateFile(WriterPoolMember f)
    throws IOException {
        try {
            this.pool.invalidateObject(f);
        } catch (Exception e) {
            // Convert exception.
            throw new IOException(e.getMessage());
        }
        // It'll have been closed.  Rename with an '.invalid' suffix so it
        // gets attention.
        File file = f.getFile();
        file.renameTo(new File(file.getAbsoluteFile() +
                WriterPoolMember.INVALID_SUFFIX));
    }

	/**
	 * @return Number of {@link WriterPoolMember}s checked out of pool.
	 * @throws java.lang.UnsupportedOperationException
	 */
    public int getNumActive()
    throws UnsupportedOperationException {
        return this.pool.getNumActive();
    }

	/**
	 * @return Number of {@link WriterPoolMember} instances still in the pool.
	 * @throws java.lang.UnsupportedOperationException
	 */
    public int getNumIdle()
    throws UnsupportedOperationException {
        return this.pool.getNumIdle();
    }
    
	/**
	 * Close all {@link WriterPoolMember}s in pool.
	 */
    public void close() {
        this.pool.clear();
    }

	/**
	 * @return Returns settings.
	 */
    public WriterPoolSettings getSettings() {
        return this.settings;
    }
    
    /**
     * @return State of the pool string
     */
    protected String getPoolState() {
        return getPoolState(-1);
    }
    
    /**
     * @param startTime If we are passed a start time, we'll add difference
     * between it and now to end of string.  Pass -1 if don't want this
     * added to end of state string.
     * @return State of the pool string
     */
    protected String getPoolState(long startTime) {
        StringBuffer buffer = new StringBuffer("Active ");
        buffer.append(getNumActive());
        buffer.append(" of max ");
        buffer.append(this.pool.getMaxActive());
        buffer.append(", idle ");
        buffer.append(this.pool.getNumIdle());
        if (startTime != -1) {
            buffer.append(", time ");
            buffer.append(System.currentTimeMillis() - startTime);
            buffer.append("ms of max ");
            buffer.append(this.pool.getMaxWait());
            buffer.append("ms");
        }
        return buffer.toString();
    }
    
    /**
     * Returns the atomic integer used to generate serial numbers
     * for files.
     * 
     * @return  the serial number generator
     */
    public AtomicInteger getSerialNo() {
        return serialNo;
    }
}