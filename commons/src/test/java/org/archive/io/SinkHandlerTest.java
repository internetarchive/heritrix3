/* SinkHandlerTest.java
 *
 * $Id$
 *
 * Created Aug 9, 2005
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
package org.archive.io;

import junit.framework.TestCase;

public class SinkHandlerTest extends TestCase {

    public void testNothing() {
    }  

// Commented out the below test code because it, like so many other
// things that are good in the world, does not work with maven2.

// maven2 doesn't usually use the system class loader when executing 
// tests, and the below code requires the system class loader for 
// loading the LogHandler (per LogHandler restrictions).

// We could configure maven2 to use the system class loader, but then
// we would have to use the same classpath as maven2.  Unfortunately
// there's a conflict:  maven2 uses commons-lang-2.1, and we require
// commons-lang-2.3.  
  
/*    protected void setUp() throws Exception {
        super.setUp();
        Class.forName("org.archive.io.SinkHandler");
        String logConfig = "handlers = " +
            "org.archive.io.SinkHandler\n" +
            "org.archive.io.SinkHandler.level = ALL";
        ByteArrayInputStream bais =
            new ByteArrayInputStream(logConfig.getBytes());
        LogManager.getLogManager().readConfiguration(bais);
    }
    
    public void testLogging() throws Exception {
        LogRecord lr = new LogRecord(Level.SEVERE, "");
        long base = lr.getSequenceNumber() + 1;
        System.out.println(base);
        LOGGER.severe("Test1");
        LOGGER.severe("Test2");
        LOGGER.warning("Test3");
        RuntimeException e = new RuntimeException("Nothing exception");
        LOGGER.log(Level.SEVERE, "with exception", e);
        SinkHandler h = SinkHandler.getInstance();
        assertEquals(h.getAllUnread().size(), 4);
        SinkHandlerLogRecord shlr = h.get(base + 3);
        assertTrue(shlr != null);
        h.remove(base + 3);
        assertEquals(h.getAllUnread().size(), 3);
        h.publish(shlr);
        assertEquals(h.getAllUnread().size(), 4);
    } */
    /*
    public void testToString() throws Exception {
        RuntimeException e = new RuntimeException("Some-Message");
        LOGGER.log(Level.SEVERE, "With-Exception", e);
        SinkHandler h = SinkHandler.getInstance();
        System.out.print(((SeenLogRecord)h.getSink().get(0)).toString());
        LOGGER.log(Level.SEVERE, "No-Exception");
        System.out.print(((SeenLogRecord)h.getSink().get(1)).toString());
    }*/
}
