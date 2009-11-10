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

import org.archive.io.SeekInputStream;


/**
 * Describes the internal file system contained in .doc files.
 */
public interface BlockFileSystem {

    
    /**
     * The size of a block in bytes.
     */
    int BLOCK_SIZE = 512;


    /**
     * Returns the root entry of the file system.  Subfiles and directories
     * can be found by searching the returned entry.
     * 
     * @return  the root entry
     * @throws IOException  if an IO error occurs
     */
    public abstract Entry getRoot() throws IOException;

    
    /**
     * Returns the number of the block that follows the given block.
     * The internal block allocation tables are consulted to determine the
     * next block.  A return value that is less than zero indicates that
     * there is no next block.
     * 
     * @param block   the number of block whose successor to return
     * @return  the successor of that block
     * @throws IOException  if an IO error occurs
     */
    public abstract int getNextBlock(int block) throws IOException;


    /**
     * Returns the raw input stream for this file system.  
     * Typically this will be the random access file containing the .doc.
     * 
     * @return  the raw input stream for this file system
     */
    public abstract SeekInputStream getRawInput();

}