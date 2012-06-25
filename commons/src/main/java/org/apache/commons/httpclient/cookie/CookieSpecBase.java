/*
 * $Header: /home/jerenkrantz/tmp/commons/commons-convert/cvs/home/cvs/jakarta-commons//httpclient/src/java/org/apache/commons/httpclient/cookie/CookieSpecBase.java,v 1.28 2004/11/06 19:15:42 mbecke Exp $
 * $Revision$
 * $Date$
 *
 * ====================================================================
 *
 *  Copyright 2002-2004 The Apache Software Foundation
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 *
 */ 

package org.apache.commons.httpclient.cookie;

import java.util.Collection;
import java.util.Date;
import java.util.Iterator; // <- IA/HERITRIX CHANGE
import java.util.LinkedList;
import java.util.List;
import java.util.SortedMap; // <- IA/HERITRIX CHANGE

import org.apache.commons.httpclient.Cookie;
import org.apache.commons.httpclient.Header;
import org.apache.commons.httpclient.HeaderElement;
import org.apache.commons.httpclient.NameValuePair;
import org.apache.commons.httpclient.util.DateParseException;
import org.apache.commons.httpclient.util.DateUtil;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.google.common.net.InternetDomainName; // <- IA/HERITRIX CHANGE
import com.sleepycat.collections.StoredIterator; // <- IA/HERITRIX CHANGE

/**
 * 
 * Cookie management functions shared by all specification.
 *
 * @author  B.C. Holmes
 * @author <a href="mailto:jericho@thinkfree.com">Park, Sung-Gu</a>
 * @author <a href="mailto:dsale@us.britannica.com">Doug Sale</a>
 * @author Rod Waldhoff
 * @author dIon Gillard
 * @author Sean C. Sullivan
 * @author <a href="mailto:JEvans@Cyveillance.com">John Evans</a>
 * @author Marc A. Saegesser
 * @author <a href="mailto:oleg@ural.ru">Oleg Kalnichevski</a>
 * @author <a href="mailto:mbowler@GargoyleSoftware.com">Mike Bowler</a>
 * 
 * @since 2.0 
 */
public class CookieSpecBase implements CookieSpec {
    
    /** Log object */
    protected static final Log LOG = LogFactory.getLog(CookieSpec.class);

    /** Valid date patterns */
    private Collection<String> datepatterns = null;
    
    /** Default constructor */
    public CookieSpecBase() {
        super();
    }


    /**
      * Parses the Set-Cookie value into an array of <tt>Cookie</tt>s.
      *
      * <P>The syntax for the Set-Cookie response header is:
      *
      * <PRE>
      * set-cookie      =    "Set-Cookie:" cookies
      * cookies         =    1#cookie
      * cookie          =    NAME "=" VALUE * (";" cookie-av)
      * NAME            =    attr
      * VALUE           =    value
      * cookie-av       =    "Comment" "=" value
      *                 |    "Domain" "=" value
      *                 |    "Max-Age" "=" value
      *                 |    "Path" "=" value
      *                 |    "Secure"
      *                 |    "Version" "=" 1*DIGIT
      * </PRE>
      *
      * @param host the host from which the <tt>Set-Cookie</tt> value was
      * received
      * @param port the port from which the <tt>Set-Cookie</tt> value was
      * received
      * @param path the path from which the <tt>Set-Cookie</tt> value was
      * received
      * @param secure <tt>true</tt> when the <tt>Set-Cookie</tt> value was
      * received over secure conection
      * @param header the <tt>Set-Cookie</tt> received from the server
      * @return an array of <tt>Cookie</tt>s parsed from the Set-Cookie value
      * @throws MalformedCookieException if an exception occurs during parsing
      */
    public Cookie[] parse(String host, int port, String path, 
        boolean secure, final String header) 
        throws MalformedCookieException {
            
        LOG.trace("enter CookieSpecBase.parse(" 
            + "String, port, path, boolean, Header)");

        if (host == null) {
            throw new IllegalArgumentException(
                "Host of origin may not be null");
        }
        if (host.trim().equals("")) {
            throw new IllegalArgumentException(
                "Host of origin may not be blank");
        }
        if (port < 0) {
            throw new IllegalArgumentException("Invalid port: " + port);
        }
        if (path == null) {
            throw new IllegalArgumentException(
                "Path of origin may not be null.");
        }
        if (header == null) {
            throw new IllegalArgumentException("Header may not be null.");
        }

        if (path.trim().equals("")) {
            path = PATH_DELIM;
        }
        host = host.toLowerCase();

        String defaultPath = path;    
        int lastSlashIndex = defaultPath.lastIndexOf(PATH_DELIM);
        if (lastSlashIndex >= 0) {
            if (lastSlashIndex == 0) {
                //Do not remove the very first slash
                lastSlashIndex = 1;
            }
            defaultPath = defaultPath.substring(0, lastSlashIndex);
        }

        HeaderElement[] headerElements = null;

        boolean isNetscapeCookie = false; 
        int i1 = header.toLowerCase().indexOf("expires=");
        if (i1 != -1) {
            i1 += "expires=".length();
            int i2 = header.indexOf(";", i1);
            if (i2 == -1) {
                i2 = header.length(); 
            }
            try {
                DateUtil.parseDate(header.substring(i1, i2), this.datepatterns);
                isNetscapeCookie = true; 
            } catch (DateParseException e) {
                // Does not look like a valid expiry date
            }
        }
        if (isNetscapeCookie) {
            headerElements = new HeaderElement[] {
                    new HeaderElement(header.toCharArray())
            };
        } else {
            headerElements = HeaderElement.parseElements(header.toCharArray());
        }
        
        Cookie[] cookies = new Cookie[headerElements.length];

        for (int i = 0; i < headerElements.length; i++) {

            HeaderElement headerelement = headerElements[i];
            Cookie cookie = null;
            try {
                cookie = new Cookie(host,
                                    headerelement.getName(),
                                    headerelement.getValue(),
                                    defaultPath, 
                                    null,
                                    false);
            } catch (IllegalArgumentException e) {
                throw new MalformedCookieException(e.getMessage()); 
            }
            // cycle through the parameters
            NameValuePair[] parameters = headerelement.getParameters();
            // could be null. In case only a header element and no parameters.
            if (parameters != null) {

                for (int j = 0; j < parameters.length; j++) {
                    parseAttribute(parameters[j], cookie);
                }
            }
            cookies[i] = cookie;
        }
        return cookies;
    }


    /**
      * Parse the <tt>"Set-Cookie"</tt> {@link Header} into an array of {@link
      * Cookie}s.
      *
      * <P>The syntax for the Set-Cookie response header is:
      *
      * <PRE>
      * set-cookie      =    "Set-Cookie:" cookies
      * cookies         =    1#cookie
      * cookie          =    NAME "=" VALUE * (";" cookie-av)
      * NAME            =    attr
      * VALUE           =    value
      * cookie-av       =    "Comment" "=" value
      *                 |    "Domain" "=" value
      *                 |    "Max-Age" "=" value
      *                 |    "Path" "=" value
      *                 |    "Secure"
      *                 |    "Version" "=" 1*DIGIT
      * </PRE>
      *
      * @param host the host from which the <tt>Set-Cookie</tt> header was
      * received
      * @param port the port from which the <tt>Set-Cookie</tt> header was
      * received
      * @param path the path from which the <tt>Set-Cookie</tt> header was
      * received
      * @param secure <tt>true</tt> when the <tt>Set-Cookie</tt> header was
      * received over secure conection
      * @param header the <tt>Set-Cookie</tt> received from the server
      * @return an array of <tt>Cookie</tt>s parsed from the <tt>"Set-Cookie"
      * </tt> header
      * @throws MalformedCookieException if an exception occurs during parsing
      */
    public Cookie[] parse(
        String host, int port, String path, boolean secure, final Header header)
        throws MalformedCookieException {
            
        LOG.trace("enter CookieSpecBase.parse("
            + "String, port, path, boolean, String)");
        if (header == null) {
            throw new IllegalArgumentException("Header may not be null.");
        }
        return parse(host, port, path, secure, header.getValue());
    }


    /**
      * Parse the cookie attribute and update the corresponsing {@link Cookie}
      * properties.
      *
      * @param attribute {@link HeaderElement} cookie attribute from the
      * <tt>Set- Cookie</tt>
      * @param cookie {@link Cookie} to be updated
      * @throws MalformedCookieException if an exception occurs during parsing
      */

    public void parseAttribute(
        final NameValuePair attribute, final Cookie cookie)
        throws MalformedCookieException {
            
        if (attribute == null) {
            throw new IllegalArgumentException("Attribute may not be null.");
        }
        if (cookie == null) {
            throw new IllegalArgumentException("Cookie may not be null.");
        }
        final String paramName = attribute.getName().toLowerCase();
        String paramValue = attribute.getValue();

        if (paramName.equals("path")) {

            if ((paramValue == null) || (paramValue.trim().equals(""))) {
                paramValue = "/";
            }
            cookie.setPath(paramValue);
            cookie.setPathAttributeSpecified(true);

        } else if (paramName.equals("domain")) {

            if (paramValue == null) {
                throw new MalformedCookieException(
                    "Missing value for domain attribute");
            }
            if (paramValue.trim().equals("")) {
                throw new MalformedCookieException(
                    "Blank value for domain attribute");
            }
            cookie.setDomain(paramValue);
            cookie.setDomainAttributeSpecified(true);

        } else if (paramName.equals("max-age")) {

            if (paramValue == null) {
                throw new MalformedCookieException(
                    "Missing value for max-age attribute");
            }
            int age;
            try {
                age = Integer.parseInt(paramValue);
            } catch (NumberFormatException e) {
                throw new MalformedCookieException ("Invalid max-age "
                    + "attribute: " + e.getMessage());
            }
            cookie.setExpiryDate(
                new Date(System.currentTimeMillis() + age * 1000L));

        } else if (paramName.equals("secure")) {

            cookie.setSecure(true);

        } else if (paramName.equals("comment")) {

            cookie.setComment(paramValue);

        } else if (paramName.equals("expires")) {

            if (paramValue == null) {
                throw new MalformedCookieException(
                    "Missing value for expires attribute");
            }

            try {
                cookie.setExpiryDate(DateUtil.parseDate(paramValue, this.datepatterns));
            } catch (DateParseException dpe) {
                LOG.debug("Error parsing cookie date", dpe);
                throw new MalformedCookieException(
                    "Unable to parse expiration date parameter: " 
                    + paramValue);
            }
        } else {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Unrecognized cookie attribute: " 
                    + attribute.toString());
            }
        }
    }

    
    @Override
    public Collection<String> getValidDateFormats() {
        return this.datepatterns;
    }

    @Override
    public void setValidDateFormats(final Collection<String> datepatterns) {
        this.datepatterns = datepatterns;
    }

    /**
      * Performs most common {@link Cookie} validation
      *
      * @param host the host from which the {@link Cookie} was received
      * @param port the port from which the {@link Cookie} was received
      * @param path the path from which the {@link Cookie} was received
      * @param secure <tt>true</tt> when the {@link Cookie} was received using a
      * secure connection
      * @param cookie The cookie to validate.
      * @throws MalformedCookieException if an exception occurs during
      * validation
      */
    
    public void validate(String host, int port, String path, 
        boolean secure, final Cookie cookie) 
        throws MalformedCookieException {
            
        LOG.trace("enter CookieSpecBase.validate("
            + "String, port, path, boolean, Cookie)");
        if (host == null) {
            throw new IllegalArgumentException(
                "Host of origin may not be null");
        }
        if (host.trim().equals("")) {
            throw new IllegalArgumentException(
                "Host of origin may not be blank");
        }
        if (port < 0) {
            throw new IllegalArgumentException("Invalid port: " + port);
        }
        if (path == null) {
            throw new IllegalArgumentException(
                "Path of origin may not be null.");
        }
        if (path.trim().equals("")) {
            path = PATH_DELIM;
        }
        host = host.toLowerCase();
        // check version
        if (cookie.getVersion() < 0) {
            throw new MalformedCookieException ("Illegal version number " 
                + cookie.getValue());
        }

        // security check... we musn't allow the server to give us an
        // invalid domain scope

        // Validate the cookies domain attribute.  NOTE:  Domains without 
        // any dots are allowed to support hosts on private LANs that don't 
        // have DNS names.  Since they have no dots, to domain-match the 
        // request-host and domain must be identical for the cookie to sent 
        // back to the origin-server.
        if (host.indexOf(".") >= 0) {
            // Not required to have at least two dots.  RFC 2965.
            // A Set-Cookie2 with Domain=ajax.com will be accepted.

            // domain must match host
            if (!host.endsWith(cookie.getDomain())) {
                String s = cookie.getDomain();
                if (s.startsWith(".")) {
                    s = s.substring(1, s.length());
                }
                if (!host.equals(s)) { 
                    throw new MalformedCookieException(
                        "Illegal domain attribute \"" + cookie.getDomain() 
                        + "\". Domain of origin: \"" + host + "\"");
                }
            } 
            // BEGIN IA/HERITRIX ADDITION 
            else {
                // requested domain is suffix of origin host; now make sure
                // it's not a public-suffix
                String requestedDomain = cookie.getDomain(); 
                if(requestedDomain.startsWith(".")) {
                    requestedDomain = requestedDomain.substring(1);
                }
                try {
                    if((InternetDomainName.fromLenient(requestedDomain)).isPublicSuffix()) {
                        throw new MalformedCookieException(
                                "Illegal public-suffix domain attribute \"" + cookie.getDomain() 
                                + "\". Domain of origin: \"" + host + "\"");
                    }  
                } catch (IllegalArgumentException e) {
                    // TODO: consider if this means cookie should be invalid
                }
            }
           // END IA/HERITRIX ADDITION 
        } else {
            if (!host.equals(cookie.getDomain())) {
                throw new MalformedCookieException(
                    "Illegal domain attribute \"" + cookie.getDomain() 
                    + "\". Domain of origin: \"" + host + "\"");
            }
        }

        // another security check... we musn't allow the server to give us a
        // cookie that doesn't match this path

        if (!path.startsWith(cookie.getPath())) {
            throw new MalformedCookieException(
                "Illegal path attribute \"" + cookie.getPath() 
                + "\". Path of origin: \"" + path + "\"");
        }
    }


    /**
     * Return <tt>true</tt> if the cookie should be submitted with a request
     * with given attributes, <tt>false</tt> otherwise.
     * @param host the host to which the request is being submitted
     * @param port the port to which the request is being submitted (ignored)
     * @param path the path to which the request is being submitted
     * @param secure <tt>true</tt> if the request is using a secure connection
     * @param cookie {@link Cookie} to be matched
     * @return true if the cookie matches the criterium
     */

    public boolean match(String host, int port, String path, 
        boolean secure, final Cookie cookie) {
            
        LOG.trace("enter CookieSpecBase.match("
            + "String, int, String, boolean, Cookie");
            
        if (host == null) {
            throw new IllegalArgumentException(
                "Host of origin may not be null");
        }
        if (host.trim().equals("")) {
            throw new IllegalArgumentException(
                "Host of origin may not be blank");
        }
        if (port < 0) {
            throw new IllegalArgumentException("Invalid port: " + port);
        }
        if (path == null) {
            throw new IllegalArgumentException(
                "Path of origin may not be null.");
        }
        if (cookie == null) {
            throw new IllegalArgumentException("Cookie may not be null");
        }
        if (path.trim().equals("")) {
            path = PATH_DELIM;
        }
        host = host.toLowerCase();
        if (cookie.getDomain() == null) {
            LOG.warn("Invalid cookie state: domain not specified");
            return false;
        }
        if (cookie.getPath() == null) {
            LOG.warn("Invalid cookie state: path not specified");
            return false;
        }
        
        return
            // only add the cookie if it hasn't yet expired 
            (cookie.getExpiryDate() == null 
                || cookie.getExpiryDate().after(new Date()))
            // and the domain pattern matches 
            && (domainMatch(host, cookie.getDomain()))
            // and the path is null or matching
            && (pathMatch(path, cookie.getPath()))
            // and if the secure flag is set, only if the request is 
            // actually secure 
            && (cookie.getSecure() ? secure : true);      
    }

    /**
     * Performs domain-match as implemented in common browsers.
     * @param host The target host.
     * @param domain The cookie domain attribute.
     * @return true if the specified host matches the given domain.
     */
    public boolean domainMatch(final String host, String domain) {
        if (host.equals(domain)) {
            return true;
        }
        if (!domain.startsWith(".")) {
            domain = "." + domain;
        }
        return host.endsWith(domain) || host.equals(domain.substring(1));
    }

    /**
     * Performs path-match as implemented in common browsers.
     * @param path The target path.
     * @param topmostPath The cookie path attribute.
     * @return true if the paths match
     */
    public boolean pathMatch(final String path, final String topmostPath) {
        boolean match = path.startsWith (topmostPath);
        // if there is a match and these values are not exactly the same we have
        // to make sure we're not matcing "/foobar" and "/foo"
        if (match && path.length() != topmostPath.length()) {
            if (!topmostPath.endsWith(PATH_DELIM)) {
                match = (path.charAt(topmostPath.length()) == PATH_DELIM_CHAR);
            }
        }
        return match;
    }

    /**
     * Return an array of {@link Cookie}s that should be submitted with a
     * request with given attributes, <tt>false</tt> otherwise.
     * @param host the host to which the request is being submitted
     * @param port the port to which the request is being submitted (currently
     * ignored)
     * @param path the path to which the request is being submitted
     * @param secure <tt>true</tt> if the request is using a secure protocol
     * @param cookies an array of <tt>Cookie</tt>s to be matched
     * @return an array of <tt>Cookie</tt>s matching the criterium
     * 
// BEGIN IA/HERITRIX CHANGES
     * @deprecated use match(String, int, String, boolean, SortedMap)
// END IA/HERITRIX CHANGES
     */

    public Cookie[] match(String host, int port, String path, 
        boolean secure, final Cookie cookies[]) {
            
        LOG.trace("enter CookieSpecBase.match("
            + "String, int, String, boolean, Cookie[])");

        if (cookies == null) {
            return null;
        }
        List<Cookie> matching = new LinkedList<Cookie>();
        for (int i = 0; i < cookies.length; i++) {
            if (match(host, port, path, secure, cookies[i])) {
                addInPathOrder(matching, cookies[i]);
            }
        }
        return (Cookie[]) matching.toArray(new Cookie[matching.size()]);
    }

//  BEGIN IA/HERITRIX CHANGES
    /**
     * Return an array of {@link Cookie}s that should be submitted with a
     * request with given attributes, <tt>false</tt> otherwise. 
     * 
     * If the SortedMap comes from an HttpState and is not itself
     * thread-safe, it may be necessary to synchronize on the HttpState
     * instance to protect against concurrent modification. 
     *
     * @param host the host to which the request is being submitted
     * @param port the port to which the request is being submitted (currently
     * ignored)
     * @param path the path to which the request is being submitted
     * @param secure <tt>true</tt> if the request is using a secure protocol
     * @param cookies SortedMap of <tt>Cookie</tt>s to be matched
     * @return an array of <tt>Cookie</tt>s matching the criterium
     */
    @Override
    public Cookie[] match(String host, int port, String path, 
        boolean secure, final SortedMap<String, Cookie> cookies) {
            
        LOG.trace("enter CookieSpecBase.match("
           + "String, int, String, boolean, SortedMap)");

        if (cookies == null) {
            return null;
        }
        List<Cookie> matching = new LinkedList<Cookie>();
        InternetDomainName domain; 
        try {
            domain = InternetDomainName.fromLenient(host); 
        } catch(IllegalArgumentException e) {
            domain = null; 
        }
        
        String candidate = (domain!=null) ? domain.name() : host;
        while(candidate!=null) {
            Iterator<Cookie> iter = cookies.subMap(candidate,
                    candidate + Cookie.DOMAIN_OVERBOUNDS).values().iterator();
            while (iter.hasNext()) {
                Cookie cookie = (Cookie) (iter.next());
                if (match(host, port, path, secure, cookie)) {
                    addInPathOrder(matching, cookie);
                }
            }
            StoredIterator.close(iter);
            if(domain!=null && domain.isUnderPublicSuffix()) {
                domain = domain.parent(); 
                candidate = domain.name(); 
            } else {
                candidate = null;
            }
        }

        return (Cookie[]) matching.toArray(new Cookie[matching.size()]); 
    }
//  END IA/HERITRIX CHANGES
    
    /**
     * Adds the given cookie into the given list in descending path order. That
     * is, more specific path to least specific paths.  This may not be the
     * fastest algorythm, but it'll work OK for the small number of cookies
     * we're generally dealing with.
     *
     * @param list - the list to add the cookie to
     * @param addCookie - the Cookie to add to list
     */
    private static void addInPathOrder(List<Cookie> list, Cookie addCookie) {
        int i = 0;

        for (i = 0; i < list.size(); i++) {
            Cookie c = (Cookie) list.get(i);
            if (addCookie.compare(addCookie, c) > 0) {
                break;
            }
        }
        list.add(i, addCookie);
    }

    /**
     * Return a string suitable for sending in a <tt>"Cookie"</tt> header
     * @param cookie a {@link Cookie} to be formatted as string
     * @return a string suitable for sending in a <tt>"Cookie"</tt> header.
     */
    public String formatCookie(Cookie cookie) {
        LOG.trace("enter CookieSpecBase.formatCookie(Cookie)");
        if (cookie == null) {
            throw new IllegalArgumentException("Cookie may not be null");
        }
        StringBuffer buf = new StringBuffer();
        buf.append(cookie.getName());
        buf.append("=");
        String s = cookie.getValue();
        if (s != null) {
            buf.append(s);
        }
        return buf.toString();
    }

    /**
     * Create a <tt>"Cookie"</tt> header value containing all {@link Cookie}s in
     * <i>cookies</i> suitable for sending in a <tt>"Cookie"</tt> header
     * @param cookies an array of {@link Cookie}s to be formatted
     * @return a string suitable for sending in a Cookie header.
     * @throws IllegalArgumentException if an input parameter is illegal
     */

    public String formatCookies(Cookie[] cookies)
      throws IllegalArgumentException {
        LOG.trace("enter CookieSpecBase.formatCookies(Cookie[])");
        if (cookies == null) {
            throw new IllegalArgumentException("Cookie array may not be null");
        }
        if (cookies.length == 0) {
            throw new IllegalArgumentException("Cookie array may not be empty");
        }

        StringBuffer buffer = new StringBuffer();
        for (int i = 0; i < cookies.length; i++) {
            if (i > 0) {
                buffer.append("; ");
            }
            buffer.append(formatCookie(cookies[i]));
        }
        return buffer.toString();
    }


    /**
     * Create a <tt>"Cookie"</tt> {@link Header} containing all {@link Cookie}s
     * in <i>cookies</i>.
     * @param cookies an array of {@link Cookie}s to be formatted as a <tt>"
     * Cookie"</tt> header
     * @return a <tt>"Cookie"</tt> {@link Header}.
     */
    public Header formatCookieHeader(Cookie[] cookies) {
        LOG.trace("enter CookieSpecBase.formatCookieHeader(Cookie[])");
        return new Header("Cookie", formatCookies(cookies));
    }


    /**
     * Create a <tt>"Cookie"</tt> {@link Header} containing the {@link Cookie}.
     * @param cookie <tt>Cookie</tt>s to be formatted as a <tt>Cookie</tt>
     * header
     * @return a Cookie header.
     */
    public Header formatCookieHeader(Cookie cookie) {
        LOG.trace("enter CookieSpecBase.formatCookieHeader(Cookie)");
        return new Header("Cookie", formatCookie(cookie));
    }

}
