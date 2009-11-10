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
