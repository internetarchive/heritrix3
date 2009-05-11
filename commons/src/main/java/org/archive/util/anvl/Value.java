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
 * TODO: Now values 'fold' but should but perhaps they shouldn't be stored
 * folded.  Only when we serialize should we fold (But how to know where
 * to fold?).
 * @author stack
 * @version $Date$ $Version$
 */
class Value extends SubElement {

    private StringBuilder sb;
    private boolean folding = false;
	
    @SuppressWarnings("unused")
    private Value() {
        this(null);
    }
    
    public Value(final String s) {
        super(s);
    }
    
    protected String baseCheck(String s) {
        this.sb = new StringBuilder(s.length() * 2);
        super.baseCheck(s);
        return sb.toString();
    }
    
    @Override
    protected void checkCharacter(char c, String srcStr, int index) {
        checkControlCharacter(c, srcStr, index);
        // Now, rewrite the value String with folding (If CR or LF or CRLF
        // present.
        if (ANVLRecord.isCR(c)) {
            this.folding = true;
            this.sb.append(ANVLRecord.FOLD_PREFIX);
        } else if (ANVLRecord.isLF(c)) {
            if (!this.folding) {
                this.folding = true;
                this.sb.append(ANVLRecord.FOLD_PREFIX);
            } else {
                // Previous character was a CR. Fold prefix has been added.
            }
        } else if (this.folding && Character.isWhitespace(c)) {
            // Only write out one whitespace character. Skip.
        } else {
            this.folding = false;
            this.sb.append(c);
        }
    }
}