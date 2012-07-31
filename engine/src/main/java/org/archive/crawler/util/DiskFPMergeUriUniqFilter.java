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
package org.archive.crawler.util;

import it.unimi.dsi.fastutil.longs.LongIterators;
import it.unimi.dsi.fastutil.longs.LongIterator;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.NoSuchElementException;

import org.archive.util.ArchiveUtils;

/**
 * Crude FPMergeUriUniqFilter using a disk data file of raw longs as the
 * overall FP record. 
 * 
 * @author gojomo
 */
public class DiskFPMergeUriUniqFilter extends FPMergeUriUniqFilter {
    protected long count = 0; 
    protected File scratchDir; 
    protected File currentFps;
    protected File newFpsFile;
    protected DataOutputStream newFps; 
    protected long newCount; 
    protected DataInputStream oldFps; 
    
    public DiskFPMergeUriUniqFilter(File scratchDir) {
        super();
        this.scratchDir = scratchDir; 
        // TODO: Use two scratch locations, to allow IO to be split
        // over separate disks
    }
    
    /* (non-Javadoc)
     * @see org.archive.crawler.util.FPMergeUriUniqFilter#beginFpMerge()
     */
    protected LongIterator beginFpMerge() {
        newFpsFile = new File(scratchDir,ArchiveUtils.get17DigitDate()+".fp");
        if(newFpsFile.exists()) {
            throw new RuntimeException(newFpsFile+" exists");
        }
        try {
            newFps = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(newFpsFile)));
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
        newCount = 0;
        if(currentFps==null) {
            return LongIterators.EMPTY_ITERATOR;
        }
        try {
            oldFps = new DataInputStream(new BufferedInputStream(new FileInputStream(currentFps)));
        } catch (FileNotFoundException e1) {
            throw new RuntimeException(e1);
        }
        return new DataFileLongIterator(oldFps);
    }

    /* (non-Javadoc)
     * @see org.archive.crawler.util.FPMergeUriUniqFilter#addNewFp(long)
     */
    protected void addNewFp(long fp) {
        try {
            newFps.writeLong(fp);
            newCount++;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /* (non-Javadoc)
     * @see org.archive.crawler.util.FPMergeUriUniqFilter#finishFpMerge()
     */
    protected void finishFpMerge() {
        try {
            newFps.close();
            File oldFpsFile = currentFps;
            currentFps = newFpsFile;
            if(oldFps!=null) {
                oldFps.close();
            }
            if(oldFpsFile!=null) {
                oldFpsFile.delete();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        count = newCount;
    }

    /* (non-Javadoc)
     * @see org.archive.crawler.datamodel.UriUniqFilter#count()
     */
    public long count() {
        return count;
    }

    public class DataFileLongIterator implements LongIterator {
        DataInputStream in; 
        long next;
        boolean nextIsValid = false; 
        
        /**
         * Construct a long iterator reading from the given 
         * stream. 
         * 
         * @param disStream DataInputStream from which to read longs
         */
        public DataFileLongIterator(DataInputStream disStream) {
            this.in = disStream;
        }

        /** 
         * Test whether any items remain; loads next item into
         * holding 'next' field. 
         * 
         * @see java.util.Iterator#hasNext()
         */
        public boolean hasNext() {
            return nextIsValid ? true: lookahead();
        }
        
        /**
         * Check if there's a next by trying to read it. 
         * 
         * @return true if 'next' field is filled with a valid next, false otherwise
         */
        protected boolean lookahead() {
            try {
                next = in.readLong();
            } catch (IOException e) {
                return false; 
            }
            nextIsValid = true; 
            return true; 
        }

        /** 
         * Return the next item.
         * 
         * @see java.util.Iterator#next()
         */
        public Long next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            // 'next' is guaranteed set by a hasNext() which returned true
            Long returnObj = new Long(this.next);
            this.nextIsValid = false;
            return returnObj;
        }
        
        /* (non-Javadoc)
         * @see java.util.Iterator#remove()
         */
        public void remove() {
            throw new UnsupportedOperationException();
        }
        
        
        /* (non-Javadoc)
         * @see it.unimi.dsi.fastutil.longs.LongIterator#nextLong()
         */
        public long nextLong() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            // 'next' is guaranteed non-null by a hasNext() which returned true
            this.nextIsValid = false; // after this return, 'next' needs refresh
            return this.next;
        }

        /* (non-Javadoc)
         * @see it.unimi.dsi.fastutil.longs.LongIterator#skip(int)
         */
        public int skip(int arg0) {
            return 0;
        }
    }

}
