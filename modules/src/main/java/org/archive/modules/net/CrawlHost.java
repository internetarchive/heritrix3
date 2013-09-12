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

package org.archive.modules.net;

import java.io.Serializable;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.archive.bdb.AutoKryo;
import org.archive.modules.fetcher.FetchStats;
import org.archive.util.IdentityCacheable;
import org.archive.util.InetAddressUtil;
import org.archive.util.ObjectIdentityCache;

import com.esotericsoftware.kryo.Serializer;

/** 
 * Represents a single remote "host".
 *
 * An host is a name for which there is a dns record or an IP-address. This
 * might be a machine or a virtual host.
 *
 * @author gojomo
 */
public class CrawlHost implements Serializable, FetchStats.HasFetchStats, IdentityCacheable {

    private static final long serialVersionUID = -5494573967890942895L;

    private static final Logger logger = Logger.getLogger(CrawlHost.class.getName());
    /** Flag value indicating always-valid IP */
    public static final long IP_NEVER_EXPIRES = -1;
    /** Flag value indicating an IP has not yet been looked up */
    public static final long IP_NEVER_LOOKED_UP = -2;
    private String hostname;
    private String countryCode;
    private InetAddress ip;
    private long ipFetched = IP_NEVER_LOOKED_UP;
    protected FetchStats substats = new FetchStats(); 
    /**
     * TTL gotten from dns record.
     *
     * From rfc2035:
     * <pre>
     * TTL       a 32 bit unsigned integer that specifies the time
     *           interval (in seconds) that the resource record may be
     *           cached before it should be discarded.  Zero values are
     *           interpreted to mean that the RR can only be used for the
     *           transaction in progress, and should not be cached.
     * </pre>
     */
    private long ipTTL = IP_NEVER_LOOKED_UP;

    // Used when bandwith constraint are used
    private long earliestNextURIEmitTime = 0;
    
    /** 
     * Create a new CrawlHost object.
     *
     * @param hostname the host name for this host.
     */
    public CrawlHost(String hostname) {
    		this(hostname, null);
    }

    /** 
     * Create a new CrawlHost object.
     *
     * @param hostname the host name for this host.
     * @param countryCode the country code for this host.
     */
    public CrawlHost(String hostname, String countryCode) {
        this.hostname = hostname;
        this.countryCode = countryCode;
        InetAddress tmp = InetAddressUtil.getIPHostAddress(hostname);
        if (tmp != null) {
            setIP(tmp, IP_NEVER_EXPIRES);
        }
    }

    /** Return true if the IP for this host has been looked up.
     *
     * Returns true even if the lookup failed.
     *
     * @return true if the IP for this host has been looked up.
     */
    public boolean hasBeenLookedUp() {
        return ipFetched != IP_NEVER_LOOKED_UP;
    }

    /**
     * Set the IP address for this host.
     *
     * @param address
     * @param ttl the TTL from the dns record in seconds or -1 if it should live
     * forever (is a numeric IP).
     */
    public void setIP(InetAddress address, long ttl) {
        this.ip = address;
        // Assume that a lookup as occurred by the time
        // a caller decides to set this (even to null)
        this.ipFetched = System.currentTimeMillis();
        this.ipTTL = ttl;
        if (logger.isLoggable(Level.FINE)) {
            logger.fine(hostname + ": " +
                ((address != null)? address.toString(): "null"));
        }
    }

    /** Get the IP address for this host.
     *
     * @return the IP address for this host.
     */
    public InetAddress getIP() {
        return ip;
    }

    /** Get the time when the IP address for this host was last looked up.
     *
     * @return the time when the IP address for this host was last looked up.
     */
    public long getIpFetched() {
        return ipFetched;
    }

    /**
     * Get the TTL value from the dns record for this host.
     *
     * @return the TTL value from the dns record for this host -- in seconds --
     * or -1 if this lookup should be valid forever (numeric ip).
     */
    public long getIpTTL() {
        return this.ipTTL;
    }

    public String toString() {
        return "CrawlHost<" + hostname + "(ip:" + ip + ")>";
    }

    @Override
    public int hashCode() {
        return this.hostname != null ? this.hostname.hashCode() : 0;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final CrawlHost other = (CrawlHost) obj;
        if (this.hostname != other.hostname   // identity compare
                && (this.hostname == null 
                    || !this.hostname.equals(other.hostname))) {
            return false;
        }
        return true;
    }
    
    /**
     * Get the host name.
     * @return Returns the host name.
     */
    public String getHostName() {
        return hostname;
    }

    /** 
     * Get the earliest time a URI for this host could be emitted.
     * This only has effect if constraints on bandwidth per host is set.
     *
     * @return Returns the earliestNextURIEmitTime.
     */
    public long getEarliestNextURIEmitTime() {
        return earliestNextURIEmitTime;
    }

    /** 
     * Set the earliest time a URI for this host could be emitted.
     * This only has effect if constraints on bandwidth per host is set.
     *
     * @param earliestNextURIEmitTime The earliestNextURIEmitTime to set.
     */
    public void setEarliestNextURIEmitTime(long earliestNextURIEmitTime) {
        this.earliestNextURIEmitTime = earliestNextURIEmitTime;
    }

    /**
     * Get country code of this host
     * 
     * @return Retruns country code or null if not availabe 
     */
	public String getCountryCode() {
		return countryCode;
	}

	/**
	 * Set country code for this hos
	 * 
	 * @param countryCode The country code of this host
	 */
	public void setCountryCode(String countryCode) {
		this.countryCode = countryCode;
	}
    
    /* (non-Javadoc)
     * @see org.archive.crawler.datamodel.CrawlSubstats.HasCrawlSubstats#getSubstats()
     */
    public FetchStats getSubstats() {
        return substats;
    }
    
    // Kryo support
    public static void autoregisterTo(final AutoKryo kryo) {
        kryo.register(CrawlHost.class);
        kryo.autoregister(FetchStats.class);
        
        /*
         * Custom serializer because default serialization doesn't work. Any
         * non-null IP address comes back as 0.0.0.0. XXX Inet4Address also
         * holds hostname, but heritrix doesn't use that; and retrieving it can
         * result in dns lookup, so we don't serialize it.
         */
        kryo.register(Inet4Address.class, new Serializer() {
            @Override
            public void writeObjectData(ByteBuffer buffer, Object object) {
                Inet4Address i4a = (Inet4Address) object;
                kryo.writeObject(buffer, i4a.getAddress());
            }
            
            @Override
            @SuppressWarnings("unchecked")
            public <T> T readObjectData(ByteBuffer buffer, Class<T> type) {
                byte[] address = kryo.readObject(buffer, byte[].class);
                try {
                    return (T) InetAddress.getByAddress(address);
                } catch (UnknownHostException e) {
                    throw new RuntimeException(e);
                }
            }
        });
        kryo.autoregister(byte[].class);
        kryo.setRegistrationOptional(true);
    }
    
    //
    // IdentityCacheable support
    //
    transient private ObjectIdentityCache<?> cache;
    @Override
    public String getKey() {
        return getHostName();
    }

    @Override
    public void makeDirty() {
        cache.dirtyKey(getKey());
    }

    @Override
    public void setIdentityCache(ObjectIdentityCache<?> cache) {
        this.cache = cache; 
    } 
}
