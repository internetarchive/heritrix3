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

import java.util.Arrays;
import java.util.BitSet;

import org.apache.commons.httpclient.URI;
import org.apache.commons.httpclient.URIException;
import org.apache.commons.httpclient.util.EncodingUtil;

/**
 * URI subclass which allows partial/inconsistent encoding, matching
 * the URIs which will be relayed in requests from popular web
 * browsers (esp. Mozilla Firefox and MS IE).
 * 
 * @author gojomo
 */
public class LaxURI extends URI {

    private static final long serialVersionUID = 5273922211722239537L;
    
    final protected static char[] HTTP_SCHEME = {'h','t','t','p'};
    final protected static char[] HTTPS_SCHEME = {'h','t','t','p','s'};
    
    protected static final BitSet lax_rel_segment = new BitSet(256);
    // Static initializer for lax_rel_segment
    static {
        lax_rel_segment.or(rel_segment);
        lax_rel_segment.set(':'); // allow ':'
        // TODO: add additional allowances as need is demonstrated
    }

    protected static final BitSet lax_abs_path = new BitSet(256);
    static {
        lax_abs_path.or(abs_path);
        lax_abs_path.set('|'); // tests indicate Firefox (1.0.6) doesn't escape.
    }
    
    protected static final BitSet lax_rel_path = new BitSet(256);
    // Static initializer for rel_path
    static {
        lax_rel_path.or(lax_rel_segment);
        lax_rel_path.or(lax_abs_path);
    }
    
    protected static final BitSet lax_query = new BitSet(256);
    static {
        lax_query.or(query);
        lax_query.set('{'); // tests indicate FF doesn't escape { in query
        lax_query.set('}'); // tests indicate FF doesn't escape } in query
        lax_query.set('|'); // tests indicate FF doesn't escape | in query
        lax_query.set('['); // tests indicate FF doesn't escape [ in query
        lax_query.set(']'); // tests indicate FF doesn't escape ] in query
        lax_query.set('^'); // tests indicate FF doesn't escape ^ in query
    }
    
    // passthrough initializers
    public LaxURI(String uri, boolean escaped, String charset)
    throws URIException {
        super(uri,escaped,charset);
    }
    public LaxURI(URI base, URI relative) throws URIException {
        super(base,relative);
    }
    public LaxURI(String uri, boolean escaped) throws URIException {
        super(uri,escaped);
    }
    public LaxURI() {
        super();
    }

    // overridden to use this class's static decode()
    public String getURI() throws URIException {
        return (_uri == null) ? null : decode(_uri, getProtocolCharset());
    }
    
    // overridden to use this class's static decode()
    public String getPath() throws URIException {
        char[] p = getRawPath();
        return (p == null) ? null : decode(p, getProtocolCharset());
    }

    // overridden to use this class's static decode()
    public String getPathQuery() throws URIException {
        char[] rawPathQuery = getRawPathQuery();
        return (rawPathQuery == null) ? null : decode(rawPathQuery,
                getProtocolCharset());
    }
    // overridden to use this class's static decode()
    protected static String decode(char[] component, String charset)
            throws URIException {
        if (component == null) {
            throw new IllegalArgumentException(
                    "Component array of chars may not be null");
        }
        return decode(new String(component), charset);
    }

    // overridden to use IA's LaxURLCodec, which never throws DecoderException
    protected static String decode(String component, String charset)
            throws URIException {
        if (component == null) {
            throw new IllegalArgumentException(
                    "Component array of chars may not be null");
        }
        byte[] rawdata = null;
        //     try {
        rawdata = LaxURLCodec.decodeUrlLoose(EncodingUtil
                .getAsciiBytes(component));
        //     } catch (DecoderException e) {
        //         throw new URIException(e.getMessage());
        //     }
        return EncodingUtil.getString(rawdata, charset);
    }
    
    // overidden to lax() the acceptable-char BitSet passed in
    protected boolean validate(char[] component, BitSet generous) {
        return super.validate(component, lax(generous));
    }

    // overidden to lax() the acceptable-char BitSet passed in
    protected boolean validate(char[] component, int soffset, int eoffset,
            BitSet generous) {
        return super.validate(component, soffset, eoffset, lax(generous));
    }
    
    /**
     * Given a BitSet -- typically one of the URI superclass's
     * predefined static variables -- possibly replace it with
     * a more-lax version to better match the character sets
     * actually left unencoded in web browser requests
     * 
     * @param generous original BitSet
     * @return (possibly more lax) BitSet to use
     */
    protected BitSet lax(BitSet generous) {
        if (generous == rel_segment) {
            // Swap in more lax allowable set
            return lax_rel_segment;
        }
        if (generous == abs_path) {
            return lax_abs_path;
        }
        if (generous == query) {
            return lax_query;
        }
        if (generous == rel_path) {
            return lax_rel_path; 
        }
        // otherwise, leave as is
        return generous;
    }
    
    /** 
     * Coalesce the _host and _authority fields where 
     * possible.
     * 
     * In the web crawl/http domain, most URIs have an 
     * identical _host and _authority. (There is no port
     * or user info.) However, the superclass always 
     * creates two separate char[] instances. 
     * 
     * Notably, the lengths of these char[] fields are 
     * equal if and only if their values are identical.
     * This method makes use of this fact to reduce the
     * two instances to one where possible, slimming 
     * instances.  
     * 
     * @see org.apache.commons.httpclient.URI#parseAuthority(java.lang.String, boolean)
     */
    protected void parseAuthority(String original, boolean escaped)
            throws URIException {
        super.parseAuthority(original, escaped);
        if (_host != null && _authority != null
                && _host.length == _authority.length) {
            _host = _authority;
        }
    }
    
    
    /** 
     * Coalesce _scheme to existing instances, where appropriate.
     * 
     * In the web-crawl domain, most _schemes are 'http' or 'https',
     * but the superclass always creates a new char[] instance. For
     * these two cases, we replace the created instance with a 
     * long-lived instance from a static field, saving 12-14 bytes
     * per instance. 
     * 
     * @see org.apache.commons.httpclient.URI#setURI()
     */
    protected void setURI() {
        if (_scheme != null) {
            if (_scheme.length == 4 && Arrays.equals(_scheme, HTTP_SCHEME)) {
                _scheme = HTTP_SCHEME;
            } else if (_scheme.length == 5
                    && Arrays.equals(_scheme, HTTP_SCHEME)) {
                _scheme = HTTPS_SCHEME;
            }
        }
        super.setURI();
    }
    
    /**
     * IA OVERRIDDEN IN LaxURI TO INCLUDE FIX FOR 
     * http://issues.apache.org/jira/browse/HTTPCLIENT-588
     * AND
     * http://webteam.archive.org/jira/browse/HER-1268
     * 
     * In order to avoid any possilbity of conflict with non-ASCII characters,
     * Parse a URI reference as a <code>String</code> with the character
     * encoding of the local system or the document.
     * <p>
     * The following line is the regular expression for breaking-down a URI
     * reference into its components.
     * <p><blockquote><pre>
     *   ^(([^:/?#]+):)?(//([^/?#]*))?([^?#]*)(\?([^#]*))?(#(.*))?
     *    12            3  4          5       6  7        8 9
     * </pre></blockquote><p>
     * For example, matching the above expression to
     *   http://jakarta.apache.org/ietf/uri/#Related
     * results in the following subexpression matches:
     * <p><blockquote><pre>
     *               $1 = http:
     *  scheme    =  $2 = http
     *               $3 = //jakarta.apache.org
     *  authority =  $4 = jakarta.apache.org
     *  path      =  $5 = /ietf/uri/
     *               $6 = <undefined>
     *  query     =  $7 = <undefined>
     *               $8 = #Related
     *  fragment  =  $9 = Related
     * </pre></blockquote><p>
     *
     * @param original the original character sequence
     * @param escaped <code>true</code> if <code>original</code> is escaped
     * @throws URIException If an error occurs.
     */
    protected void parseUriReference(String original, boolean escaped)
        throws URIException {

        // validate and contruct the URI character sequence
        if (original == null) {
            throw new URIException("URI-Reference required");
        }

        /* @
         *  ^(([^:/?#]+):)?(//([^/?#]*))?([^?#]*)(\?([^#]*))?(#(.*))?
         */
        String tmp = original.trim();
        
        /*
         * The length of the string sequence of characters.
         * It may not be equal to the length of the byte array.
         */
        int length = tmp.length();

        /*
         * Remove the delimiters like angle brackets around an URI.
         */
        if (length > 0) {
            char[] firstDelimiter = { tmp.charAt(0) };
            if (validate(firstDelimiter, delims)) {
                if (length >= 2) {
                    char[] lastDelimiter = { tmp.charAt(length - 1) };
                    if (validate(lastDelimiter, delims)) {
                        tmp = tmp.substring(1, length - 1);
                        length = length - 2;
                    }
                }
            }
        }

        /*
         * The starting index
         */
        int from = 0;

        /*
         * The test flag whether the URI is started from the path component.
         */
        boolean isStartedFromPath = false;
        int atColon = tmp.indexOf(':');
        int atSlash = tmp.indexOf('/');
        if ((atColon <= 0 && !tmp.startsWith("//"))
            || (atSlash >= 0 && atSlash < atColon)) {
            isStartedFromPath = true;
        }

        /*
         * <p><blockquote><pre>
         *     @@@@@@@@
         *  ^(([^:/?#]+):)?(//([^/?#]*))?([^?#]*)(\?([^#]*))?(#(.*))?
         * </pre></blockquote><p>
         */
        int at = indexFirstOf(tmp, isStartedFromPath ? "/?#" : ":/?#", from);
        if (at == -1) { 
            at = 0;
        }

        /*
         * Parse the scheme.
         * <p><blockquote><pre>
         *  scheme    =  $2 = http
         *              @
         *  ^(([^:/?#]+):)?(//([^/?#]*))?([^?#]*)(\?([^#]*))?(#(.*))?
         * </pre></blockquote><p>
         */
        if (at > 0 && at < length && tmp.charAt(at) == ':') {
            char[] target = tmp.substring(0, at).toLowerCase().toCharArray();
            if (validate(target, scheme)) {
                _scheme = target;
                from = ++at;
            } else {
                // IA CHANGE:
                // do nothing; allow interpretation as URI with 
                // later colon in other syntactical component
            }
        }

        /*
         * Parse the authority component.
         * <p><blockquote><pre>
         *  authority =  $4 = jakarta.apache.org
         *                  @@
         *  ^(([^:/?#]+):)?(//([^/?#]*))?([^?#]*)(\?([^#]*))?(#(.*))?
         * </pre></blockquote><p>
         */
        // Reset flags
        _is_net_path = _is_abs_path = _is_rel_path = _is_hier_part = false;
        if (0 <= at && at < length && tmp.charAt(at) == '/') {
            // Set flag
            _is_hier_part = true;
            if (at + 2 < length && tmp.charAt(at + 1) == '/' 
                && !isStartedFromPath) {
                // the temporary index to start the search from
                int next = indexFirstOf(tmp, "/?#", at + 2);
                if (next == -1) {
                    next = (tmp.substring(at + 2).length() == 0) ? at + 2 
                        : tmp.length();
                }
                parseAuthority(tmp.substring(at + 2, next), escaped);
                from = at = next;
                // Set flag
                _is_net_path = true;
            }
            if (from == at) {
                // Set flag
                _is_abs_path = true;
            }
        }

        /*
         * Parse the path component.
         * <p><blockquote><pre>
         *  path      =  $5 = /ietf/uri/
         *                                @@@@@@
         *  ^(([^:/?#]+):)?(//([^/?#]*))?([^?#]*)(\?([^#]*))?(#(.*))?
         * </pre></blockquote><p>
         */
        if (from < length) {
            // rel_path = rel_segment [ abs_path ]
            int next = indexFirstOf(tmp, "?#", from);
            if (next == -1) {
                next = tmp.length();
            }
            if (!_is_abs_path) {
                if (!escaped 
                    && prevalidate(tmp.substring(from, next), disallowed_rel_path) 
                    || escaped 
                    && validate(tmp.substring(from, next).toCharArray(), rel_path)) {
                    // Set flag
                    _is_rel_path = true;
                } else if (!escaped 
                    && prevalidate(tmp.substring(from, next), disallowed_opaque_part) 
                    || escaped 
                    && validate(tmp.substring(from, next).toCharArray(), opaque_part)) {
                    // Set flag
                    _is_opaque_part = true;
                } else {
                    // the path component may be empty
                    _path = null;
                }
            }
            String s = tmp.substring(from, next);
            if (escaped) {
                setRawPath(s.toCharArray());
            } else {
                setPath(s);
            }
            at = next;
        }

        // set the charset to do escape encoding
        String charset = getProtocolCharset();

        /*
         * Parse the query component.
         * <p><blockquote><pre>
         *  query     =  $7 = <undefined>
         *                                        @@@@@@@@@
         *  ^(([^:/?#]+):)?(//([^/?#]*))?([^?#]*)(\?([^#]*))?(#(.*))?
         * </pre></blockquote><p>
         */
        if (0 <= at && at + 1 < length && tmp.charAt(at) == '?') {
            int next = tmp.indexOf('#', at + 1);
            if (next == -1) {
                next = tmp.length();
            }
            if (escaped) {
                _query = tmp.substring(at + 1, next).toCharArray();
                if (!validate(_query, query)) {
                    throw new URIException("Invalid query");
                }
            } else {
                _query = encode(tmp.substring(at + 1, next), allowed_query, charset);
            }
            at = next;
        }

        /*
         * Parse the fragment component.
         * <p><blockquote><pre>
         *  fragment  =  $9 = Related
         *                                                   @@@@@@@@
         *  ^(([^:/?#]+):)?(//([^/?#]*))?([^?#]*)(\?([^#]*))?(#(.*))?
         * </pre></blockquote><p>
         */
        if (0 <= at && at + 1 <= length && tmp.charAt(at) == '#') {
            if (at + 1 == length) { // empty fragment
                _fragment = "".toCharArray();
            } else {
                _fragment = (escaped) ? tmp.substring(at + 1).toCharArray() 
                    : encode(tmp.substring(at + 1), allowed_fragment, charset);
            }
        }

        // set this URI.
        setURI();
    }
    
}
