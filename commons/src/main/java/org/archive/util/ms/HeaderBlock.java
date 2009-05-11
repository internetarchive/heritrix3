/* HeaderBlock
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


import java.nio.ByteBuffer;
import java.nio.ByteOrder;


class HeaderBlock {

    
    private ByteBuffer buffer;
    
    
    public HeaderBlock(ByteBuffer buffer) {
        // FIXME: Read the fields we're interested in directly from stream
        this.buffer = buffer;
        buffer.order(ByteOrder.LITTLE_ENDIAN);
    }
    
    
    public long getFileType() {
        return buffer.getLong(0);
    }
    
    
    public int getBATCount() {
        return buffer.getInt(0x2C);
    }
    
    
    public int getEntriesStart() {
        return buffer.getInt(0x30);
    }
    
    
    public int getSmallBATStart() {
        return buffer.getInt(0x3C);
    }
    
    
    public int getSmallBATCount() {
        return buffer.getInt(0x40);
    }
    
    
    public int getExtendedBATStart() {
        return buffer.getInt(0x44);
    }
    
    
    public int getExtendedBATCount() {
        return buffer.getInt(0x48);
    }
    
    
    public int getBATBlockNumber(int block) {
        assert block < 110;
        return buffer.getInt(0x4C + block * 4);
    }

    
    public String toString() {
        StringBuilder sb = new StringBuilder("HeaderBlock{");
        sb.append("fileType=" + getFileType());
        sb.append(" propertiesStart=" + getEntriesStart());
        sb.append(" batCount=" + getBATCount());
        sb.append(" extendedBATStart=" + getExtendedBATStart());
        sb.append(" extendedBATCount=" + getExtendedBATCount());
        sb.append(" smallBATStart=" + getSmallBATStart());
        sb.append(" smallBATCount=" + getSmallBATCount());
        sb.append("}");
        return sb.toString();
    }

}
