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
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * InetAddress utility.
 * @author stack
 * @version $Date$, $Revision$
 */
public class InetAddressUtil {
    private static Logger logger =
        Logger.getLogger(InetAddressUtil.class.getName());
    
    /**
     * ipv4 address.
     */
    public static Pattern IPV4_QUADS = Pattern.compile(
        "([0-9]{1,3})\\.([0-9]{1,3})\\.([0-9]{1,3})\\.([0-9]{1,3})");
    
    private InetAddressUtil () {
        super();
    }
    
    /**
     * Returns InetAddress for passed <code>host</code> IF its in
     * IPV4 quads format (e.g. 128.128.128.128).
     * <p>TODO: Move to an AddressParsingUtil class.
     * @param host Host name to examine.
     * @return InetAddress IF the passed name was an IP address, else null.
     */
    public static InetAddress getIPHostAddress(String host) {
        InetAddress result = null;
        Matcher matcher = IPV4_QUADS.matcher(host);
        if (matcher == null || !matcher.matches()) {
            return result;
        }
        try {
            // Doing an Inet.getByAddress() avoids a lookup.
            result = InetAddress.getByAddress(host,
                    new byte[] {
                    (byte)(new Integer(matcher.group(1)).intValue()),
                    (byte)(new Integer(matcher.group(2)).intValue()),
                    (byte)(new Integer(matcher.group(3)).intValue()),
                    (byte)(new Integer(matcher.group(4)).intValue())});
        } catch (NumberFormatException e) {
            logger.warning(e.getMessage());
        } catch (UnknownHostException e) {
            logger.warning(e.getMessage());
        }
        return result;
    }
    
    /**
     * @return All known local names for this host or null if none found.
     */
    public static List<String> getAllLocalHostNames() {
        List<String> localNames = new ArrayList<String>();
        Enumeration<NetworkInterface> e = null;
        try {
            e = NetworkInterface.getNetworkInterfaces();
        } catch(SocketException exception) {
            throw new RuntimeException(exception);
        }
        for (; e.hasMoreElements();) {
            for (Enumeration<InetAddress> ee = e.nextElement().getInetAddresses();
                    ee.hasMoreElements();) {
                InetAddress ia = ee.nextElement();
                if (ia != null) {
                    if (ia.getHostName() != null) {
                        localNames.add(ia.getCanonicalHostName());
                    }
                    if (ia.getHostAddress() !=  null) {
                        localNames.add(ia.getHostAddress());
                    }
                }
            }
        }
        final String localhost = "localhost";
        if (!localNames.contains(localhost)) {
            localNames.add(localhost);
        }
        final String localhostLocaldomain = "localhost.localdomain";
        if (!localNames.contains(localhostLocaldomain)) {
            localNames.add(localhostLocaldomain);
        }
        return localNames;
    }
}