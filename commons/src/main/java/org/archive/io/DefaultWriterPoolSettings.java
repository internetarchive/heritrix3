/* 
 * Copyright (C) 2007 Internet Archive.
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
 *
 * DefaultWriterPoolSettings.java
 *
 * Created on Mar 11, 2007
 *
 * $Id:$
 */

package org.archive.io;

import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import org.archive.checkpointing.CheckpointRecovery;

/**
 * @author pjack
 *
 */
public class DefaultWriterPoolSettings 
implements WriterPoolSettings, Serializable {


    private static final long serialVersionUID = 1L;


    private long maxSize;
    private List<String> metadata = new ArrayList<String>();;
    transient private List<File> outputDirs = new ArrayList<File>();
    private String prefix;
    private String suffix;
    private boolean compressed;

    
    public DefaultWriterPoolSettings() {
    }
    
    
    public boolean isCompressed() {
        return compressed;
    }
    
    
    public void setCompressed(boolean compressed) {
        this.compressed = compressed;
    }
    
    
    public long getMaxSize() {
        return maxSize;
    }
    
    
    public void setMaxSize(long maxSize) {
        this.maxSize = maxSize;
    }
    
    
    public List<String> getMetadata() {
        return metadata;
    }
    
    
    public void setMetadata(List<String> metadata) {
        this.metadata = metadata;
    }
    
    
    public List<File> getOutputDirs() {
        return outputDirs;
    }
    
    
    public void setOutputDirs(List<File> outputDirs) {
        this.outputDirs = outputDirs;
    }
    
    
    public String getPrefix() {
        return prefix;
    }
    
    
    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }
    
    
    public String getSuffix() {
        return suffix;
    }
    
    
    public void setSuffix(String suffix) {
        this.suffix = suffix;
    }


    private void writeObject(ObjectOutputStream out) throws IOException {
        out.defaultWriteObject();
        out.writeInt(outputDirs.size());
        for (File f: outputDirs) {
            out.writeUTF(f.getAbsolutePath());
        }
    }

    
    private void readObject(ObjectInputStream input) 
    throws IOException, ClassNotFoundException {
        input.defaultReadObject();
        CheckpointRecovery cr = null;
        this.outputDirs = new ArrayList<File>();
        if (input instanceof CheckpointRecovery) {
            cr = (CheckpointRecovery)input;
        }
        int size = input.readInt();
        for (int i = 0; i < size; i++) {
            String path = input.readUTF();
            if (cr != null) {
                path = cr.translatePath(path);
            }
            File f = new File(path);
            f.mkdirs();
            outputDirs.add(f);
        }
    }
}
