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
package org.archive.modules.fetcher;

import static org.archive.modules.fetcher.FetchStatusCodes.S_DNS_SUCCESS;
import static org.archive.modules.fetcher.FetchStatusCodes.S_DOMAIN_UNRESOLVABLE;
import static org.archive.modules.fetcher.FetchStatusCodes.S_GETBYNAME_SUCCESS;
import static org.archive.modules.fetcher.FetchStatusCodes.S_UNFETCHABLE_URI;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.MessageDigest;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;

import org.archive.url.URIException;
import org.apache.commons.lang3.StringUtils;
import org.archive.modules.CrawlURI;
import org.archive.modules.Processor;
import org.archive.modules.net.CrawlHost;
import org.archive.modules.net.ServerCache;
import org.archive.util.ArchiveUtils;
import org.archive.util.InetAddressUtil;
import org.archive.util.Recorder;
import org.springframework.beans.factory.annotation.Autowired;
import org.xbill.DNS.ARecord;
import org.xbill.DNS.DClass;
import org.xbill.DNS.DohResolver;
import org.xbill.DNS.Lookup;
import org.xbill.DNS.Record;
import org.xbill.DNS.ResolverConfig;
import org.xbill.DNS.TextParseException;
import org.xbill.DNS.Type;


/**
 * Processor to resolve 'dns:' URIs.
 * 
 * TODO: Refactor to use org.archive.util.DNSJavaUtils.
 *
 * @author multiple
 */
public class FetchDNS extends Processor {

    @SuppressWarnings("unused")
    private static final long serialVersionUID = 3L;

    private static Logger logger = Logger.getLogger(FetchDNS.class.getName());

    static {
        // cap size at 1 (we never want a cached value; 0 is non-operative)
        Lookup.getDefaultCache(DClass.IN).setMaxEntries(1);

        // do a dummy lookup to force the creation of dnsjava NIO selector thread
        // ensures it doesn't end up in the toe-threads group
        try {
            new Lookup("localhost").run();
        } catch (TextParseException e) {
            throw new RuntimeException(e);
        }
    }

    // Defaults.
    private short ClassType = DClass.IN;
    private short TypeType = Type.A;
    protected InetAddress serverInetAddr = null;

    {
        setAcceptNonDnsResolves(false);
    }
    public boolean getAcceptNonDnsResolves() {
        return (Boolean) kp.get("acceptNonDnsResolves");
    }
    /**
     * If a DNS lookup fails, whether or not to fall back to InetAddress
     * resolution, which may use local 'hosts' files or other mechanisms.
     */
    public void setAcceptNonDnsResolves(boolean acceptNonDnsResolves) {
        kp.put("acceptNonDnsResolves",acceptNonDnsResolves);
    }
    
    {
        setDisableJavaDnsResolves(false);
    }
    public boolean getDisableJavaDnsResolves() {
        return (Boolean) kp.get("disableJavaDnsResolves");
    }
    /**
     * Optionally, only allow InetAddress resolution, precisely because it
     * may use local 'hosts' files or other mechanisms.
     *
     * This should not generally be used in production as it will prevent
     * DNS lookups from being recorded properly.
     *
     */
    public void setDisableJavaDnsResolves(boolean disableJavaDnsResolves) {
        kp.put("disableJavaDnsResolves",disableJavaDnsResolves);
    }

    public String getDnsOverHttpServer() {
        return (String) kp.get("dnsOverHttpServer");
    }
    /**
     * URL to the DNS-on-HTTP(S) server.
     * If this not set or set to an empty string, no DNS-over-HTTP(S)
     * will be used; otherwise if should contain the URL to the
     * DNS-over-HTTPS server.
     */
    public void setDnsOverHttpServer(String dnsOverHttpServer) {
        kp.put("dnsOverHttpServer", dnsOverHttpServer);
    }
    
    /**
     * Used to do DNS lookups.
     */
    protected ServerCache serverCache;
    public ServerCache getServerCache() {
        return this.serverCache;
    }
    @Autowired
    public void setServerCache(ServerCache serverCache) {
        this.serverCache = serverCache;
    }
    
    {
        setDigestContent(true);
    }
    public boolean getDigestContent() {
        return (Boolean) kp.get("digestContent");
    }
    /**
     * Whether or not to perform an on-the-fly digest hash of retrieved
     * content-bodies.
     */
    public void setDigestContent(boolean digest) {
        kp.put("digestContent",digest);
    }

    protected String digestAlgorithm = "sha1";
    public String getDigestAlgorithm() {
        return digestAlgorithm;
    }
    /**
     * Which algorithm (for example MD5 or SHA-1) to use to perform an
     * on-the-fly digest hash of retrieved content-bodies.
     */
    public void setDigestAlgorithm(String digestAlgorithm) {
        this.digestAlgorithm = digestAlgorithm;
    }

    private static final long DEFAULT_TTL_FOR_NON_DNS_RESOLVES
        = 6 * 60 * 60; // 6 hrs

    public FetchDNS() {
    }

    protected boolean shouldProcess(CrawlURI curi) {
        return curi.getUURI().getScheme().equals("dns");
    }


    protected void innerProcess(CrawlURI curi) {
        Record[] rrecordSet = null; // Retrieved dns records
        String dnsName = null;
        try {
            dnsName = curi.getUURI().getReferencedHost();
        } catch (URIException e) {
            logger.log(Level.SEVERE, "Failed parse of dns record " + curi, e);
        }
        
        if(dnsName == null) {
            curi.setFetchStatus(S_UNFETCHABLE_URI);
            return;
        }

        CrawlHost targetHost = getServerCache().getHostFor(dnsName);
        if (isQuadAddress(curi, dnsName, targetHost)) {
        	// We're done processing.
        	return;
        }
        
        // Do actual DNS lookup.
        curi.setFetchBeginTime(System.currentTimeMillis());

        // Try to get the records for this host (assume domain name)
        // TODO: Bug #935119 concerns potential hang here
        String lookupName = dnsName.endsWith(".") ? dnsName : dnsName + ".";
        // If we have not disabled JavaDNS, use that:
        if (!getDisableJavaDnsResolves()) {
            try {
                rrecordSet = createDNSLookup(lookupName).run();
            } catch (TextParseException e) {
                rrecordSet = null;
            }
        }
        curi.setContentType("text/dns");
        if (rrecordSet != null) {
            if (logger.isLoggable(Level.FINE)) {
                logger.fine("Found recordset for " + lookupName);
            }
        	storeDNSRecord(curi, dnsName, targetHost, rrecordSet);
        } else {
            if (logger.isLoggable(Level.FINE)) {
                logger.fine("Failed find of recordset for " + lookupName);
            }
            if (getAcceptNonDnsResolves()||getDisableJavaDnsResolves()||"localhost".equals(dnsName)) {
                // Do lookup that bypasses javadns.
                InetAddress address = null;
                try {
                    address = InetAddress.getByName(dnsName);
                } catch (UnknownHostException e1) {
                    address = null;
                }
                if (address != null) {
                    targetHost.setIP(address, DEFAULT_TTL_FOR_NON_DNS_RESOLVES);
                    curi.setFetchStatus(S_GETBYNAME_SUCCESS);
                    curi.setContentSize(0);
                    if (logger.isLoggable(Level.FINE)) {
                        logger.fine("Found address for " + dnsName +
                            " using native dns.");
                    }
                } else {
                    if (logger.isLoggable(Level.FINE)) {
                        logger.fine("Failed find of address for " + dnsName +
                            " using native dns.");
                    }
                    setUnresolvable(curi, targetHost);
                }
            } else {
                setUnresolvable(curi, targetHost);
            }
        }
        curi.setFetchCompletedTime(System.currentTimeMillis());
    }
    
    protected void storeDNSRecord(final CrawlURI curi, final String dnsName,
    		final CrawlHost targetHost, final Record[] rrecordSet) {
        // Get TTL and IP info from the first A record (there may be
        // multiple, e.g. www.washington.edu) then update the CrawlServer
        ARecord arecord = getFirstARecord(rrecordSet);
        if (arecord == null) {
            throw new NullPointerException("Got null arecord for " +
                dnsName);
        }
        targetHost.setIP(arecord.getAddress(), arecord.getTTL());
        try {
        	recordDNS(curi, rrecordSet);
            curi.setFetchStatus(S_DNS_SUCCESS);
            curi.setServerIP(ResolverConfig.getCurrentConfig().server().getAddress().getHostAddress());
        } catch (IOException e) {
        	logger.log(Level.SEVERE, "Failed store of DNS Record for " +
        		curi.toString(), e);
        	setUnresolvable(curi, targetHost);
        }
    }
    
    protected boolean isQuadAddress(final CrawlURI curi, final String dnsName,
			final CrawlHost targetHost) {
		boolean result = false;
		Matcher matcher = InetAddressUtil.IPV4_QUADS.matcher(dnsName);
		// If it's an ip no need to do a lookup
		if (matcher == null || !matcher.matches()) {
			return result;
		}
		
		result = true;
		// Ideally this branch would never be reached: no CrawlURI
		// would be created for numerical IPs
		if (logger.isLoggable(Level.WARNING)) {
			logger.warning("Unnecessary DNS CrawlURI created: " + curi);
		}
		try {
			targetHost.setIP(InetAddress.getByAddress(dnsName, new byte[] {
					(byte) Integer.parseInt(matcher.group(1)),
					(byte) Integer.parseInt(matcher.group(2)),
					(byte) Integer.parseInt(matcher.group(3)),
					(byte) Integer.parseInt(matcher.group(4)) }),
					CrawlHost.IP_NEVER_EXPIRES); // Never expire numeric IPs
			curi.setFetchStatus(S_DNS_SUCCESS);
		} catch (UnknownHostException e) {
			logger.log(Level.SEVERE, "Should never be " + e.getMessage(), e);
			setUnresolvable(curi, targetHost);
		}
		return result;
	}
    
    protected void recordDNS(final CrawlURI curi, final Record[] rrecordSet)
            throws IOException {
        final byte[] dnsRecord = getDNSRecord(curi.getFetchBeginTime(),
                rrecordSet);

        Recorder rec = curi.getRecorder();
        // Shall we get a digest on the content downloaded?
        boolean digestContent = getDigestContent();
        String algorithm = null;
        if (digestContent) {
            algorithm = getDigestAlgorithm();
            rec.getRecordedInput().setDigest(algorithm);
        } else {
            rec.getRecordedInput().setDigest((MessageDigest)null);
        }
        InputStream is = curi.getRecorder().inputWrap(
                new ByteArrayInputStream(dnsRecord));

        if (digestContent) {
            rec.getRecordedInput().startDigest();
        }

        // Reading from the wrapped stream, behind the scenes, will write
        // files into scratch space
        try {
            byte[] buf = new byte[256];
            while (is.read(buf) != -1) {
                continue;
            }
        } finally {
            is.close();
            rec.closeRecorders();
        }
        curi.setContentSize(dnsRecord.length);

        if (digestContent) {
            curi.setContentDigest(algorithm,
                rec.getRecordedInput().getDigestValue());
        }
    }
    
    protected byte [] getDNSRecord(final long fetchStart,
    		final Record[] rrecordSet)
    throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        // Start the record with a 14-digit date per RFC 2540
        byte[] fetchDate = ArchiveUtils.get14DigitDate(fetchStart).getBytes();
        baos.write(fetchDate);
        // Don't forget the newline
        baos.write("\n".getBytes());
        if (rrecordSet != null) {
            for (int i = 0; i < rrecordSet.length; i++) {
                byte[] record = rrecordSet[i].toString().getBytes();
                baos.write(record);
                // Add the newline between records back in
                baos.write("\n".getBytes());
            }
        }
        return baos.toByteArray();
    }
    
    protected void setUnresolvable(CrawlURI curi, CrawlHost host) {
        host.setIP(null, 0);
        curi.setFetchStatus(S_DOMAIN_UNRESOLVABLE); 
    }
    
    protected ARecord getFirstARecord(Record[] rrecordSet) {
        ARecord arecord = null;
        if (rrecordSet == null || rrecordSet.length == 0) {
            if (logger.isLoggable(Level.FINEST)) {
                logger.finest("rrecordSet is null or zero length: " +
                    rrecordSet);
            }
            return arecord;
        }
        for (int i = 0; i < rrecordSet.length; i++) {
            if (rrecordSet[i].getType() != Type.A) {
                if (logger.isLoggable(Level.FINEST)) {
                    logger.finest("Record " + Integer.toString(i) +
                        " is not A type but " + rrecordSet[i].getType());
                }
                continue;
            }
            arecord = (ARecord) rrecordSet[i];
            break;
        }
        return arecord;
    }

    protected Lookup createDNSLookup(String lookupName)
            throws TextParseException {
        Lookup lookup = new Lookup(lookupName, TypeType, ClassType);

        String dohServer = getDnsOverHttpServer();
        if (StringUtils.isNotEmpty(dohServer)) {
            if (logger.isLoggable(Level.FINER)) {
                logger.log(Level.FINER,
                        "use dns on http with server " + dohServer);
            }

            DohResolver hts = new DohResolver(dohServer);
            lookup.setResolver(hts);
        }

        return lookup;
    }
}
