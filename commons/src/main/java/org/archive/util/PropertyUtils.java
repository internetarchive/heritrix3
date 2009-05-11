/* PropertyUtils.java
 *
 * Created Aug 4, 2005
 *
 * Copyright (C) 2005 Internet Archive.
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
