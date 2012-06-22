/*
 * $Header: /home/jerenkrantz/tmp/commons/commons-convert/cvs/home/cvs/jakarta-commons//httpclient/src/java/org/apache/commons/httpclient/HttpState.java,v 1.38 2004/12/20 11:50:54 olegk Exp $
 * $Revision$
 * $Date$
 *
 * ====================================================================
 *
 *  Copyright 1999-2004 The Apache Software Foundation
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

package org.apache.commons.httpclient;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.SortedMap;
import java.util.concurrent.ConcurrentSkipListMap;

import org.apache.commons.httpclient.auth.AuthScope;
import org.apache.commons.httpclient.cookie.CookiePolicy;
import org.apache.commons.httpclient.cookie.CookieSpec;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.sleepycat.collections.StoredIterator;

/**
 * <p>
 * A container for HTTP attributes that may persist from request
 * to request, such as {@link Cookie cookies} and authentication
 * {@link Credentials credentials}.
 * </p>
 * 
 * @author <a href="mailto:remm@apache.org">Remy Maucherat</a>
 * @author Rodney Waldhoff
 * @author <a href="mailto:jsdever@apache.org">Jeff Dever</a>
 * @author Sean C. Sullivan
 * @author <a href="mailto:becke@u.washington.edu">Michael Becke</a>
 * @author <a href="mailto:oleg@ural.ru">Oleg Kalnichevski</a>
 * @author <a href="mailto:mbowler@GargoyleSoftware.com">Mike Bowler</a>
 * @author <a href="mailto:adrian@intencha.com">Adrian Sutton</a>
 * 
 * @version $Revision$ $Date$
 * 
 */
public class HttpState {

    // ----------------------------------------------------- Instance Variables

    /**
     * Map of {@link Credentials credentials} by realm that this 
     * HTTP state contains.
     */
    private HashMap<AuthScope, Credentials> credMap = new HashMap<AuthScope, Credentials>();

    /**
     * Map of {@link Credentials proxy credentials} by realm that this
     * HTTP state contains
     */
    private HashMap<AuthScope, Credentials> proxyCred = new HashMap<AuthScope, Credentials>();

// BEGIN IA/HERITRIX CHANGES
//    /**
//     * Array of {@link Cookie cookies} that this HTTP state contains.
//     */
//    private ArrayList cookiesArrayList = new ArrayList();
    /**
     * SortedMap of {@link Cookie cookies} that this HTTP state contains.
     */
    private SortedMap<String, Cookie> cookiesMap = new ConcurrentSkipListMap<String, Cookie>();
// END IA/HERITRIX CHANGES

    private boolean preemptive = false;

    private int cookiePolicy = -1;
        // -------------------------------------------------------- Class Variables

    /**
     * The boolean system property name to turn on preemptive authentication.
     * @deprecated This field and feature will be removed following HttpClient 3.0.
     */
    public static final String PREEMPTIVE_PROPERTY = "httpclient.authentication.preemptive";

    /**
     * The default value for {@link #PREEMPTIVE_PROPERTY}.
     * @deprecated This field and feature will be removed following HttpClient 3.0.
     */
    public static final String PREEMPTIVE_DEFAULT = "false";
    
    /** Log object for this class. */
    private static final Log LOG = LogFactory.getLog(HttpState.class);

    /**
     * Default constructor.
     */
    public HttpState() {
        super();
    }

    // ------------------------------------------------------------- Properties

    /**
     * Adds an {@link Cookie HTTP cookie}, replacing any existing equivalent cookies.
     * If the given cookie has already expired it will not be added, but existing 
     * values will still be removed.
     * 
     * @param cookie the {@link Cookie cookie} to be added
     * 
     * @see #addCookies(Cookie[])
     * 
     */
 // BEGIN IA/HERITRIX CHANGES
    public /*synchronized*/ void addCookie(Cookie cookie) {
        LOG.trace("enter HttpState.addCookie(Cookie)");

// PRIOR IMPL & COMPARISON HARNESS LEFT COMMENTED OUT FOR TEMPORARY REFERENCE
//        Cookie removed1 = null;
//        Cookie removed2 = null;
        if (cookie != null) {
            // first remove any old cookie that is equivalent
//            for (Iterator it = cookiesArrayList.iterator(); it.hasNext();) {
//                Cookie tmp = (Cookie) it.next();
//                if (cookie.equals(tmp)) {
//                    it.remove();
//                    removed1 = tmp;
//                    break;
//                }
//            }
            if (!cookie.isExpired()) {
//                cookiesArrayList.add(cookie);
                cookiesMap.put(cookie.getSortKey(),cookie);
            } else {
                cookiesMap.remove(cookie.getSortKey());
            }
        }
//        if(removed1!=null && !removed1.equals(removed2)) {
//            System.out.println("addCookie discrepancy");
//        }
// END IA/HERITRIX CHANGES
    }

    /**
     * Adds an array of {@link Cookie HTTP cookies}. Cookies are added individually and 
     * in the given array order. If any of the given cookies has already expired it will 
     * not be added, but existing values will still be removed.
     * 
     * @param cookies the {@link Cookie cookies} to be added
     * 
     * @see #addCookie(Cookie)
     * 
     * 
     */
    public synchronized void addCookies(Cookie[] cookies) {
        LOG.trace("enter HttpState.addCookies(Cookie[])");

        if (cookies != null) {
            for (int i = 0; i < cookies.length; i++) {
                this.addCookie(cookies[i]);
            }
        }
    }

    /**
     * Returns an array of {@link Cookie cookies} that this HTTP
     * state currently contains.
     * 
     * @return an array of {@link Cookie cookies}.
     * 
     * @see #getCookies(String, int, String, boolean)
     * 
     * @deprecated use getCookiesMap() // <- IA/HERITRIX CHANGE
     */
    public synchronized Cookie[] getCookies() {
        LOG.trace("enter HttpState.getCookies()");
// BEGIN IA/HERITRIX CHANGES
//      PRIOR IMPL & COMPARISON HARNESS LEFT COMMENTED OUT FOR TEMPORARY REFERENCE
//        Cookie[] arrayListAnswer = (Cookie[]) (cookiesArrayList.toArray(new Cookie[cookiesArrayList.size()]));
        ArrayList<Cookie> arrayableCookies = new ArrayList<Cookie>();
        Iterator<Cookie> iter = cookiesMap.values().iterator();
        while(iter.hasNext()) {
            arrayableCookies.add(iter.next());
    }
        StoredIterator.close(iter);
        Cookie[] mapAnswer = 
            (Cookie[]) arrayableCookies.toArray(new Cookie[arrayableCookies.size()]);

//        if(cookiesArrayList.size()!=arrayableCookies.size()) {
//            System.out.println("discrepancy");
//        }
        return mapAnswer;
// END IA/HERITRIX CHANGES    
    }

// START IA/HERITRIX ADDITIONS
    /**
     * Returns a sorted map of {@link Cookie cookies} that this HTTP
     * state currently contains.
     * 
     * Any operations on this map should be synchronized with respect 
     * to this HttpState instance.
     * 
     * @return sorter map of {@link Cookie cookies}
     */
    public SortedMap<String, Cookie> getCookiesMap() {
        return cookiesMap;
    }
    
    /**
     * Replace the standard sorted map with an external implemenations 
     * (such as one backed by persistent store, like BDB's StoredSortedMap.)
     * 
     * @param map alternate sorted map to use to store cookies
     */
    public void setCookiesMap(SortedMap<String, Cookie> map) {
        this.cookiesMap = map;
    }
// END IA/HERITRIX ADDITIONS

    /**
     * Returns an array of {@link Cookie cookies} in this HTTP 
     * state that match the given request parameters.
     * 
     * @param domain the request domain
     * @param port the request port
     * @param path the request path
     * @param secure <code>true</code> when using HTTPS
     * 
     * @return an array of {@link Cookie cookies}.
     * 
     * @see #getCookies()
     * 
     * @deprecated use CookieSpec#match(String, int, String, boolean, Cookie)
     */
    public synchronized Cookie[] getCookies(
        String domain, 
        int port, 
        String path, 
        boolean secure
    ) {
        LOG.trace("enter HttpState.getCookies(String, int, String, boolean)");

        CookieSpec matcher = CookiePolicy.getDefaultSpec();
// BEGIN IA/HERITRIX CHANGES
//      PRIOR IMPL & COMPARISON HARNESS LEFT COMMENTED OUT FOR TEMPORARY REFERENCE
//        ArrayList list = new ArrayList(cookiesArrayList.size());
//        for (int i = 0, m = cookiesArrayList.size(); i < m; i++) {
//            Cookie cookie = (Cookie) (cookiesArrayList.get(i));
//            if (matcher.match(domain, port, path, secure, cookie)) {
//                list.add(cookie);
//            }
//        }
//        Cookie[] arrayListAnswer = (Cookie[]) (list.toArray(new Cookie[list.size()]));
        Cookie[] mapAnswer = matcher.match(domain,port,path,secure,cookiesMap);

//        if(! (new HashSet(list).equals(new HashSet(Arrays.asList(mapAnswer))))) {
//            System.out.println("discrepancy");
//        }
        return mapAnswer;
// END IA/HERITRIX CHANGES
    }

    /**
     * Removes all of {@link Cookie cookies} in this HTTP state
     * that have expired according to the current system time.
     * 
     * @see #purgeExpiredCookies(java.util.Date)
     * 
     */
    public synchronized boolean purgeExpiredCookies() {
        LOG.trace("enter HttpState.purgeExpiredCookies()");
        return purgeExpiredCookies(new Date());
    }

    /**
     * Removes all of {@link Cookie cookies} in this HTTP state
     * that have expired by the specified {@link java.util.Date date}. 
     * 
     * @param date The {@link java.util.Date date} to compare against.
     * 
     * @return true if any cookies were purged.
     * 
     * @see Cookie#isExpired(java.util.Date)
     * 
     * @see #purgeExpiredCookies()
     */
    public synchronized boolean purgeExpiredCookies(Date date) {
        LOG.trace("enter HttpState.purgeExpiredCookies(Date)");
// BEGIN IA/HERITRIX CHANGES
//      PRIOR IMPL & COMPARISON HARNESS LEFT COMMENTED OUT FOR TEMPORARY REFERENCE
//        boolean arrayRemoved = false;
//        Iterator ita = cookiesArrayList.iterator();
//        while (ita.hasNext()) {
//            if (((Cookie) (ita.next())).isExpired(date)) {
//                ita.remove();
//                arrayRemoved = true;
//            }
//        }
        boolean removed = false;
        Iterator<Cookie> it = cookiesMap.values().iterator();
        while (it.hasNext()) {
            if (it.next().isExpired(date)) {
                it.remove();
                removed = true;
            }
        }
        StoredIterator.close(it);
//        assert removed == arrayRemoved : "discrepancy"     
// END IA/HERITRIX CHANGES
        return removed;
    }


    /**
     * Returns the current {@link CookiePolicy cookie policy} for this
     * HTTP state.
     * 
     * @return The {@link CookiePolicy cookie policy}.
     * 
     * @deprecated Use 
     *  {@link org.apache.commons.httpclient.params.HttpMethodParams#getCookiePolicy()},
     *  {@link HttpMethod#getParams()}.     
     */
    
    public int getCookiePolicy() {
        return this.cookiePolicy;
    }
    

    /**
     * Defines whether preemptive authentication should be 
     * attempted.
     * 
     * @param value <tt>true</tt> if preemptive authentication should be 
     * attempted, <tt>false</tt> otherwise. 
     * 
     * @deprecated Use 
     * {@link org.apache.commons.httpclient.params.HttpClientParams#setAuthenticationPreemptive(boolean)}, 
     * {@link HttpClient#getParams()}.
     */
    
    public void setAuthenticationPreemptive(boolean value) {
        this.preemptive = value;
    }


    /**
     * Returns <tt>true</tt> if preemptive authentication should be 
     * attempted, <tt>false</tt> otherwise.
     * 
     * @return boolean flag.
     * 
     * @deprecated Use 
     * {@link org.apache.commons.httpclient.params.HttpClientParams#isAuthenticationPreemptive()}, 
     * {@link HttpClient#getParams()}.
     */
    
    public boolean isAuthenticationPreemptive() {
        return this.preemptive;
    }
    

    /**
     * Sets the current {@link CookiePolicy cookie policy} for this HTTP
     * state to one of the following supported policies: 
     * {@link CookiePolicy#COMPATIBILITY}, 
     * {@link CookiePolicy#NETSCAPE_DRAFT} or
     * {@link CookiePolicy#RFC2109}.
     * 
     * @param policy new {@link CookiePolicy cookie policy}
     * 
     * @deprecated 
     *  Use {@link org.apache.commons.httpclient.params.HttpMethodParams#setCookiePolicy(String)},
     *  {@link HttpMethod#getParams()}.     
     */
    
    public void setCookiePolicy(int policy) {
        this.cookiePolicy = policy;
    }

    /** 
     * Sets the {@link Credentials credentials} for the given authentication 
     * realm on the given host. The <code>null</code> realm signifies default 
     * credentials for the given host, which should be used when no 
     * {@link Credentials credentials} have been explictly supplied for the 
     * challenging realm. The <code>null</code> host signifies default 
     * credentials, which should be used when no {@link Credentials credentials} 
     * have been explictly supplied for the challenging host. Any previous 
     * credentials for the given realm on the given host will be overwritten.
     * 
     * @param realm the authentication realm
     * @param host the host the realm belongs to
     * @param credentials the authentication {@link Credentials credentials} 
     * for the given realm.
     * 
     * @see #getCredentials(String, String)
     * @see #setProxyCredentials(String, String, Credentials) 
     * 
     * @deprecated use #setCredentials(AuthScope, Credentials)
     */
    
    public synchronized void setCredentials(String realm, String host, Credentials credentials) {
        LOG.trace("enter HttpState.setCredentials(String, String, Credentials)");
        credMap.put(new AuthScope(host, AuthScope.ANY_PORT, realm, AuthScope.ANY_SCHEME), credentials);
    }

    /** 
     * Sets the {@link Credentials credentials} for the given authentication 
     * scope. Any previous credentials for the given scope will be overwritten.
     * 
     * @param authscope the {@link AuthScope authentication scope}
     * @param credentials the authentication {@link Credentials credentials} 
     * for the given scope.
     * 
     * @see #getCredentials(AuthScope)
     * @see #setProxyCredentials(AuthScope, Credentials) 
     * 
     * @since 3.0
     */
    public synchronized void setCredentials(final AuthScope authscope, final Credentials credentials) {
        if (authscope == null) {
            throw new IllegalArgumentException("Authentication scope may not be null");
        }
        LOG.trace("enter HttpState.setCredentials(AuthScope, Credentials)");
        credMap.put(authscope, credentials);
    }

    /**
     * Find matching {@link Credentials credentials} for the given authentication scope.
     *
     * @param map the credentials hash map
     * @param token the {@link AuthScope authentication scope}
     * @return the credentials 
     * 
     */
    private static Credentials matchCredentials(final HashMap<AuthScope, Credentials> map, final AuthScope authscope) {
        // see if we get a direct hit
        Credentials creds = map.get(authscope);
        if (creds == null) {
            // Nope.
            // Do a full scan
            int bestMatchFactor  = -1;
            AuthScope bestMatch  = null;
            Iterator<AuthScope> items = map.keySet().iterator();
            while (items.hasNext()) {
                AuthScope current = (AuthScope)items.next();
                int factor = authscope.match(current);
                if (factor > bestMatchFactor) {
                    bestMatchFactor = factor;
                    bestMatch = current;
                }
            }
            if (bestMatch != null) {
                creds = (Credentials)map.get(bestMatch);
            }
        }
        return creds;
    }
    
    /**
     * Get the {@link Credentials credentials} for the given authentication scope on the 
     * given host.
     *
     * If the <i>realm</i> exists on <i>host</i>, return the coresponding credentials.
     * If the <i>host</i> exists with a <tt>null</tt> <i>realm</i>, return the corresponding
     * credentials.
     * If the <i>realm</i> exists with a <tt>null</tt> <i>host</i>, return the
     * corresponding credentials.  If the <i>realm</i> does not exist, return
     * the default Credentials.  If there are no default credentials, return
     * <code>null</code>.
     *
     * @param realm the authentication realm
     * @param host the host the realm is on
     * @return the credentials 
     * 
     * @see #setCredentials(String, String, Credentials)
     * 
     * @deprecated use #getCredentials(AuthScope)
     */
    
    public synchronized Credentials getCredentials(String realm, String host) {
        LOG.trace("enter HttpState.getCredentials(String, String");
        return matchCredentials(this.credMap, 
            new AuthScope(host, AuthScope.ANY_PORT, realm, AuthScope.ANY_SCHEME));
    }

    /**
     * Get the {@link Credentials credentials} for the given authentication scope.
     *
     * @param authscope the {@link AuthScope authentication scope}
     * @return the credentials 
     * 
     * @see #setCredentials(AuthScope, Credentials)
     * 
     * @since 3.0
     */
    public synchronized Credentials getCredentials(final AuthScope authscope) {
        if (authscope == null) {
            throw new IllegalArgumentException("Authentication scope may not be null");
        }
        LOG.trace("enter HttpState.getCredentials(AuthScope)");
        return matchCredentials(this.credMap, authscope);
    }

    /**
     * Sets the {@link Credentials credentials} for the given proxy authentication 
     * realm on the given proxy host. The <code>null</code> proxy realm signifies 
     * default credentials for the given proxy host, which should be used when no 
     * {@link Credentials credentials} have been explictly supplied for the 
     * challenging proxy realm. The <code>null</code> proxy host signifies default 
     * credentials, which should be used when no {@link Credentials credentials} 
     * have been explictly supplied for the challenging proxy host. Any previous 
     * credentials for the given proxy realm on the given proxy host will be 
     * overwritten.
     *
     * @param realm the authentication realm
     * @param proxyHost the proxy host
     * @param credentials the authentication credentials for the given realm
     * 
     * @see #getProxyCredentials(AuthScope)
     * @see #setCredentials(AuthScope, Credentials)
     * 
     * @deprecated use #setProxyCredentials(AuthScope, Credentials)
     */
    public synchronized void setProxyCredentials(
        String realm, 
        String proxyHost, 
        Credentials credentials
    ) {
        LOG.trace("enter HttpState.setProxyCredentials(String, String, Credentials");
        proxyCred.put(new AuthScope(proxyHost, AuthScope.ANY_PORT, realm, AuthScope.ANY_SCHEME), credentials);
    }

    /** 
     * Sets the {@link Credentials proxy credentials} for the given authentication 
     * realm. Any previous credentials for the given realm will be overwritten.
     * 
     * @param authscope the {@link AuthScope authentication scope}
     * @param credentials the authentication {@link Credentials credentials} 
     * for the given realm.
     * 
     * @see #getProxyCredentials(AuthScope)
     * @see #setCredentials(AuthScope, Credentials) 
     * 
     * @since 3.0
     */
    public synchronized void setProxyCredentials(final AuthScope authscope, 
        final Credentials credentials)
    {
        if (authscope == null) {
            throw new IllegalArgumentException("Authentication scope may not be null");
        }
        LOG.trace("enter HttpState.setProxyCredentials(AuthScope, Credentials)");
        proxyCred.put(authscope, credentials);
    }

    /**
     * Get the {@link Credentials credentials} for the proxy host with the given 
     * authentication scope.
     *
     * If the <i>realm</i> exists on <i>host</i>, return the coresponding credentials.
     * If the <i>host</i> exists with a <tt>null</tt> <i>realm</i>, return the corresponding
     * credentials.
     * If the <i>realm</i> exists with a <tt>null</tt> <i>host</i>, return the
     * corresponding credentials.  If the <i>realm</i> does not exist, return
     * the default Credentials.  If there are no default credentials, return
     * <code>null</code>.
     * 
     * @param realm the authentication realm
     * @param proxyHost the proxy host the realm is on
     * @return the credentials 
     * @see #setProxyCredentials(String, String, Credentials)
     * 
     * @deprecated use #getProxyCredentials(AuthScope)
     */
    public synchronized Credentials getProxyCredentials(String realm, String proxyHost) {
       LOG.trace("enter HttpState.getCredentials(String, String");
        return matchCredentials(this.proxyCred, 
            new AuthScope(proxyHost, AuthScope.ANY_PORT, realm, AuthScope.ANY_SCHEME));
    }
    
    /**
     * Get the {@link Credentials proxy credentials} for the given authentication scope.
     *
     * @param authscope the {@link AuthScope authentication scope}
     * @return the credentials 
     * 
     * @see #setProxyCredentials(AuthScope, Credentials)
     * 
     * @since 3.0
     */
    public synchronized Credentials getProxyCredentials(final AuthScope authscope) {
        if (authscope == null) {
            throw new IllegalArgumentException("Authentication scope may not be null");
        }
        LOG.trace("enter HttpState.getProxyCredentials(AuthScope)");
        return matchCredentials(this.proxyCred, authscope);
    }

    /**
     * Returns a string representation of this HTTP state.
     * 
     * @return The string representation of the HTTP state.
     * 
     * @see java.lang.Object#toString()
     */
    public synchronized String toString() {
        StringBuffer sbResult = new StringBuffer();

        sbResult.append("[");
        sbResult.append(getCredentialsStringRepresentation(proxyCred));
        sbResult.append(" | ");
        sbResult.append(getCredentialsStringRepresentation(credMap));
        sbResult.append(" | ");
        sbResult.append(getCookiesStringRepresentation(cookiesMap.values())); // <- IA/HERITRIX CHANGE
        sbResult.append("]");

        String strResult = sbResult.toString();

        return strResult;
    }
    
    /**
     * Returns a string representation of the credentials.
     * @param credMap The credentials.
     * @return The string representation.
     */
    private static String getCredentialsStringRepresentation(final Map<AuthScope, Credentials> credMap) {
        StringBuffer sbResult = new StringBuffer();
        Iterator<AuthScope> iter = credMap.keySet().iterator();
        while (iter.hasNext()) {
            Object key = iter.next();
            Credentials cred = credMap.get(key);
            if (sbResult.length() > 0) {
                sbResult.append(", ");
            }
            sbResult.append(key);
            sbResult.append("#");
            sbResult.append(cred.toString());
        }
        return sbResult.toString();
    }
    
    /**
     * Returns a string representation of the cookies.
     * @param cookies The cookies
     * @return The string representation.
     */
    private static String getCookiesStringRepresentation(final Collection<Cookie> cookies) { // <- IA/HERITRIX CHANGE
        StringBuffer sbResult = new StringBuffer();
        Iterator<Cookie> iter = cookies.iterator();
        while (iter.hasNext()) {
            Cookie ck = iter.next();
            if (sbResult.length() > 0) {
                sbResult.append("#");
            }
            sbResult.append(ck.toExternalForm());
        }
        return sbResult.toString();
    }
    
    /**
     * Clears all credentials.
     */
    public void clearCredentials() {
        this.credMap.clear();
    }
    
    /**
     * Clears all proxy credentials.
     */
    public void clearProxyCredentials() {
        this.proxyCred.clear();
    }
    
    /**
     * Clears all cookies.
     */
    public void clearCookies() {
// BEGIN IA/HERITRIX CHANGES
//        this.cookiesArrayList.clear();
        this.cookiesMap.clear();
// END IA/HERITRIX CHANGES
    }
    
    /**
     * Clears the state information (all cookies, credentials and proxy credentials).
     */
    public void clear() {
        clearCookies();
        clearCredentials();
        clearProxyCredentials();
    }
}
