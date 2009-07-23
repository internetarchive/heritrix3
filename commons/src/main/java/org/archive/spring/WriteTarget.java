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

package org.archive.spring;

import java.io.Writer;

/**
 * Interface for objects that can provide a Writer for replacing or
 * appending to their textual contents.  
 * 
 */
public interface WriteTarget {
    /**
     * Obtain a Writer for changing this object's contents. By default, 
     * the Writer will replace the current contents. 
     * 
     * Note that the mere act of obtaining the Writer may (as in the 
     * case of a backing file) truncate away all existing contents, 
     * even before anything is written to the writer. Not named 
     * 'getWriter' because of this  potential destructiveness.
     * 
     * @return a Writer for replacing the object's textual contents
     */
    Writer obtainWriter(); 
    /**
     * Obtain a Writer for changing this object's contents. Whether 
     * written text replaces or appends existing contents is controlled
     * by the 'append' parameter. 
     * 
     * Note that the mere act of obtaining the Writer may (as in the 
     * case of a backing file) truncate away all existing contents, 
     * even before anything is written to the writer. Not named 
     * 'getWriter' because of this  potential destructiveness.
     * 
     * @param append if true, anything written will be appended to current contents
     * @return a Writer for replacing the object's textual contents
     */
    Writer obtainWriter(boolean append); 
}
