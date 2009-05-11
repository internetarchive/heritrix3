/* ObjectPlusFilesOutputStream
*
* $Id$
*
* Created on Apr 28, 2004
*
* Copyright (C) 2004 Internet Archive.
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
package org.archive.io;

import java.io.File;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.util.LinkedList;

import org.archive.util.FileUtils;


/**
 * Enhanced ObjectOutputStream which maintains (a stack of) auxiliary
 * directories and offers convenience methods for serialized objects
 * to save their related disk files alongside their serialized version.
 *
 * @author gojomo
 */
public class ObjectPlusFilesOutputStream extends ObjectOutputStream {
    LinkedList<File> auxiliaryDirectoryStack = new LinkedList<File>();

    /**
     * Constructor
     *
     * @param out
     * @param topDirectory
     * @throws java.io.IOException
     */
    public ObjectPlusFilesOutputStream(OutputStream out, File topDirectory) throws IOException {
        super(out);
        auxiliaryDirectoryStack.addFirst(topDirectory);
    }

    /**
     * Add another subdirectory for any file-capture needs during the
     * current serialization.
     *
     * @param dir
     */
    public void pushAuxiliaryDirectory(String dir) {
        auxiliaryDirectoryStack.addFirst(new File(getAuxiliaryDirectory(),dir));
    }

    /**
     * Remove the top subdirectory.
     *
     */
    public void popAuxiliaryDirectory() {
        auxiliaryDirectoryStack.removeFirst();
    }

    /**
     * Return the current auxiliary directory for storing
     * files associated with serialized objects.
     *
     * @return Auxillary directory.
     */
    public File getAuxiliaryDirectory() {
        return (File)auxiliaryDirectoryStack.getFirst();
    }

    /**
     * Store a snapshot of an object's supporting file to the
     * current auxiliary directory. Should only be used for
     * files which are strictly appended-to, because it tries
     * to use a "hard link" where possible (meaning that
     * future edits to the original file's contents will
     * also affect the snapshot).
     *
     * Remembers current file extent to allow a future restore
     * to ignore subsequent appended data.
     *
     * @param file
     * @throws IOException
     */
    public void snapshotAppendOnlyFile(File file) throws IOException {
        // write filename
        String name = file.getName();
        writeUTF(name);
        // write current file length
        writeLong(file.length());
        File auxDir = getAuxiliaryDirectory();
        if(!auxDir.exists()) {
        	auxDir.mkdirs();
        }
        File destination = new File(auxDir,name);
        hardlinkOrCopy(file, destination);
    }

	/**
     * Create a backup of this given file, first by trying a "hard
     * link", then by using a copy if hard linking is unavailable
     * (either because it is unsupported or the origin and checkpoint
     * directories are on different volumes).
     *
	 * @param file
	 * @param destination
     * @throws IOException
	 */
	private void hardlinkOrCopy(File file, File destination) throws IOException {
		// For Linux/UNIX, try a hard link first.
        Process link = Runtime.getRuntime().exec("ln "+file.getAbsolutePath()+" "+destination.getAbsolutePath());
        // TODO NTFS also supports hard links; add appropriate try
        try {
			link.waitFor();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
        if(link.exitValue()!=0) {
        	// hard link failed
            FileUtils.copyFile(file,destination);
        }
	}

}
