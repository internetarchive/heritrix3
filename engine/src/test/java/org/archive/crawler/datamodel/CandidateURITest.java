/* CandidateURITest.java
 *
 * $Id$
 *
 * Created Jun 23, 2005
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
package org.archive.crawler.datamodel;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import junit.framework.TestCase;

import org.archive.net.UURIFactory;

/**
 * Test  CandidateURI serialization.
 * @author stack
 */
public class CandidateURITest extends TestCase {
    public void testSerialization()
    throws IOException, ClassNotFoundException {
        doOneSerialization("http://www.archive.org/");
        doOneSerialization("http://www.archive.org/a?" +
            "sch=%2E%2F%3Faction%3Dsearch");
    }
    
    private void doOneSerialization(final String urlStr)
    throws IOException, ClassNotFoundException {
        CrawlURI cauri =
            new CrawlURI(UURIFactory.getInstance(urlStr));
        cauri = serialize(cauri);
        assertEquals(urlStr + " doesn't serialize", urlStr,
            cauri.getUURI().toString());  
    }
    
    private CrawlURI serialize(CrawlURI cauri)
    throws IOException, ClassNotFoundException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        oos.writeObject(cauri);
        oos.flush();
        oos.close();
        ByteArrayInputStream bais =
            new ByteArrayInputStream(baos.toByteArray());
        return (CrawlURI)(new ObjectInputStream(bais)).readObject();
    }
}
