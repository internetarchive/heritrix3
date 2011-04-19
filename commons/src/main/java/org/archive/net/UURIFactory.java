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

import gnu.inet.encoding.IDNA;
import gnu.inet.encoding.IDNAException;
import it.unimi.dsi.mg4j.util.MutableString;

import java.io.UnsupportedEncodingException;
import java.util.BitSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.httpclient.URI;
import org.apache.commons.httpclient.URIException;
import org.archive.util.TextUtils;

/**
 * Factory that returns UURIs.
 * 
 * Does escaping and fixup on URIs massaging in accordance with RFC2396 and to
 * match browser practice. For example, it removes any '..' if first thing in
 * the path as per IE, converts backslashes preceding the query string to
 * forward slashes, and discards any 'fragment'/anchor portion of the URI. This
 * class will also fail URIs if they are longer than IE's allowed maximum
 * length.
 * 
 * <p>
 * TODO: Test logging.
 * 
 * @author stack
 */
public class UURIFactory extends URI {
    
    private static final long serialVersionUID = -6146295130382209042L;

    /**
     * Logging instance.
     */
    private static Logger logger =
        Logger.getLogger(UURIFactory.class.getName());
    
    /**
     * The single instance of this factory.
     */
    private static final UURIFactory factory = new UURIFactory();
    
    /**
     * RFC 2396-inspired regex.
     *
     * From the RFC Appendix B:
     * <pre>
     * URI Generic Syntax                August 1998
     *
     * B. Parsing a URI Reference with a Regular Expression
     *
     * As described in Section 4.3, the generic URI syntax is not sufficient
     * to disambiguate the components of some forms of URI.  Since the
     * "greedy algorithm" described in that section is identical to the
     * disambiguation method used by POSIX regular expressions, it is
     * natural and commonplace to use a regular expression for parsing the
     * potential four components and fragment identifier of a URI reference.
     *
     * The following line is the regular expression for breaking-down a URI
     * reference into its components.
     *
     * ^(([^:/?#]+):)?(//([^/?#]*))?([^?#]*)(\?([^#]*))?(#(.*))?
     * 12            3  4          5       6  7        8 9
     *
     * The numbers in the second line above are only to assist readability;
     * they indicate the reference points for each subexpression (i.e., each
     * paired parenthesis).  We refer to the value matched for subexpression
     * <n> as $<n>.  For example, matching the above expression to
     *
     * http://www.ics.uci.edu/pub/ietf/uri/#Related
     *
     * results in the following subexpression matches:
     *
     * $1 = http:
     * $2 = http
     * $3 = //www.ics.uci.edu
     * $4 = www.ics.uci.edu
     * $5 = /pub/ietf/uri/
     * $6 = <undefined>
     * $7 = <undefined>
     * $8 = #Related
     * $9 = Related
     *
     * where <undefined> indicates that the component is not present, as is
     * the case for the query component in the above example.  Therefore, we
     * can determine the value of the four components and fragment as
     *
     * scheme    = $2
     * authority = $4
     * path      = $5
     * query     = $7
     * fragment  = $9
     * </pre>
     *
     * -- 
     * <p>Below differs from the rfc regex in that... 
     * (1) it has java escaping of regex characters 
     * (2) we allow a URI made of a fragment only (Added extra
     * group so indexing is off by one after scheme).
     * (3) scheme is limited to legal scheme characters 
     */
    final public static Pattern RFC2396REGEX = Pattern.compile(
        "^(([a-zA-Z][a-zA-Z0-9\\+\\-\\.]*):)?((//([^/?#]*))?([^?#]*)(\\?([^#]*))?)?(#(.*))?");
    //    12                             34  5          6       7   8          9 A
    //                                2 1             54        6          87 3      A9    // 1: scheme
    // 2: scheme:
    // 3: //authority/path
    // 4: //authority
    // 5: authority
    // 6: path
    // 7: ?query
    // 8: query 
    // 9: #fragment
    // A: fragment

    public static final String SLASHDOTDOTSLASH = "^(/\\.\\./)+";
    public static final String SLASH = "/";
    public static final String HTTP = "http";
    public static final String HTTP_PORT = ":80";
    public static final String HTTPS = "https";
    public static final String HTTPS_PORT = ":443";
    public static final String DOT = ".";
    public static final String EMPTY_STRING = "";
    public static final String NBSP = "\u00A0";
    public static final String SPACE = " ";
    public static final String ESCAPED_SPACE = "%20";
    public static final String TRAILING_ESCAPED_SPACE = "^(.*)(%20)+$";
    public static final String PIPE = "|";
    public static final String PIPE_PATTERN = "\\|";
    public static final String ESCAPED_PIPE = "%7C";
    public static final String CIRCUMFLEX = "^";
    public static final String CIRCUMFLEX_PATTERN = "\\^";
    public static final String ESCAPED_CIRCUMFLEX = "%5E";
    public static final String QUOT = "\"";
    public static final String ESCAPED_QUOT = "%22";
    public static final String SQUOT = "'";
    public static final String ESCAPED_SQUOT = "%27";
    public static final String APOSTROPH = "`";
    public static final String ESCAPED_APOSTROPH = "%60";
    public static final String LSQRBRACKET = "[";
    public static final String LSQRBRACKET_PATTERN = "\\[";
    public static final String ESCAPED_LSQRBRACKET = "%5B";
    public static final String RSQRBRACKET = "]";
    public static final String RSQRBRACKET_PATTERN = "\\]";
    public static final String ESCAPED_RSQRBRACKET = "%5D";
    public static final String LCURBRACKET = "{";
    public static final String LCURBRACKET_PATTERN = "\\{";
    public static final String ESCAPED_LCURBRACKET = "%7B";
    public static final String RCURBRACKET = "}";
    public static final String RCURBRACKET_PATTERN = "\\}";
    public static final String ESCAPED_RCURBRACKET = "%7D";
    public static final String BACKSLASH = "\\";
    public static final String ESCAPED_BACKSLASH = "%5C";
    public static final String STRAY_SPACING = "[\n\r\t]+";
    public static final String IMPROPERESC_REPLACE = "%25$1";
    public static final String IMPROPERESC =
        "%((?:[^\\p{XDigit}])|(?:.[^\\p{XDigit}])|(?:\\z))";
    public static final String COMMERCIAL_AT = "@";
    public static final char PERCENT_SIGN = '%';
    public static final char COLON = ':';
    
    /**
     * First percent sign in string followed by two hex chars.
     */
    public static final String URI_HEX_ENCODING =
        "^[^%]*%[\\p{XDigit}][\\p{XDigit}].*";
    
    /**
     * Authority port number regex.
     */
    final static Pattern PORTREGEX = Pattern.compile("(.*:)([0-9]+)$");
    
    /**
     * Characters we'll accept in the domain label part of a URI
     * authority: ASCII letters-digits-hyphen (LDH) plus underscore,
     * with single intervening '.' characters.
     * 
     * (We accept '_' because DNS servers have tolerated for many
     * years counter to spec; we also accept dash patterns and ACE
     * prefixes that will be rejected by IDN-punycoding attempt.)
     */
    final static String ACCEPTABLE_ASCII_DOMAIN =
        "^(?:[a-zA-Z0-9_-]++(?:\\.)?)++$";
    
    /**
     * Pattern that looks for case of three or more slashes after the 
     * scheme.  If found, we replace them with two only as mozilla does.
     */
    final static Pattern HTTP_SCHEME_SLASHES =
        Pattern.compile("^(https?://)/+(.*)");
    
    /**
     * Pattern that looks for case of two or more slashes in a path.
     */
    final static Pattern MULTIPLE_SLASHES = Pattern.compile("//+");
    
    /**
     * Protected constructor.
     */
    private UURIFactory() {
        super();
    }
    
    /**
     * @param uri URI as string.
     * @return An instance of UURI
     * @throws URIException
     */
    public static UURI getInstance(String uri) throws URIException {
        return UURIFactory.factory.create(uri);
    }
    
    /**
     * @param uri URI as string.
     * @param charset Character encoding of the passed uri string.
     * @return An instance of UURI
     * @throws URIException
     */
    public static UURI getInstance(String uri, String charset)
    		throws URIException {
        return UURIFactory.factory.create(uri, charset);
    }
    
    /**
     * @param base Base uri to use resolving passed relative uri.
     * @param relative URI as string.
     * @return An instance of UURI
     * @throws URIException
     */
    public static UURI getInstance(UURI base, String relative)
    		throws URIException {
//      return base.resolve(relative);
        return UURIFactory.factory.create(base, relative);
    }

    /**
     * @param uri URI as string.
     * @return Instance of UURI.
     * @throws URIException
     */
    private UURI create(String uri) throws URIException {
        return create(uri, UURI.getDefaultProtocolCharset());
    }
    
    /**
     * @param uri URI as string.
     * @param charset Original encoding of the string.
     * @return Instance of UURI.
     * @throws URIException
     */
    private UURI create(String uri, String charset) throws URIException {
        UURI uuri  = new UURI(fixup(uri, null, charset), true, charset);
        if (logger.isLoggable(Level.FINE)) {
            logger.fine("URI " + uri +
                " PRODUCT " + uuri.toString() +
                " CHARSET " + charset);
        }
        return validityCheck(uuri);
    }
    
    /**
     * @param base UURI to use as a base resolving <code>relative</code>.
     * @param relative Relative URI.
     * @return Instance of UURI.
     * @throws URIException
     */
    private UURI create(UURI base, String relative) throws URIException {
        UURI uuri = new UURI(base, new UURI(fixup(relative, base, base.getProtocolCharset()),
            true, base.getProtocolCharset()));
        if (logger.isLoggable(Level.FINE)) {
            logger.fine(" URI " + relative +
                " PRODUCT " + uuri.toString() +
                " CHARSET " + base.getProtocolCharset() +
                " BASE " + base);
        }
        return validityCheck(uuri);
    }

    /**
     * Check the generated UURI.
     * 
     * At the least look at length of uuri string.  We were seeing case
     * where before escaping, string was &lt; MAX_URL_LENGTH but after was
     * &gt;.  Letting out a too-big message was causing us troubles later
     * down the processing chain.
     * @param uuri Created uuri to check.
     * @return The passed <code>uuri</code> so can easily inline this check.
     * @throws URIException
     */
    protected UURI validityCheck(UURI uuri) throws URIException {
        if (uuri.getRawURI().length > UURI.MAX_URL_LENGTH) {
           throw new URIException("Created (escaped) uuri > " +
              UURI.MAX_URL_LENGTH +": "+uuri.toString());
        }
        return uuri;
    }
    
    /**
     * Do heritrix fix-up on passed uri string.
     *
     * Does heritrix escaping; usually escaping done to make our behavior align
     * with IEs.  This method codifies our experience pulling URIs from the
     * wilds.  Its does all the escaping we want; its output can always be
     * assumed to be 'escaped' (though perhaps to a laxer standard than the 
     * vanilla HttpClient URI class or official specs might suggest). 
     *
     * @param uri URI as string.
     * @param base May be null.
     * @param e True if the uri is already escaped.
     * @return A fixed up URI string.
     * @throws URIException
     */
    private String fixup(String uri, final URI base, final String charset)
    throws URIException {
        if (uri == null) {
            throw new NullPointerException();
        } else if (uri.length() == 0 && base == null) {
            throw new URIException("URI length is zero (and not relative).");
        }
        
        if (uri.length() > UURI.MAX_URL_LENGTH) {
            // We check length here and again later after all convertions.
            throw new URIException("URI length > " + UURI.MAX_URL_LENGTH +
                ": " + uri);
        }
        
        // Replace nbsp with normal spaces (so that they get stripped if at
        // ends, or encoded if in middle)
        if (uri.indexOf(NBSP) >= 0) {
            uri = TextUtils.replaceAll(NBSP, uri, SPACE);
        }
        
        // Get rid of any trailing spaces or new-lines. 
        uri = uri.trim();
        
        // IE converts backslashes preceding the query string to slashes, rather
        // than to %5C. Since URIs that have backslashes usually work only with
        // IE, we will convert backslashes to slashes as well.
        int nextBackslash = uri.indexOf(BACKSLASH);
        if (nextBackslash >= 0) {
            int queryStart = uri.indexOf('?');
            StringBuilder tmp = new StringBuilder(uri);
            while (nextBackslash >= 0
                    && (queryStart < 0 || nextBackslash < queryStart)) {
                tmp.setCharAt(nextBackslash, '/');
                nextBackslash = uri.indexOf(BACKSLASH, nextBackslash + 1);
            }
            uri = tmp.toString();
        }
        
        // Remove stray TAB/CR/LF
        uri = TextUtils.replaceAll(STRAY_SPACING, uri, EMPTY_STRING);
        
        // Test for the case of more than two slashes after the http(s) scheme.
        // Replace with two slashes as mozilla does if found.
        // See [ 788219 ] URI Syntax Errors stop page parsing.
//        Matcher matcher = HTTP_SCHEME_SLASHES.matcher(uri);
        Matcher matcher = TextUtils.getMatcher(HTTP_SCHEME_SLASHES.pattern(), uri);
        if (matcher.matches()) {
            uri = matcher.group(1) + matcher.group(2);
        }
        TextUtils.recycleMatcher(matcher); 

        // now, minimally escape any whitespace
        uri = escapeWhitespace(uri);
        
        // For further processing, get uri elements.  See the RFC2396REGEX
        // comment above for explanation of group indices used in the below.
//        matcher = RFC2396REGEX.matcher(uri);
        matcher = TextUtils.getMatcher(RFC2396REGEX.pattern(), uri);
        if (!matcher.matches()) {
            throw new URIException("Failed parse of " + uri);
        }
        String uriScheme = checkUriElementAndLowerCase(matcher.group(2));
        String uriSchemeSpecificPart = checkUriElement(matcher.group(3));
        String uriAuthority = checkUriElement(matcher.group(5));
        String uriPath = checkUriElement(matcher.group(6));
        String uriQuery = checkUriElement(matcher.group(8));
        // UNUSED String uriFragment = checkUriElement(matcher.group(10));
        TextUtils.recycleMatcher(matcher); matcher = null;
        
        // Test if relative URI. If so, need a base to resolve against.
        if (uriScheme == null || uriScheme.length() <= 0) {
            if (base == null) {
                throw new URIException("Relative URI but no base: " + uri);
            }
        } else {
        	checkHttpSchemeSpecificPartSlashPrefix(base, uriScheme,
        		uriSchemeSpecificPart);
        }
        
        // fixup authority portion: lowercase/IDN-punycode any domain; 
        // remove stray trailing spaces
        uriAuthority = fixupAuthority(uriAuthority,charset);

        // Do some checks if absolute path.
        if (uriSchemeSpecificPart != null &&
                uriSchemeSpecificPart.startsWith(SLASH)) {
            if (uriPath != null) {
                // Eliminate '..' if its first thing in the path.  IE does this.
                uriPath = TextUtils.replaceFirst(SLASHDOTDOTSLASH, uriPath,
                    SLASH);
            }
            // Ensure root URLs end with '/': browsers always send "/"
            // on the request-line, so we should consider "http://host"
            // to be "http://host/".
            if (uriPath == null || EMPTY_STRING.equals(uriPath)) {
                uriPath = SLASH;
            }
        }

        if (uriAuthority != null) {
            if (uriScheme != null && uriScheme.length() > 0 &&
                    uriScheme.equals(HTTP)) {
                uriAuthority = checkPort(uriAuthority);
                uriAuthority = stripTail(uriAuthority, HTTP_PORT);
            } else if (uriScheme != null && uriScheme.length() > 0 &&
                    uriScheme.equals(HTTPS)) {
                uriAuthority = checkPort(uriAuthority);
                uriAuthority = stripTail(uriAuthority, HTTPS_PORT);
            }
            // Strip any prefix dot or tail dots from the authority.
            uriAuthority = stripTail(uriAuthority, DOT);
            uriAuthority = stripPrefix(uriAuthority, DOT);
        } else {
            // no authority; may be relative. consider stripping scheme
            // to work-around org.apache.commons.httpclient.URI bug
            // ( http://issues.apache.org/jira/browse/HTTPCLIENT-587 )
            if (uriScheme != null && base != null
                    && uriScheme.equals(base.getScheme())) {
                // uriScheme redundant and will only confound httpclient.URI
                uriScheme = null; 
            }
        }
        
        // Ensure minimal escaping. Use of 'lax' URI and URLCodec 
        // means minimal escaping isn't necessarily complete/consistent.
        // There is a chance such lax encoding will throw exceptions
        // later at inconvenient times. 
        //
        // One reason for these bad escapings -- though not the only --
        // is that the page is using an encoding other than the ASCII or the
        // UTF-8 that is our default URI encoding.  In this case the parent
        // class is burping on the passed URL encoding.  If the page encoding
        // was passed into this factory, the encoding seems to be parsed
        // correctly (See the testEscapedEncoding unit test).
        //
        // This fixup may cause us to miss content.  There is the charset case
        // noted above.  TODO: Look out for cases where we fail other than for
        // the above given reason which will be fixed when we address
        // '[ 913687 ] Make extractors interrogate for charset'.

        uriPath = ensureMinimalEscaping(uriPath, charset);
        uriQuery = ensureMinimalEscaping(uriQuery, charset,
            LaxURLCodec.QUERY_SAFE);

        // Preallocate.  The '1's and '2's in below are space for ':',
        // '//', etc. URI characters.
        MutableString s = new MutableString(
            ((uriScheme != null)? uriScheme.length(): 0)
            + 1 // ';' 
            + ((uriAuthority != null)? uriAuthority.length(): 0)
            + 2 // '//'
            + ((uriPath != null)? uriPath.length(): 0)
            + 1 // '?'
            + ((uriQuery != null)? uriQuery.length(): 0));
        appendNonNull(s, uriScheme, ":", true);
        appendNonNull(s, uriAuthority, "//", false);
        appendNonNull(s, uriPath, "", false);
        appendNonNull(s, uriQuery, "?", false);
        return s.toString();
    }
    
    /**
     * If http(s) scheme, check scheme specific part begins '//'.
     * @throws URIException 
     * @see http://www.faqs.org/rfcs/rfc1738.html Section 3.1. Common Internet
     * Scheme Syntax
     */
    protected void checkHttpSchemeSpecificPartSlashPrefix(final URI base,
            final String scheme, final String schemeSpecificPart)
    throws URIException {
        if (scheme == null || scheme.length() <= 0) {
            return;
        }
        if (!scheme.equals("http") && !scheme.equals("https")) {
            return;
        }
        if ( schemeSpecificPart == null 
                || !schemeSpecificPart.startsWith("//")) {
            // only acceptable if schemes match
            if (base == null || !scheme.equals(base.getScheme())) {
                throw new URIException(
                        "relative URI with scheme only allowed for " +
                        "scheme matching base");
            } 
            return; 
        }
        if (schemeSpecificPart.length() <= 2) {
            throw new URIException("http scheme specific part is " +
                "too short: " + schemeSpecificPart);
        }
    }
    
    /**
     * Fixup 'authority' portion of URI, by removing any stray 
     * encoded spaces, lowercasing any domain names, and applying
     * IDN-punycoding to Unicode domains. 
     * 
     * @param uriAuthority the authority string to fix
     * @return fixed version
     * @throws URIException
     */
    private String fixupAuthority(String uriAuthority, String charset) throws URIException {
        // Lowercase the host part of the uriAuthority; don't destroy any
        // userinfo capitalizations.  Make sure no illegal characters in
        // domainlabel substring of the uri authority.
        if (uriAuthority != null) {
            // Get rid of any trailing escaped spaces:
            // http://www.archive.org%20.  Rare but happens.
            // TODO: reevaluate: do IE or firefox do such mid-URI space-removal?
            // if not, we shouldn't either. 
            while(uriAuthority.endsWith(ESCAPED_SPACE)) {
                uriAuthority = uriAuthority.substring(0,uriAuthority.length()-3);
            }

            // lowercase & IDN-punycode only the domain portion
            int atIndex = uriAuthority.indexOf(COMMERCIAL_AT);
            int portColonIndex = uriAuthority.indexOf(COLON,(atIndex<0)?0:atIndex);
            if(atIndex<0 && portColonIndex<0) {
                // most common case: neither userinfo nor port
                return fixupDomainlabel(uriAuthority);
            } else if (atIndex<0 && portColonIndex>-1) {
                // next most common: port but no userinfo
                String domain = fixupDomainlabel(uriAuthority.substring(0,portColonIndex));
                String port = uriAuthority.substring(portColonIndex);
                return domain + port;
            } else if (atIndex>-1 && portColonIndex<0) {
                // uncommon: userinfo, no port
                String userinfo = ensureMinimalEscaping(uriAuthority.substring(0,atIndex+1),charset);
                String domain = fixupDomainlabel(uriAuthority.substring(atIndex+1));
                return userinfo + domain;
            } else {
                // uncommon: userinfo, port
                String userinfo = ensureMinimalEscaping(uriAuthority.substring(0,atIndex+1),charset);
                String domain = fixupDomainlabel(uriAuthority.substring(atIndex+1,portColonIndex));
                String port = uriAuthority.substring(portColonIndex);
                return userinfo + domain + port;
            }
        }
        return uriAuthority;
    }
    
    /**
     * Fixup the domain label part of the authority.
     * 
     * We're more lax than the spec. in that we allow underscores.
     * 
     * @param label Domain label to fix.
     * @return Return fixed domain label.
     * @throws URIException
     */
    private String fixupDomainlabel(String label)
    throws URIException {
        
        // apply IDN-punycoding, as necessary
        try {
            // TODO: optimize: only apply when necessary, or
            // keep cache of recent encodings
            label = IDNA.toASCII(label);
        } catch (IDNAException e) {
            if(TextUtils.matches(ACCEPTABLE_ASCII_DOMAIN,label)) {
                // domain name has ACE prefix, leading/trailing dash, or 
                // underscore -- but is still a name we wish to tolerate;
                // simply continue
            } else {
                // problematic domain: neither ASCII acceptable characters
                // nor IDN-punycodable, so throw exception 
                // TODO: change to HeritrixURIException so distinguishable
                // from URIExceptions in library code
                URIException ue = new URIException(e+" "+label);
                ue.initCause(e);
                throw ue;
            }
        }
        label = label.toLowerCase();
        return label;
    }
    
    /**
     * Ensure that there all characters needing escaping
     * in the passed-in String are escaped. Stray '%' characters
     * are *not* escaped, as per browser behavior. 
     * 
     * @param u String to escape
     * @param charset 
     * @return string with any necessary escaping applied
     */
    private String ensureMinimalEscaping(String u, final String charset) {
        return ensureMinimalEscaping(u, charset, LaxURLCodec.EXPANDED_URI_SAFE);
    }
    
    /**
     * Ensure that there all characters needing escaping
     * in the passed-in String are escaped. Stray '%' characters
     * are *not* escaped, as per browser behavior. 
     * 
     * @param u String to escape
     * @param charset 
     * @param bitset 
     * @return string with any necessary escaping applied
     */
    private String ensureMinimalEscaping(String u, final String charset,
            final BitSet bitset) {
        if (u == null) {
            return null;
        }
        for (int i = 0; i < u.length(); i++) {
            char c = u.charAt(i);
            if (!bitset.get(c)) {
                try {
                    u = LaxURLCodec.DEFAULT.encode(bitset, u, charset);
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                }
                break;
            }
        }
        return u;
    }

    /**
     * Escape any whitespace found.
     * 
     * The parent class takes care of the bulk of escaping.  But if any
     * instance of escaping is found in the URI, then we ask for parent
     * to do NO escaping.  Here we escape any whitespace found irrespective
     * of whether the uri has already been escaped.  We do this for
     * case where uri has been judged already-escaped only, its been
     * incompletly done and whitespace remains.  Spaces, etc., in the URI are
     * a real pain.  Their presence will break log file and ARC parsing.
     * @param uri URI string to check.
     * @return uri with spaces escaped if any found.
     */
    protected String escapeWhitespace(String uri) {
        // Just write a new string anyways.  The perl '\s' is not
        // as inclusive as the Character.isWhitespace so there are
        // whitespace characters we could miss.  So, rather than
        // write some awkward regex, just go through the string
        // a character at a time.  Only create buffer first time
        // we find a space.
        MutableString buffer = null;
        for (int i = 0; i < uri.length(); i++) {
            char c = uri.charAt(i);
            if (Character.isWhitespace(c)) {
                if (buffer == null) {
                    buffer = new MutableString(uri.length() +
                        2 /*If space, two extra characters (at least)*/);
                    buffer.append(uri.substring(0, i));
                }
                buffer.append("%");
                String hexStr = Integer.toHexString(c);
                if ((hexStr.length() % 2) > 0) {
                    buffer.append("0");
                }
                buffer.append(hexStr);
                
            } else {
                if (buffer != null) {
                    buffer.append(c);
                }
            }
        }
        return (buffer !=  null)? buffer.toString(): uri;
    }

    /**
     * Check port on passed http authority.  Make sure the size is not larger
     * than allowed: See the 'port' definition on this
     * page, http://www.kerio.com/manual/wrp/en/418.htm.
     * Also, we've seen port numbers of '0080' whose leading zeros confuse
     * the parent class. Strip the leading zeros.
     *
     * @param uriAuthority
     * @return Null or an amended port number.
     * @throws URIException
     */
    private String checkPort(String uriAuthority)
    throws URIException {
//        Matcher m = PORTREGEX.matcher(uriAuthority);
        Matcher m = TextUtils.getMatcher(PORTREGEX.pattern(), uriAuthority);
        if (m.matches()) {
            String no = m.group(2);
            if (no != null && no.length() > 0) {
                // First check if the port has leading zeros
                // as in '0080'.  Strip them if it has and
                // then reconstitute the uriAuthority.  Be careful
                // of cases where port is '0' or '000'.
                while (no.charAt(0) == '0' && no.length() > 1) {
                    no = no.substring(1);
                }
                uriAuthority = m.group(1) + no;
                // Now makesure the number is legit.
                int portNo = 0;
                try {
                    portNo = Integer.parseInt(no);
                } catch (NumberFormatException nfe) {
                    // just catch and leave portNo at illegal 0
                }
                if (portNo <= 0 || portNo > 65535) {
                    throw new URIException("Port out of bounds: " +
                        uriAuthority);
                }
            }
        }
        TextUtils.recycleMatcher(m); 
        return uriAuthority;
    }

    /**
     * @param b Buffer to append to.
     * @param str String to append if not null.
     * @param substr Suffix or prefix to use if <code>str</code> is not null.
     * @param suffix True if <code>substr</code> is a suffix.
     */
    private void appendNonNull(MutableString b, String str, String substr,
            boolean suffix) {
        if (str != null && str.length() > 0) {
            if (!suffix) {
                b.append(substr);
            }
            b.append(str);
            if (suffix) {
                b.append(substr);
            }
        }
    }

    /**
     * @param str String to work on.
     * @param prefix Prefix to strip if present.
     * @return <code>str</code> w/o <code>prefix</code>.
     */
    private String stripPrefix(String str, String prefix) {
        return str.startsWith(prefix)?
            str.substring(prefix.length(), str.length()):
            str;
    }

    /**
     * @param str String to work on.
     * @param tail Tail to strip if present.
     * @return <code>str</code> w/o <code>tail</code>.
     */
    private static String stripTail(String str, String tail) {
        return str.endsWith(tail)?
            str.substring(0, str.length() - tail.length()):
            str;
    }

    /**
     * @param element to examine.
     * @return Null if passed null or an empty string otherwise
     * <code>element</code>.
     */
    private String checkUriElement(String element) {
        return (element == null || element.length() <= 0)? null: element;
    }

    /**
     * @param element to examine and lowercase if non-null.
     * @return Null if passed null or an empty string otherwise
     * <code>element</code> lowercased.
     */
    private String checkUriElementAndLowerCase(String element) {
        String tmp = checkUriElement(element);
        return (tmp != null)? tmp.toLowerCase(): tmp;
    }
}
