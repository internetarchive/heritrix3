/*
 * $Header: /home/jerenkrantz/tmp/commons/commons-convert/cvs/home/cvs/jakarta-commons//httpclient/src/java/org/apache/commons/httpclient/Cookie.java,v 1.44 2004/06/05 16:49:20 olegk Exp $
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

import java.io.Serializable;
import java.text.RuleBasedCollator;
import java.util.Comparator;
import java.util.Date;
import java.util.Locale;

import org.apache.commons.httpclient.cookie.CookiePolicy;
import org.apache.commons.httpclient.cookie.CookieSpec;
import org.apache.commons.httpclient.util.LangUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * <p>
 * HTTP "magic-cookie" represents a piece of state information
 * that the HTTP agent and the target server can exchange to maintain 
 * a session.
 * </p>
 * 
 * @author B.C. Holmes
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
 * @version $Revision$ $Date$
 */
@SuppressWarnings({"serial","unchecked"}) // <- HERITRIX CHANGE
public class Cookie extends NameValuePair implements Serializable, Comparator {

    // ----------------------------------------------------------- Constructors

    /**
     * Default constructor. Creates a blank cookie 
     */

    public Cookie() {
        this(null, "noname", null, null, null, false);
    }

    /**
     * Creates a cookie with the given name, value and domain attribute.
     *
     * @param name    the cookie name
     * @param value   the cookie value
     * @param domain  the domain this cookie can be sent to
     */
    public Cookie(String domain, String name, String value) {
        this(domain, name, value, null, null, false);
    }

    /**
     * Creates a cookie with the given name, value, domain attribute,
     * path attribute, expiration attribute, and secure attribute 
     *
     * @param name    the cookie name
     * @param value   the cookie value
     * @param domain  the domain this cookie can be sent to
     * @param path    the path prefix for which this cookie can be sent
     * @param expires the {@link Date} at which this cookie expires,
     *                or <tt>null</tt> if the cookie expires at the end
     *                of the session
     * @param secure if true this cookie can only be sent over secure
     * connections
     * @throws IllegalArgumentException If cookie name is null or blank,
     *   cookie name contains a blank, or cookie name starts with character $
     *   
     */
    public Cookie(String domain, String name, String value, 
        String path, Date expires, boolean secure) {
            
        super(name, value);
        LOG.trace("enter Cookie(String, String, String, String, Date, boolean)");
        if (name == null) {
            throw new IllegalArgumentException("Cookie name may not be null");
        }
        if (name.trim().equals("")) {
            throw new IllegalArgumentException("Cookie name may not be blank");
        }
        this.setPath(path);
        this.setDomain(domain);
        this.setExpiryDate(expires);
        this.setSecure(secure);
    }

    /**
     * Creates a cookie with the given name, value, domain attribute,
     * path attribute, maximum age attribute, and secure attribute 
     *
     * @param name   the cookie name
     * @param value  the cookie value
     * @param domain the domain this cookie can be sent to
     * @param path   the path prefix for which this cookie can be sent
     * @param maxAge the number of seconds for which this cookie is valid.
     *               maxAge is expected to be a non-negative number. 
     *               <tt>-1</tt> signifies that the cookie should never expire.
     * @param secure if <tt>true</tt> this cookie can only be sent over secure
     * connections
     */
    public Cookie(String domain, String name, String value, String path, 
        int maxAge, boolean secure) {
            
        this(domain, name, value, path, null, secure);
        if (maxAge < -1) {
            throw new IllegalArgumentException("Invalid max age:  " + Integer.toString(maxAge));
        }            
        if (maxAge >= 0) {
            setExpiryDate(new Date(System.currentTimeMillis() + maxAge * 1000L));
        }
    }

    /**
     * Returns the comment describing the purpose of this cookie, or
     * <tt>null</tt> if no such comment has been defined.
     * 
     * @return comment 
     *
     * @see #setComment(String)
     */
    public String getComment() {
        return cookieComment;
    }

    /**
     * If a user agent (web browser) presents this cookie to a user, the
     * cookie's purpose will be described using this comment.
     * 
     * @param comment
     *  
     * @see #getComment()
     */
    public void setComment(String comment) {
        cookieComment = comment;
    }

    /**
     * Returns the expiration {@link Date} of the cookie, or <tt>null</tt>
     * if none exists.
     * <p><strong>Note:</strong> the object returned by this method is 
     * considered immutable. Changing it (e.g. using setTime()) could result
     * in undefined behaviour. Do so at your peril. </p>
     * @return Expiration {@link Date}, or <tt>null</tt>.
     *
     * @see #setExpiryDate(java.util.Date)
     *
     */
    public Date getExpiryDate() {
        return cookieExpiryDate;
    }

    /**
     * Sets expiration date.
     * <p><strong>Note:</strong> the object returned by this method is considered
     * immutable. Changing it (e.g. using setTime()) could result in undefined 
     * behaviour. Do so at your peril.</p>
     *
     * @param expiryDate the {@link Date} after which this cookie is no longer valid.
     *
     * @see #getExpiryDate
     *
     */
    public void setExpiryDate (Date expiryDate) {
        cookieExpiryDate = expiryDate;
    }


    /**
     * Returns <tt>false</tt> if the cookie should be discarded at the end
     * of the "session"; <tt>true</tt> otherwise.
     *
     * @return <tt>false</tt> if the cookie should be discarded at the end
     *         of the "session"; <tt>true</tt> otherwise
     */
    public boolean isPersistent() {
        return (null != cookieExpiryDate);
    }


    /**
     * Returns domain attribute of the cookie.
     * 
     * @return the value of the domain attribute
     *
     * @see #setDomain(java.lang.String)
     */
    public String getDomain() {
        return cookieDomain;
    }

    /**
     * Sets the domain attribute.
     * 
     * @param domain The value of the domain attribute
     *
     * @see #getDomain
     */
    public void setDomain(String domain) {
        if (domain != null) {
            int ndx = domain.indexOf(":");
            if (ndx != -1) {
              domain = domain.substring(0, ndx);
            }
            cookieDomain = domain.toLowerCase();
        }
    }


    /**
     * Returns the path attribute of the cookie
     * 
     * @return The value of the path attribute.
     * 
     * @see #setPath(java.lang.String)
     */
    public String getPath() {
        return cookiePath;
    }

    /**
     * Sets the path attribute.
     *
     * @param path The value of the path attribute
     *
     * @see #getPath
     *
     */
    public void setPath(String path) {
        cookiePath = path;
    }

    /**
     * @return <code>true</code> if this cookie should only be sent over secure connections.
     * @see #setSecure(boolean)
     */
    public boolean getSecure() {
        return isSecure;
    }

    /**
     * Sets the secure attribute of the cookie.
     * <p>
     * When <tt>true</tt> the cookie should only be sent
     * using a secure protocol (https).  This should only be set when
     * the cookie's originating server used a secure protocol to set the
     * cookie's value.
     *
     * @param secure The value of the secure attribute
     * 
     * @see #getSecure()
     */
    public void setSecure (boolean secure) {
        isSecure = secure;
    }

    /**
     * Returns the version of the cookie specification to which this
     * cookie conforms.
     *
     * @return the version of the cookie.
     * 
     * @see #setVersion(int)
     *
     */
    public int getVersion() {
        return cookieVersion;
    }

    /**
     * Sets the version of the cookie specification to which this
     * cookie conforms. 
     *
     * @param version the version of the cookie.
     * 
     * @see #getVersion
     */
    public void setVersion(int version) {
        cookieVersion = version;
    }

    /**
     * Returns true if this cookie has expired.
     * 
     * @return <tt>true</tt> if the cookie has expired.
     */
    public boolean isExpired() {
        return (cookieExpiryDate != null  
            && cookieExpiryDate.getTime() <= System.currentTimeMillis());
    }

    /**
     * Returns true if this cookie has expired according to the time passed in.
     * 
     * @param now The current time.
     * 
     * @return <tt>true</tt> if the cookie expired.
     */
    public boolean isExpired(Date now) {
        return (cookieExpiryDate != null  
            && cookieExpiryDate.getTime() <= now.getTime());
    }


    /**
     * Indicates whether the cookie had a path specified in a 
     * path attribute of the <tt>Set-Cookie</tt> header. This value
     * is important for generating the <tt>Cookie</tt> header because 
     * some cookie specifications require that the <tt>Cookie</tt> header 
     * should only include a path attribute if the cookie's path 
     * was specified in the <tt>Set-Cookie</tt> header.
     *
     * @param value <tt>true</tt> if the cookie's path was explicitly 
     * set, <tt>false</tt> otherwise.
     * 
     * @see #isPathAttributeSpecified
     */
    public void setPathAttributeSpecified(boolean value) {
        hasPathAttribute = value;
    }

    /**
     * Returns <tt>true</tt> if cookie's path was set via a path attribute
     * in the <tt>Set-Cookie</tt> header.
     *
     * @return value <tt>true</tt> if the cookie's path was explicitly 
     * set, <tt>false</tt> otherwise.
     * 
     * @see #setPathAttributeSpecified
     */
    public boolean isPathAttributeSpecified() {
        return hasPathAttribute;
    }

    /**
     * Indicates whether the cookie had a domain specified in a 
     * domain attribute of the <tt>Set-Cookie</tt> header. This value
     * is important for generating the <tt>Cookie</tt> header because 
     * some cookie specifications require that the <tt>Cookie</tt> header 
     * should only include a domain attribute if the cookie's domain 
     * was specified in the <tt>Set-Cookie</tt> header.
     *
     * @param value <tt>true</tt> if the cookie's domain was explicitly 
     * set, <tt>false</tt> otherwise.
     *
     * @see #isDomainAttributeSpecified
     */
    public void setDomainAttributeSpecified(boolean value) {
        hasDomainAttribute = value;
    }

    /**
     * Returns <tt>true</tt> if cookie's domain was set via a domain 
     * attribute in the <tt>Set-Cookie</tt> header.
     *
     * @return value <tt>true</tt> if the cookie's domain was explicitly 
     * set, <tt>false</tt> otherwise.
     *
     * @see #setDomainAttributeSpecified
     */
    public boolean isDomainAttributeSpecified() {
        return hasDomainAttribute;
    }

    /**
     * Returns a hash code in keeping with the
     * {@link Object#hashCode} general hashCode contract.
     * @return A hash code
     */
    public int hashCode() {
        int hash = LangUtils.HASH_SEED;
        hash = LangUtils.hashCode(hash, this.getName());
        hash = LangUtils.hashCode(hash, this.cookieDomain);
        hash = LangUtils.hashCode(hash, this.cookiePath);
        return hash;
    }


    /**
     * Two cookies are equal if the name, path and domain match.
     * @param obj The object to compare against.
     * @return true if the two objects are equal.
     */
    public boolean equals(Object obj) {
        if (obj == null) return false;
        if (this == obj) return true;
        if (obj instanceof Cookie) {
            Cookie that = (Cookie) obj;
            return LangUtils.equals(this.getName(), that.getName())
                  && LangUtils.equals(this.cookieDomain, that.cookieDomain)
                  && LangUtils.equals(this.cookiePath, that.cookiePath);
        } else {
            return false;
        }
    }


    /**
     * Return a textual representation of the cookie.
     * 
     * @return string.
     */
    public String toExternalForm() {
        CookieSpec spec = null;
        if (getVersion() > 0) {
            spec = CookiePolicy.getDefaultSpec(); 
        } else {
            spec = CookiePolicy.getCookieSpec(CookiePolicy.NETSCAPE); 
        }
        return spec.formatCookie(this); 
    }

    /**
     * <p>Compares two cookies to determine order for cookie header.</p>
     * <p>Most specific should be first. </p>
     * <p>This method is implemented so a cookie can be used as a comparator for
     * a SortedSet of cookies. Specifically it's used above in the 
     * createCookieHeader method.</p>
     * @param o1 The first object to be compared
     * @param o2 The second object to be compared
     * @return See {@link java.util.Comparator#compare(Object,Object)}
     */
    public int compare(Object o1, Object o2) {
        LOG.trace("enter Cookie.compare(Object, Object)");

        if (!(o1 instanceof Cookie)) {
            throw new ClassCastException(o1.getClass().getName());
        }
        if (!(o2 instanceof Cookie)) {
            throw new ClassCastException(o2.getClass().getName());
        }
        Cookie c1 = (Cookie) o1;
        Cookie c2 = (Cookie) o2;
        if (c1.getPath() == null && c2.getPath() == null) {
            return 0;
        } else if (c1.getPath() == null) {
            // null is assumed to be "/"
            if (c2.getPath().equals(CookieSpec.PATH_DELIM)) {
                return 0;
            } else {
                return -1;
            }
        } else if (c2.getPath() == null) {
            // null is assumed to be "/"
            if (c1.getPath().equals(CookieSpec.PATH_DELIM)) {
                return 0;
            } else {
                return 1;
            }
        } else {
// BEGIN IA/HERITRIX CHANGE
// based on: https://cwiki.apache.org/jira/browse/HTTPCLIENT-645
            return c1.getPath().compareTo(c2.getPath());
//          return STRING_COLLATOR.compare(c1.getPath(), c2.getPath());
// END IA/HERITRIX CHANGE
        }
    }

    /**
     * Return a textual representation of the cookie.
     * 
     * @return string.
     * 
     * @see #toExternalForm
     */
    public String toString() {
        return toExternalForm();
    }

// BEGIN IA/HERITRIX ADDITION
    /**
     * Create a 'sort key' for this Cookie that will cause it to sort 
     * alongside other Cookies of the same domain (with or without leading
     * '.'). This helps cookie-match checks consider only narrow set of
     * possible matches, rather than all cookies. 
     * 
     * Only two cookies that are equals() (same domain, path, name) will have
     * the same sort key. The '\1' separator character is important in 
     * conjunction with Cookie.DOMAIN_OVERBOUNDS, allowing keys based on the
     * domain plus an extension to define the relevant range in a SortedMap. 
     * @return String sort key for this cookie
     */
    public String getSortKey() {
        String domain = getDomain();
        return (domain.startsWith(".")) 
            ? domain.substring(1) + "\1.\1" + getPath() + "\1" + getName() 
            : domain + "\1\1" + getPath() + "\1" + getName();
    }
//  END IA/HERITRIX ADDITION   
    
   // ----------------------------------------------------- Instance Variables

   /** Comment attribute. */
   private String  cookieComment;

   /** Domain attribute. */
   private String  cookieDomain;

   /** Expiration {@link Date}. */
   private Date    cookieExpiryDate;

   /** Path attribute. */
   private String  cookiePath;

   /** My secure flag. */
   private boolean isSecure;

   /**
    * Specifies if the set-cookie header included a Path attribute for this
    * cookie
    */
   private boolean hasPathAttribute = false;

   /**
    * Specifies if the set-cookie header included a Domain attribute for this
    * cookie
    */
   private boolean hasDomainAttribute = false;

   /** The version of the cookie specification I was created from. */
   private int     cookieVersion = 0;

   // -------------------------------------------------------------- Constants

   /** 
    * Collator for Cookie comparisons.  Could be replaced with references to
    * specific Locales.
    */
   @SuppressWarnings("unused") // IA/HERITRIX ADDITION
   private static final RuleBasedCollator STRING_COLLATOR =
        (RuleBasedCollator) RuleBasedCollator.getInstance(
                                                new Locale("en", "US", ""));

   /** Log object for this class */
   private static final Log LOG = LogFactory.getLog(Cookie.class);

// BEGIN IA/HERITRIX ADDITION
   /**
    * Character which, if appended to end of a domain, will give a 
    * boundary key that sorts past all Cookie sortKeys for the same
    * domain. 
    */
   public static final char DOMAIN_OVERBOUNDS = '\2';
// END IA/HERITRIX ADDITION
}

