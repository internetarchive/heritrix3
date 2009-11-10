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
