/* DefaultEntry
*
* Created on September 12, 2006
*
* Copyright (C) 2006 Internet Archive.
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
package org.archive.util.ms;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

import org.archive.util.IoUtils;
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
        IoUtils.readFully(input, temp);
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
