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
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.util.Iterator;
import java.util.LinkedList;

import org.archive.util.FileUtils;


/**
 * Enhanced ObjectOutputStream with support for restoring
 * files that had been saved, in parallel with object
 * serialization.
 *
 * @author gojomo
 *
 */
public class ObjectPlusFilesInputStream extends ObjectInputStream {
    LinkedList<File> auxiliaryDirectoryStack = new LinkedList<File>();
    LinkedList<Runnable> postRestoreTasks = new LinkedList<Runnable>();

    /**
     * Instantiate over the given stream and using the supplied
     * auxiliary storage directory.
     *
     * @param in
     * @param storeDir
     * @throws IOException
     */
    public ObjectPlusFilesInputStream(InputStream in, File storeDir)
    throws IOException {
        super(in);
        auxiliaryDirectoryStack.addFirst(storeDir);
    }

    /**
     * Push another default storage directory for use
     * until popped.
     *
     * @param dir
     */
    public void pushAuxiliaryDirectory(String dir) {
        auxiliaryDirectoryStack.
            addFirst(new File(getAuxiliaryDirectory(), dir));
    }

    /**
     * Discard the top auxiliary directory.
     */
    public void popAuxiliaryDirectory() {
        auxiliaryDirectoryStack.removeFirst();
    }

    /**
     * Return the top auxiliary directory, from
     * which saved files are restored.
     *
     * @return Auxillary directory.
     */
    public File getAuxiliaryDirectory() {
        return (File)auxiliaryDirectoryStack.getFirst();
    }

    /**
     * Restore a file from storage, using the name and length
     * info on the serialization stream and the file from the
     * current auxiliary directory, to the given File.
     *
     * @param destination
     * @throws IOException
     */
    public void restoreFile(File destination) throws IOException {
        String nameAsStored = readUTF();
        long lengthAtStoreTime = readLong();
        File storedFile = new File(getAuxiliaryDirectory(),nameAsStored);
        FileUtils.copyFile(storedFile, destination, lengthAtStoreTime);
    }

    /**
     * Restore a file from storage, using the name and length
     * info on the serialization stream and the file from the
     * current auxiliary directory, to the given File.
     *
     * @param directory
     * @throws IOException
     */
    public void restoreFileTo(File directory) throws IOException {
        String nameAsStored = readUTF();
        long lengthAtStoreTime = readLong();
        File storedFile = new File(getAuxiliaryDirectory(),nameAsStored);
        File destination = new File(directory,nameAsStored);
        FileUtils.copyFile(storedFile, destination, lengthAtStoreTime);
    }

    /**
     * Register a task to be done when the ObjectPlusFilesInputStream
     * is closed.
     *
     * @param task
     */
    public void registerFinishTask(Runnable task) {
        postRestoreTasks.addFirst(task);
    }

    private void doFinishTasks() {
    	Iterator<Runnable> iter = postRestoreTasks.iterator();
    	while(iter.hasNext()) {
    		((Runnable)iter.next()).run();
        }
    }

    /**
     * In addition to default, do any registered cleanup tasks.
     *
     * @see java.io.InputStream#close()
     */
    public void close() throws IOException {
        super.close();
        doFinishTasks();
    }
}
