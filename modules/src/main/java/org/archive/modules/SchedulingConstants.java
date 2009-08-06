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

package org.archive.modules;

/**
 * @author pjack
 *
 */
public class SchedulingConstants {

    
    /** Highest scheduling priority.
     * Before any others of its class.
     */
    public static final int HIGHEST = 0;
    
    /** High scheduling priority.
     * After any {@link #HIGHEST}.
     */
    public static final int HIGH = 1;
    
    /** Medium priority.
     * After any {@link #HIGH}.
     */
    public static final int MEDIUM = 2;
    
    /** Normal/low priority.
     * Whenever/end of queue.
     */
    public static final int NORMAL = 3;

    
    private SchedulingConstants() {
    }
}
