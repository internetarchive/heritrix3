/* Copyright (C) 2006 Internet Archive.
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
 *
 * ProcessorLevel.java
 * Created on December 14, 2006
 *
 * $Header$
 */
package org.archive.modules;

import java.util.logging.Level;


/**
 * Custom log levels for processors.
 * 
 * @author pjack
 */
public class ProcessorLevel extends Level {


    private static final long serialVersionUID = 1L;


    /**
     * Level for logging problematic URIs.
     */
    final public static ProcessorLevel URI = new ProcessorLevel("URI", 
            (WARNING.intValue() - INFO.intValue()) / 2 + INFO.intValue());
            // Halfway between INFO and WARNING
    
    private ProcessorLevel(String name, int level) {
        super(name, level);
    }

    
    private Object readResolve() {
        return URI;
    }
    
    
    public static void main(String args[]) {
        System.out.println(Level.INFO.intValue());
        System.out.println(Level.WARNING.intValue());
        System.out.println(Level.SEVERE.intValue());
    }
}
