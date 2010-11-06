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
package org.archive.net;

import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;

import org.apache.commons.httpclient.URIException;
import org.archive.util.SURT;
import org.archive.util.TextUtils;

import com.esotericsoftware.kryo.CustomSerialization;
import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.serialize.StringSerializer;


/**
 * Usable URI.
 * 
 * This class wraps {@link org.apache.commons.httpclient.URI} adding caching
 * and methods. It cannot be instantiated directly.  Go via UURIFactory.
 * 
 *  <p>We used to use {@link java.net.URI} for parsing URIs but ran across
 * quirky behaviors and bugs.  {@link java.net.URI} is not subclassable --
 * its final -- and its unlikely that java.net.URI will change any time soon
 * (See Gordon's considered petition here:
 * <a href="http://developer.java.sun.com/developer/bugParade/bugs/4939847.html">java.net.URI
 * should have loose/tolerant/compatibility option (or allow reuse)</a>).
 *
 * <p>This class tries to cache calculated strings such as the extracted host
 * and this class as a string rather than have the parent class rerun its
 * calculation everytime.
 *
 * @author gojomo
 * @author stack
 *
 * @see org.apache.commons.httpclient.URI
 */
public class UURI extends LaxURI
implements CharSequence, Serializable, CustomSerialization {

    private static final long serialVersionUID = -1277570889914647093L;

    //private static Logger LOGGER =
    //    Logger.getLogger(UURI.class.getName());
    
    /**
     * Consider URIs too long for IE as illegal.
     */
    public final static int MAX_URL_LENGTH = 2083;
    
    public static final String MASSAGEHOST_PATTERN = "^www\\d*\\.";

    /**
     * Cache of the host name.
     *
     * Super class calculates on every call.  Profiling shows us spend 30% of
     * total elapsed time in URI class.
     */
    private transient String cachedHost = null;

    /**
     * Cache of this uuri escaped as a string.
     *
     * Super class calculates on every call.  Profiling shows us spend 30% of
     * total elapsed time in URI class.
     */
    private transient String cachedEscapedURI = null;

    /**
     * Cache of this uuri escaped as a string.
     *
     * Super class calculates on every call.  Profiling shows us spend 30% of
     * total elapsed time in URI class.
     */
    private transient String cachedString = null;
    
    /**
     * Cached authority minus userinfo.
     */
    private transient String cachedAuthorityMinusUserinfo = null;

    /**
     * Cache of this uuri in SURT format
     */
    private transient String surtForm = null;
    
    // Technically, underscores are disallowed in the domainlabel
    // portion of hostname according to rfc2396 but we'll be more
    // loose and allow them. See: [ 1072035 ] [uuri] Underscore in
    // host messes up port parsing.
    static {
        hostname.set('_');
    }


    /**
     * Shutdown access to default constructor.
     */
    protected UURI() {
        super();
    }
    
    /**
     * @param uri String representation of an absolute URI.
     * @param escaped If escaped.
     * @param charset Charset to use.
     * @throws org.apache.commons.httpclient.URIException
     */
    protected UURI(String uri, boolean escaped, String charset)
    throws URIException {
        super(uri, escaped, charset);
        normalize();
    }
    
    /**
     * @param relative String representation of URI.
     * @param base Parent UURI to use derelativizing.
     * @throws org.apache.commons.httpclient.URIException
     */
    protected UURI(UURI base, UURI relative) throws URIException {
        super(base, relative);
        normalize();
    }

    /**
     * @param uri String representation of a URI.
     * @param escaped If escaped.
     * @throws NullPointerException
     * @throws URIException
     */
    protected UURI(String uri, boolean escaped) throws URIException, NullPointerException {
        super(uri,escaped);
        normalize();
    }

    /**
     * @param uri URI as string that is resolved relative to this UURI.
     * @return UURI that uses this UURI as base.
     * @throws URIException
     */
    public UURI resolve(String uri)
    throws URIException {
        return resolve(uri, false, // assume not escaped
            this.getProtocolCharset());
    }

    /**
     * @param uri URI as string that is resolved relative to this UURI.
     * @param e True if escaped.
     * @return UURI that uses this UURI as base.
     * @throws URIException
     */
    public UURI resolve(String uri, boolean e)
    throws URIException {
        return resolve(uri, e, this.getProtocolCharset());
    }
    
    /**
     * @param uri URI as string that is resolved relative to this UURI.
     * @param e True if uri is escaped.
     * @param charset Charset to use.
     * @return UURI that uses this UURI as base.
     * @throws URIException
     */
    public UURI resolve(String uri, boolean e, String charset)
    throws URIException {
        return new UURI(this, new UURI(uri, e, charset));
    }

    /**
     * Test an object if this UURI is equal to another.
     *
     * @param obj an object to compare
     * @return true if two URI objects are equal
     */
    public boolean equals(Object obj) {

        // normalize and test each components
        if (obj == this) {
            return true;
        }
        if (!(obj instanceof UURI)) {
            return false;
        }
        UURI another = (UURI) obj;
        // scheme
        if (!equals(this._scheme, another._scheme)) {
            return false;
        }
        // is_opaque_part or is_hier_part?  and opaque
        if (!equals(this._opaque, another._opaque)) {
            return false;
        }
        // is_hier_part
        // has_authority
        if (!equals(this._authority, another._authority)) {
            return false;
        }
        // path
        if (!equals(this._path, another._path)) {
            return false;
        }
        // has_query
        if (!equals(this._query, another._query)) {
            return false;
        }
        // UURIs do not have fragments
        return true;
    }

    /**
     * Strips www variants from the host.
     *
     * Strips www[0-9]*\. from the host.  If calling getHostBaseName becomes a
     * performance issue we should consider adding the hostBasename member that
     * is set on initialization.
     *
     * @return Host's basename.
     * @throws URIException
     */
    public String getHostBasename() throws URIException {
        // caching eliminated because this is rarely used
        // (only benefits legacy DomainScope, which should
        // be retired). Saves 4-byte object pointer in UURI
        // instances.
        return (this.getReferencedHost() == null) 
            ? null 
            : TextUtils.replaceFirst(MASSAGEHOST_PATTERN, 
                    this.getReferencedHost(), UURIFactory.EMPTY_STRING);
    }

    /**
     * Returns an alternate, functional String representation -- in this 
     * case, a String of the URI represented by this UURI instance.  
     * 
     * @return
     */
    public synchronized String toCustomString() {
        if (this.cachedString == null) {
            this.cachedString = super.toString();
            coalesceUriStrings();
        }
        return this.cachedString;
    }
    
    /**
     * Override to cache result
     * 
     * TODO: eliminate, moving most callers to toCustomString, to avoid 
     * overloading/diluting toString()
     * (see http://webteam.archive.org/confluence/display/Heritrix/Preserve+toString%28%29 )
     * @return String representation of this URI
     */
    public String toString() {
        return toCustomString();
    }

    public synchronized String getEscapedURI() {
        if (this.cachedEscapedURI == null) {
            this.cachedEscapedURI = super.getEscapedURI();
            coalesceUriStrings();
        }
        return this.cachedEscapedURI;
    }

    /**
     * The two String fields cachedString and cachedEscapedURI are 
     * usually identical; if so, coalesce into a single instance. 
     */
    protected void coalesceUriStrings() {
        if (this.cachedString != null && this.cachedEscapedURI != null
                && this.cachedString.length() == this.cachedEscapedURI.length()) {
            // lengths will only be identical if contents are identical
            // (deescaping will always shrink length), so coalesce to
            // use only single cached instance
            this.cachedString = this.cachedEscapedURI;
        }
    }
    
    public synchronized String getHost() throws URIException {
        if (this.cachedHost == null) {
            // If this._host is null, 3.0 httpclient throws
            // illegalargumentexception.  Don't go there.
            if (this._host != null) {
            	this.cachedHost = super.getHost();
                coalesceHostAuthorityStrings();
            }
        }
        return this.cachedHost;
    }
    
    /**
     * The two String fields cachedHost and cachedAuthorityMinusUserInfo are 
     * usually identical; if so, coalesce into a single instance. 
     */
    protected void coalesceHostAuthorityStrings() {
        if (this.cachedAuthorityMinusUserinfo != null
                && this.cachedHost != null
                && this.cachedHost.length() ==
                    this.cachedAuthorityMinusUserinfo.length()) {
            // lengths can only be identical if contents
            // are identical; use only one instance
            this.cachedAuthorityMinusUserinfo = this.cachedHost;
        }
    }

    /**
     * Return the referenced host in the UURI, if any, also extracting the 
     * host of a DNS-lookup URI where necessary. 
     * 
     * @return the target or topic host of the URI
     * @throws URIException
     */
    public String getReferencedHost() throws URIException {
        String referencedHost = this.getHost();
        if(referencedHost==null && this.getScheme().equals("dns")) {
            // extract target domain of DNS lookup
            String possibleHost = this.getCurrentHierPath();
            if(possibleHost != null && possibleHost.matches("[-_\\w\\.:]+")) {
                referencedHost = possibleHost;
            }
        }
        return referencedHost;
    }

    /**
     * @return Return the 'SURT' format of this UURI
     */
    public String getSurtForm() {
        if (surtForm == null) {
            surtForm = SURT.fromURI(this.toString());
        }
        return surtForm;
    }
    
    /**
     * Return the authority minus userinfo (if any).
     * 
     * If no userinfo present, just returns the authority.
     * 
     * @return The authority stripped of any userinfo if present.
     * @throws URIException
     */
	public String getAuthorityMinusUserinfo()
    throws URIException {
        if (this.cachedAuthorityMinusUserinfo == null) {
            String tmp = getAuthority();
            if (tmp != null && tmp.length() > 0) {
            	int index = tmp.indexOf('@');
                if (index >= 0 && index < tmp.length()) {
                    tmp = tmp.substring(index + 1);
                }
            }
            this.cachedAuthorityMinusUserinfo = tmp;
            coalesceHostAuthorityStrings();
        }
        return this.cachedAuthorityMinusUserinfo;
	}

    /* (non-Javadoc)
     * @see java.lang.CharSequence#length()
     */
    public int length() {
        return getEscapedURI().length();
    }

    /* (non-Javadoc)
     * @see java.lang.CharSequence#charAt(int)
     */
    public char charAt(int index) {
        return getEscapedURI().charAt(index);
    }

    /* (non-Javadoc)
     * @see java.lang.CharSequence#subSequence(int, int)
     */
    public CharSequence subSequence(int start, int end) {
        return getEscapedURI().subSequence(start,end);
    }

    /* (non-Javadoc)
     * @see java.lang.Comparable#compareTo(java.lang.Object)
     */
    public int compareTo(Object arg0) {
        return getEscapedURI().compareTo(arg0.toString());
    }


    
    /**
     * Test if passed String has likely URI scheme prefix.
     * @param possibleUrl URL string to examine.
     * @return True if passed string looks like it could be an URL.
     */
    public static boolean hasScheme(String possibleUrl) {
        boolean result = false;
        for (int i = 0; i < possibleUrl.length(); i++) {
            char c = possibleUrl.charAt(i);
            if (c == ':') {
                if (i != 0) {
                    result = true;
                }
                break;
            }
            if (!scheme.get(c)) {
                break;
            }
        }
        return result;
    }
    
    /**
     * @param pathOrUri A file path or a URI.
     * @return Path parsed from passed <code>pathOrUri</code>.
     * @throws URISyntaxException
     */
    public static String parseFilename(final String pathOrUri)
    throws URISyntaxException {
        String path = pathOrUri;
        if (UURI.hasScheme(pathOrUri)) {
            URI url = new URI(pathOrUri);
            path = url.getPath();
        }
        return (new File(path)).getName();
    }
    
    public void writeObjectData(Kryo kryo, ByteBuffer buffer) {
        StringSerializer.put(buffer, toCustomString());
    }

    public void readObjectData (Kryo kryo, ByteBuffer buffer) {
        try {
            parseUriReference(StringSerializer.get(buffer),true);
        } catch (URIException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    private void writeObject(ObjectOutputStream stream) throws IOException {
        stream.writeUTF(toCustomString());
      }
    
    private void readObject(ObjectInputStream stream) throws IOException,
    ClassNotFoundException {
        parseUriReference(stream.readUTF(),true);
    }
}
