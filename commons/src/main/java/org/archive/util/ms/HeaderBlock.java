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
