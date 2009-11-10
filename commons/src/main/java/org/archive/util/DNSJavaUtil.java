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
