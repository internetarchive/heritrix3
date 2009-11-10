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
package org.archive.util;

/**
 * @author stack
 * @version $Date$ $Revision$
 */
public class PropertyUtils {
    /***
     * @param key Property key.
     * @return Named property or null if the property is null or empty.
     */
    public static String getPropertyOrNull(final String key) {
        String value = System.getProperty(key);
        return (value == null || value.length() <= 0)? null: value;
    }

    /***
     * @param key Property key.
     * @return Boolean value or false if null or unreadable.
     */
    public static boolean getBooleanProperty(final String key) {
        return (getPropertyOrNull(key) == null)?
                false: Boolean.valueOf(getPropertyOrNull(key)).booleanValue();
    }   
    
    /**
     * @param key Key to use looking up system property.
     * @param fallback If no value found for passed <code>key</code>, return
     * <code>fallback</code>.
     * @return Value of property or <code>fallback</code>.
     */
    public static int getIntProperty(final String key, final int fallback) {
        return getPropertyOrNull(key) == null?
                fallback: Integer.parseInt(getPropertyOrNull(key));
    }
}
