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

public class Piece {

    private boolean unicode;
    private int charPosStart;
    private int charPosLimit;
    private int filePos;

    
    public Piece(int filePos, int start, int end, boolean unicode) {
        this.filePos = filePos;
        this.charPosStart = start;
        this.charPosLimit = end;
        this.unicode = unicode;
    }


    /**
     * 
     * @return
     */
    public int getFilePos() {
        return filePos;
    }


    /**
     * 
     * @return
     */
    public int getCharPosLimit() {
        return charPosLimit;
    }

    
    public int getCharPosStart() {
        return charPosStart;
    }

    /**
     * 
     * @return
     */
    public boolean isUnicode() {
        return unicode;
    }


    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Piece{filePos=").append(filePos);
        sb.append(" start=").append(charPosStart);
        sb.append(" end=").append(charPosLimit);
        sb.append(" unicode=").append(unicode);
        sb.append("}");
        return sb.toString();
    }
    
    
    public boolean contains(int charPos) {
        return (charPos >= charPosStart) && (charPos < charPosLimit);
    }
}
