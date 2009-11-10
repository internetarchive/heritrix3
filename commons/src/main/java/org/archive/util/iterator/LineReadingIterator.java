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
package org.archive.util.iterator;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.logging.Logger;

/**
 * Utility class providing an Iterator interface over line-oriented
 * text input, as a thin wrapper over a BufferedReader.
 * 
 * @author gojomo
 */
public class LineReadingIterator extends LookaheadIterator<String> {
    private static final Logger logger =
        Logger.getLogger(LineReadingIterator.class.getName());

    protected BufferedReader reader = null;

    public LineReadingIterator(BufferedReader r) {
        reader = r;
    }

    /**
     * Loads next line into lookahead spot
     * 
     * @return whether any item was loaded into next field
     */
    protected boolean lookahead() {
        try {
            next = this.reader.readLine();
            if(next == null) {
                // TODO: make this close-on-exhaust optional?
                reader.close();
            }
            return (next!=null);
        } catch (IOException e) {
            logger.warning(e.toString());
            return false;
        }
    }
}
