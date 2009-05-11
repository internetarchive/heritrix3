/* DiskFPMergeUriUniqFilter
*
* $Id$
*
* Created on Dec 14, 2005
*
* Copyright (C) 2005 Internet Archive.
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
    long count = 0; 
    File scratchDir; 
    File currentFps;
    File newFpsFile;
    DataOutputStream newFps; 
    long newCount; 
    DataInputStream oldFps; 
    
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
