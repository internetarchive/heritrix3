/* DNSJavaUtil
 * 
 * Created on Oct 8, 2004
 *
 * Copyright (C) 2004 Internet Archive.
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

import java.net.InetAddress;

import org.xbill.DNS.ARecord;
import org.xbill.DNS.DClass;
import org.xbill.DNS.Record;
import org.xbill.DNS.TextParseException;
import org.xbill.DNS.Type;
import org.xbill.DNS.Lookup;;

/**
 * Utility methods based on DNSJava.
 * Use these utilities to avoid having to use the native InetAddress lookup.
 * @author stack
 * @version $Date$, $Revision$
 */
public class DNSJavaUtil {
    private DNSJavaUtil() {
        super();
    }
    
    /**
     * Return an InetAddress for passed <code>host</code>.
     * 
     * If passed host is an IPv4 address, we'll not do a DNSJava
     * lookup.
     * 
     * @param host Host to lookup in dnsjava.
     * @return A host address or null if not found.
     */
    public static InetAddress getHostAddress(String host) {
        InetAddress hostAddress = InetAddressUtil.getIPHostAddress(host);
        if (hostAddress != null) {
            return hostAddress;
        }
        
        // Ask dnsjava for the inetaddress.  Should be in its cache.
        Record[] rrecordSet;
        try {
            rrecordSet = (new Lookup(host, Type.A, DClass.IN)).run();
        } catch (TextParseException e) {
            rrecordSet = null;
        }
        if (rrecordSet != null) {
            // Get TTL and IP info from the first A record (there may be
            // multiple, e.g. www.washington.edu).
            for (int i = 0; i < rrecordSet.length; i++) {
                if (rrecordSet[i].getType() != Type.A) {
                    continue;
                }
                hostAddress = ((ARecord)rrecordSet[i]).getAddress();
                break;
            }
        }
        return hostAddress;
    }
}
