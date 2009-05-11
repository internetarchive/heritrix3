/* Element
*
* $Id$
*
* Created on July 26, 2006.
*
* Copyright (C) 2006 Internet Archive.e.
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
package org.archive.util.anvl;


/**
 * ANVL 'data element'.
 * Made of a lone {@link Label}, or a {@link Label} plus {@link Value}.
 * 
 * @author stack
 * @see <a
 * href="http://www.cdlib.org/inside/diglib/ark/anvlspec.pdf">A Name-Value
 * Language (ANVL)</a>
 */
public class Element {
    private final SubElement [] subElements;
    
    public Element(final Label l) {
        this.subElements = new SubElement [] {l};
    }
    
    public Element(final Label l, final Value v) {
        this.subElements = new SubElement [] {l, v};
    }
    
    public boolean isValue() {
        return this.subElements.length > 1;
    }
    
    public Label getLabel() {
        return (Label)this.subElements[0];
    }
    
    public Value getValue() {
        if (!isValue()) {
            return null;
        }
        return (Value)this.subElements[1];
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < subElements.length; i++) {
            sb.append(subElements[i].toString());
            if (i == 0) {
                // Add colon after Label.
                sb.append(':');
                if (isValue()) {
                    // Add space to intro the value.
                    sb.append(' ');
                }
            }
        }
        return sb.toString();
    }
}
