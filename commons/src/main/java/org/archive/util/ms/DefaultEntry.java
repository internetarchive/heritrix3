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
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

import org.archive.util.ArchiveUtils;
import org.archive.io.SeekInputStream;

class DefaultEntry implements Entry {

    
    private DefaultBlockFileSystem origin;
    private String name;
    private EntryType type;
    private int previous;
    private int next;
    private int child;
    private int startBlock;
    private int size;
    private int index;
    
    
    public DefaultEntry(DefaultBlockFileSystem origin, SeekInputStream input, int index) 
    throws IOException {
        this.index = index;
        // FIXME: Read directly from the stream
        this.origin = origin;
        byte[] temp = new byte[128];
        ArchiveUtils.readFully(input, temp);
        ByteBuffer buf = ByteBuffer.wrap(temp);
        buf.order(ByteOrder.LITTLE_ENDIAN);
        buf.position(0);
        
        StringBuilder nameBuf = new StringBuilder();
        
        char ch = buf.getChar();
        while (ch != 0) {
            nameBuf.append(ch);
            ch = buf.getChar();
        }
        this.name = nameBuf.toString();
        
        byte typeFlag = buf.get(0x42);
        switch (typeFlag) {
            case 1:
                this.type = EntryType.DIRECTORY;
                break;
            case 2:
                this.type = EntryType.FILE;
                break;
            case 5:
                this.type = EntryType.ROOT;
                break;
            default:
                throw new IllegalStateException("Invalid type: " + typeFlag);
        }
        
        this.previous = buf.getInt(0x44);
        this.next = buf.getInt(0x48);
        this.child = buf.getInt(0x4C);
        this.startBlock = buf.getInt(0x74);
        this.size = buf.getInt(0x78);
    }
    
    
    public String getName() {
        return name;
    }
    
    
    public EntryType getType() {
        return type;
    }
   
    
    public Entry getNext() throws IOException {
        return origin.getEntry(next);
    }
    
    
    public Entry getPrevious() throws IOException {
        return origin.getEntry(previous);
    }
    
    
    public Entry getChild() throws IOException {
        return origin.getEntry(child);
    }
    
    public SeekInputStream open() throws IOException {
        return new BlockInputStream(origin, startBlock);
    }


    public List<Entry> list() throws IOException {
        if (child < 0) {
            throw new IllegalStateException("Can't list non-directory.");
        }
        Entry child = getChild();
        ArrayList<Entry> r = new ArrayList<Entry>();
        list(r, child);
        return r;
    }


    public static void list(List<Entry> list, Entry e) throws IOException {
        if (e == null) {
            return;
        }
        list.add(e);
        list(list, e.getPrevious());
        list(list, e.getNext());
    }


    public int getIndex() {
        return index;
    }


    public String toString() {
        StringBuilder result = new StringBuilder("Entry{");
        result.append("name=").append(name);
        result.append(" index=").append(index);
        result.append(" type=").append(type);
        result.append(" size=").append(size);
        result.append(" prev=").append(previous);
        result.append(" next=").append(next);
        result.append(" child=").append(child);
        result.append(" startBlock=").append(startBlock);
        result.append("}");
        return result.toString();
    }


}
