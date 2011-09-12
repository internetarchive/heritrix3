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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import org.archive.util.FileUtils;

/**
 * Utilities useful checkpointing.
 * @author stack
 * @version $Date$ $Revision$
 */
public class CheckpointUtils {
    public static final String SERIALIZED_CLASS_SUFFIX = ".serialized";
    
    public static File getBdbSubDirectory(File checkpointDir) {
        return new File(checkpointDir, "bdbje-logs");
    }
    
    public static File getClassCheckpointFile(File checkpointDir,
            final String suffix, Class<?> c) {
        return new File(checkpointDir, getClassCheckpointFilename(c, suffix));
    }
    
    public static File getClassCheckpointFile(File checkpointDir, Class<?> c) {
        return new File(checkpointDir, getClassCheckpointFilename(c, null));
    }
    
    public static String getClassCheckpointFilename(final Class<?> c) {
        return getClassCheckpointFilename(c, null);
    }
    
    public static String getClassCheckpointFilename(final Class<?> c,
            final String suffix) {
        return c.getName() + ((suffix == null)? "": "." + suffix) +
            SERIALIZED_CLASS_SUFFIX;
    }
    
    /**
     * Utility function to serialize an object to a file in current checkpoint
     * dir. Facilities
     * to store related files alongside the serialized object in a directory
     * named with a <code>.auxillary</code> suffix.
     *
     * @param o Object to serialize.
     * @param dir Directory to serialize into.
     * @throws IOException
     */
    public static void writeObjectToFile(final Object o, final File dir)
    throws IOException {
        writeObjectToFile(o, null, dir);
    }
        
    public static void writeObjectToFile(final Object o, final String suffix,
            final File dir)
    throws IOException {
        FileUtils.ensureWriteableDirectory(dir);
        ObjectOutputStream out = new ObjectOutputStream(
            new FileOutputStream(getClassCheckpointFile(dir, suffix,
                o.getClass())));
        try {
            out.writeObject(o);
        } finally {
            out.close();
        }
    }
    
    public static <T> T readObjectFromFile(final Class<T> c, final File dir)
    throws FileNotFoundException, IOException, ClassNotFoundException {
        return readObjectFromFile(c, null, dir);
    }
    
    public static <T> T readObjectFromFile(final Class<T> c, final String suffix,
            final File dir)
    throws FileNotFoundException, IOException, ClassNotFoundException {
        ObjectInputStream in = new ObjectInputStream(
            new FileInputStream(getClassCheckpointFile(dir, suffix, c)));
        T o = null;
        try {
            o = c.cast(in.readObject());
        } finally {
            in.close();
        }
        return o;
    }

    /**
     * @return Instance of filename filter that will let through files ending
     * in '.jdb' (i.e. bdb je log files).
     */
    public static FilenameFilter getJeLogsFilter() {
        return new FilenameFilter() {
            public boolean accept(File dir, String name) {
                return name != null && name.toLowerCase().endsWith(".jdb");
            }
        };
    }
}
