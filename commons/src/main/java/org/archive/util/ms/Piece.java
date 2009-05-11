/* Piece
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
