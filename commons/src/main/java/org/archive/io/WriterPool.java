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
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

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
    final protected AtomicInteger serialNo;
    
	/**
	 * Default maximum active number of files in the pool.
	 */
	public static final int DEFAULT_MAX_ACTIVE = 1;

	/** Assumed largest possible value of maxActive; pool will have this
	 * maximum capacity, so dynamic changes beyond this number won't work. */
	protected static final int LARGEST_MAX_ACTIVE = 255;
	
	/**
	 * Maximum time to wait on a free file before considering
	 * making a new one (if not already at max)
	 */
	public static final int DEFAULT_MAX_WAIT_FOR_IDLE = 500;
    
    /**
     * File settings.
     * Keep in data structure rather than as individual values.
     */
    protected final WriterPoolSettings settings;

    /** maximum number of writers to create at a time*/
    protected int maxActive;
    /** maximum ms to wait before considering creation of a writer */ 
    protected int maxWait;
    /** current count of active writers; only read/mutated in synchronized blocks */
    protected int currentActive = 0; 
    /** round-robin queue of available writers */ 
    BlockingQueue<WriterPoolMember> availableWriters;

    /** system time when writer was last wanted (because one was not ready in time) */     
    protected long lastWriterNeededTime;
    /** system time when writer was last 'rolled over' (imminent creation of new file) */ 
    protected long lastWriterRolloverTime; 
    
    /**
     * Constructor
     * @param serial  Used to generate unique filename sequences
     * @param factory Factory that knows how to make a {@link WriterPoolMember}.
     * @param settings Settings for this pool.
     * @param poolMaximumActive
     * @param poolMaximumWait
     */
    public WriterPool(final AtomicInteger serial,
    		final WriterPoolSettings settings,
            final int poolMaximumActive, final int poolMaximumWait) {
        logger.info("Initial configuration:" +
                " prefix=" + settings.getPrefix() +
                ", suffix=" + settings.getTemplate() +
                ", compress=" + settings.getCompress() +
                ", maxSize=" + settings.getMaxFileSizeBytes() +
                ", maxActive=" + poolMaximumActive +
                ", maxWait=" + poolMaximumWait);
        this.settings = settings;
        this.maxActive = poolMaximumActive;
        this.maxWait = poolMaximumWait;
        availableWriters = new ArrayBlockingQueue<WriterPoolMember>(LARGEST_MAX_ACTIVE, true);
        this.serialNo = serial;
    }

	/**
	 * Check out a {@link WriterPoolMember}.
	 * 
	 * This method should be followed by a call to
	 * {@link #returnFile(WriterPoolMember)} or 
	 * {@link #invalidateFile(WriterPoolMember)} else pool starts leaking.
	 * 
	 * @return Writer checked out of a pool of files or created
	 * @throws IOException Problem getting Writer from pool (Converted
	 * from Exception to IOException so this pool can live as a good citizen
	 * down in depths of ARCSocketFactory).
	 */
    public WriterPoolMember borrowFile()
    throws IOException {
        WriterPoolMember writer = null;
        while(writer == null) {
            try {
                writer = availableWriters.poll(maxWait,TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                // nothing to do but proceed
            }
            if(writer==null) {
                writer = makeNewWriterIfAppropriate(); 
            }
        }
        return writer;
    }

	/**
	 * Create a new writer instance, if still below maxActive count. 
	 * Remember times to help make later decision when writer should 
	 * be discarded. 
	 * 
	 * @return WriterPoolMember or null if already at max
	 */
	protected synchronized WriterPoolMember makeNewWriterIfAppropriate() {
	    long now = System.currentTimeMillis();
	    lastWriterNeededTime = now; 
        if(currentActive < maxActive) {
            currentActive++;
            lastWriterRolloverTime = now; 
            return makeWriter(); 
        }
        return null; 
    }

    /**
     * @return new WriterPoolMember of appropriate type
     */
    protected abstract WriterPoolMember makeWriter();
    
    /**
     * Discard a previously-used writer, cleanly closing it and leaving it out
     * of the pool. 
     * @param writer
     * @throws IOException
     */
    public synchronized void destroyWriter(WriterPoolMember writer) throws IOException {
        currentActive--; 
        writer.close();
    }
    /**
     * Return a writer, for likely reuse unless (1) writer's current file has 
     * reached its target size; and (2) there's been no demand for additional 
     * writers since the last time a new writer-file was rolled-over. In that
     * case, the possibly-superfluous writer instance is discarded. 
	 * @param writer Writer to return to the pool.
	 * @throws IOException Problem returning File to pool.
	 */
    public void returnFile(WriterPoolMember writer)
    throws IOException {
        synchronized(this) {
            if(writer.isOversize()) {
            // maybe retire writer rather than recycle
                if(lastWriterNeededTime<=lastWriterRolloverTime) {
                    // no timeouts waiting for recycled writer since last writer rollover
                    destroyWriter(writer);
                    return;
                } else {
                    // reuse writer instance, causing new file to be created
                    lastWriterRolloverTime = System.currentTimeMillis();
                }
            }
        }
        if(!availableWriters.offer(writer)) {
            logger.log(Level.WARNING, "writer unreturnable to available pool; closing early");
            destroyWriter(writer); 
        }
    }

    /**
     * Close and discard a writer that experienced a potentially-corrupting
     * error. 
     * @param f writer with problem 
     * @throws IOException
     */
    public synchronized void invalidateFile(WriterPoolMember f)
    throws IOException {
        try {
            destroyWriter(f);
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
    public synchronized int getNumActive()
    throws UnsupportedOperationException {
        return currentActive - getNumIdle();
    }

	/**
	 * @return Number of {@link WriterPoolMember} instances still in the pool.
	 * @throws java.lang.UnsupportedOperationException
	 */
    public int getNumIdle()
    throws UnsupportedOperationException {
        return availableWriters.size();
    }
    
	/**
	 * Close all {@link WriterPoolMember}s in pool.
	 */
    public void close() {
        WriterPoolMember writer = availableWriters.poll(); 
        while (writer!=null) {
            try {
                destroyWriter(writer);
            } catch (IOException e) {
                logger.log(Level.WARNING,"problem closing writer",e); 
            }
            writer = availableWriters.poll();
        }
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
        StringBuffer buffer = new StringBuffer("Active ");
        buffer.append(getNumActive());
        buffer.append(" of max ");
        buffer.append(maxActive);
        buffer.append(", idle ");
        buffer.append(getNumIdle());
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