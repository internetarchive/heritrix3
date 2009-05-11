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

class Label extends SubElement {
	public static final char COLON = ':';
	
    @SuppressWarnings("unused")
    private Label() {
        this(null);
    }
    
    public Label(final String s) {
        super(s);
    }
    
    @Override
    protected void checkCharacter(char c, String srcStr, int index) {
    	super.checkCharacter(c, srcStr, index);
    	if (c == COLON) {
    		throw new IllegalArgumentException("Label cannot contain " + COLON);
    	}
    }
}