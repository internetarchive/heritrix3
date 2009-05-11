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

import java.util.Iterator;
import java.util.TreeMap;

import junit.framework.TestCase;

import org.apache.commons.httpclient.URIException;
import org.apache.commons.lang.SerializationUtils;

/**
 * Test UURIFactory for proper UURI creation across variety of
 * important/tricky cases.
 * 
 * Be careful writing this file.  Make sure you write it with UTF-8 encoding.
 *
 * @author igor stack gojomo
 */
public class UURIFactoryTest extends TestCase {
	
	public final void testEscaping() throws URIException {
		// Note: single quote is not being escaped by URI class.
		final String ESCAPED_URISTR = "http://archive.org/" +
		    UURIFactory.ESCAPED_SPACE +
			UURIFactory.ESCAPED_SPACE +
			UURIFactory.ESCAPED_CIRCUMFLEX +
			UURIFactory.ESCAPED_QUOT +
			UURIFactory.SQUOT +
			UURIFactory.ESCAPED_APOSTROPH +
			UURIFactory.ESCAPED_LSQRBRACKET +
			UURIFactory.ESCAPED_RSQRBRACKET +
			UURIFactory.ESCAPED_LCURBRACKET +
			UURIFactory.ESCAPED_RCURBRACKET +
			UURIFactory.SLASH + "a.gif"; // NBSP and SPACE should be trimmed;
		
		final String URISTR = "http://archive.org/.././" + "\u00A0" +
		    UURIFactory.SPACE + UURIFactory.CIRCUMFLEX +
			UURIFactory.QUOT + UURIFactory.SQUOT +
			UURIFactory.APOSTROPH + UURIFactory.LSQRBRACKET +
			UURIFactory.RSQRBRACKET + UURIFactory.LCURBRACKET +
			UURIFactory.RCURBRACKET + UURIFactory.BACKSLASH +
			"test/../a.gif" + "\u00A0" + UURIFactory.SPACE;
		
		UURI uuri = UURIFactory.getInstance(URISTR);
		final String uuriStr = uuri.toString();
		assertEquals("expected escaping", ESCAPED_URISTR, uuriStr);
	}

    public final void testUnderscoreMakesPortParseFail() throws URIException {
        UURI uuri = UURIFactory.getInstance("http://one-two_three:8080/index.html");
        int port = uuri.getPort();
        assertTrue("Failed find of port " + uuri, port == 8080);
    }
    
    public final void testRelativeURIWithTwoSlashes() throws URIException {
        UURI base = UURIFactory.getInstance("http://www.archive.org");
        UURI uuri = UURIFactory.getInstance(base, "one//index.html");
        assertTrue("Doesn't do right thing with two slashes " + uuri,
            uuri.toString().equals(
                "http://www.archive.org/one//index.html"));
    }
    
    public final void testTrailingEncodedSpace() throws URIException {
        UURI uuri = UURIFactory.getInstance("http://www.nps-shoes.co.uk%20");
        assertTrue("Doesn't strip trailing encoded space 1 " + uuri,
            uuri.toString().equals("http://www.nps-shoes.co.uk/"));
        uuri = UURIFactory.getInstance("http://www.nps-shoes.co.uk%20%20%20");
        assertTrue("Doesn't strip trailing encoded space 2 " + uuri,
            uuri.toString().equals("http://www.nps-shoes.co.uk/"));
    }
    
    public final void testPort0080is80() throws URIException {
        UURI uuri = UURIFactory.getInstance("http://archive.org:0080");
        assertTrue("Doesn't strip leading zeros " + uuri,
            uuri.toString().equals("http://archive.org/"));
    }
    
// DISABLING TEST AS PRECURSOR TO ELIMINATION
// the problematic input given -- specifically the "%6s" incomplete uri-escape,
// shouldn't necessarily be rejected as a bad URI. IE and Firefox, at least, 
// will  attempt to fetch such an URL (getting, in this case against that ad 
// server, a bad-request error). Ideally, we'd generate exactly the same 
// request against the server as they do. However, with the most recent 
// fixup for stray '%' signs, we come close, but not exactly. That's enough
// to cause this test to fail (it's not getting the expected exception) but
// our almost-URI, which might be what was intended, is better than trying 
// nothing.
//    public final void testBadPath() {
//        String message = null;
//        try {
//            UURIFactory.getInstance("http://ads.as4x.tmcs.net/" +
//                "html.ng/site=cs&pagepos=102&page=home&adsize=1x1&context=" +
//                "generic&Params.richmedia=yes%26city%3Dseattle%26" +
//                "rstid%3D2415%26market_id%3D86%26brand%3Dcitysearch" +
//                "%6state%3DWA");
//        } catch (URIException e) {
//            message = e.getMessage();
//        }
//        assertNotNull("Didn't get expected exception.", message);
//    }   
    
    public final void testEscapeEncoding() throws URIException {
        UURI uuri = UURIFactory.getInstance("http://www.y1y1.com/" +
            "albums/userpics/11111/normal_%E3%E4%EC%EC%EC.jpg", "windows-1256");
        uuri.getPath();
    }   
    
    public final void testTooLongAfterEscaping() {
        StringBuffer buffer = new StringBuffer("http://www.archive.org/a/");
        // Append bunch of spaces.  When escaped, they'll triple in size.
        for (int i = 0; i < 1024; i++) {
        	buffer.append(" ");
        }
        buffer.append("/index.html");
        String message = null;
        try {
        	UURIFactory.getInstance(buffer.toString());
        } catch (URIException e) {
            message = e.getMessage();
        }
        assertTrue("Wrong or no exception: " + message, (message != null) &&
            message.startsWith("Created (escaped) uuri >"));
    }
	
	public final void testFtpUris() throws URIException {
		final String FTP = "ftp";
		final String AUTHORITY = "pfbuser:pfbuser@mprsrv.agri.gov.cn";
		final String PATH = "/clzreceive/";
		final String uri = FTP + "://" + AUTHORITY + PATH;
		UURI uuri = UURIFactory.getInstance(uri);
		assertTrue("Failed to get matching scheme: " + uuri.getScheme(),
				(uuri.getScheme()).equals(FTP));
		assertTrue("Failed to get matching authority: " +
				uuri.getAuthority(), (uuri.getAuthority()).equals(AUTHORITY));
		assertTrue("Failed to get matching path: " +
				uuri.getPath(), (uuri.getPath()).equals(PATH));       
	}
    
    public final void testWhitespaceEscaped() throws URIException {
        // Test that we get all whitespace even if the uri is
        // already escaped.
        String uri = "http://archive.org/index%25 .html";
        String tgtUri = "http://archive.org/index%25%20.html";
        UURI uuri = UURIFactory.getInstance(uri);
        assertTrue("Not equal " + uuri.toString(),
                uuri.toString().equals(tgtUri));     
        uri = "http://archive.org/index%25\u001D.html";
        tgtUri = "http://archive.org/index%25%1D.html".toLowerCase();
        uuri = UURIFactory.getInstance(uri);
        assertEquals("whitespace escaping", tgtUri, uuri.toString());
        uri = "http://gemini.info.usaid.gov/directory/" +
            "pbResults.cfm?&urlNameLast=Rumplestiltskin";
        tgtUri = "http://gemini.info.usaid.gov/directory/faxResults.cfm?" +
            "name=Ebenezer%20+Rumplestiltskin,&location=RRB%20%20%20%205%2E08%2D006";
        uuri = UURIFactory.getInstance(UURIFactory.getInstance(uri),
            "faxResults.cfm?name=Ebenezer +Rumplestiltskin,&location=" +
            "RRB%20%20%20%205%2E08%2D006");
        assertEquals("whitespace escaping", tgtUri, uuri.toString());
    }
    
//	public final void testFailedGetPath() throws URIException {
//		final String path = "/RealMedia/ads/" +
//		"click_lx.ads/%%PAGE%%/%%RAND%%/%%POS%%/%%CAMP%%/empty";
//        // decoding in getPath will interpret %CA as 8-bit escaped char,
//        // possibly incomplete
//		final String uri = "http://ads.nandomedia.com" + path;
//		final UURI uuri = UURIFactory.getInstance(uri);
//		String foundPath = uuri.getPath();
//		assertEquals("unexpected path", path, foundPath);
//	}
    
    public final void testDnsHost() throws URIException {
        String uri = "dns://ads.nandomedia.com:81/one.html";
        UURI uuri = UURIFactory.getInstance(uri);
        String host = uuri.getReferencedHost();
        assertTrue("Host is wrong " + host, host.equals("ads.nandomedia.com"));
        uri = "dns:ads.nandomedia.com";
        uuri = UURIFactory.getInstance(uri);
        host = uuri.getReferencedHost();
        assertTrue("Host is wrong " + host, host.equals("ads.nandomedia.com"));
        uri = "dns:ads.nandomedia.com?a=b";
        uuri = UURIFactory.getInstance(uri);
        host = uuri.getReferencedHost();
        assertTrue("Host is wrong " + host, host.equals("ads.nandomedia.com"));
    }
	
	public final void testPercentEscaping() throws URIException {
		final String uri = "http://archive.org/%a%%%%%.html";
        // tests indicate firefox (1.0.6) does not encode '%' at all
        final String tgtUri = "http://archive.org/%a%%%%%.html";
		UURI uuri = UURIFactory.getInstance(uri);
		assertEquals("Not equal",tgtUri, uuri.toString());
	}
    
	public final void testRelativeDblPathSlashes() throws URIException {
		UURI base = UURIFactory.getInstance("http://www.archive.org/index.html");
		UURI uuri = UURIFactory.getInstance(base, "JIGOU//KYC//INDEX.HTM");
        assertTrue("Double slash not working " + uuri.toString(),
                uuri.getPath().equals("/JIGOU//KYC//INDEX.HTM"));
	}
    
    public final void testRelativeWithScheme() throws URIException {
        UURI base = UURIFactory.getInstance("http://www.example.com/some/page");
        UURI uuri = UURIFactory.getInstance(base, "http:boo");
        assertTrue("Relative with scheme not working " + uuri.toString(),
                uuri.toString().equals("http://www.example.com/some/boo"));
    }
    
    public final void testBadBaseResolve() throws URIException {
        UURI base = UURIFactory.getInstance("http://license.joins.com/board/" +
            "etc_board_list.asp?board_name=new_main&b_type=&nPage=" +
            "2&category=G&lic_id=70&site=changeup&g_page=changeup&g_sPage=" +
            "notice&gate=02");
        UURIFactory.getInstance(base, "http://www.changeup.com/...</a");
    }
    
    public final void testTilde() throws URIException {
        noChangeExpected("http://license.joins.com/~igor");
    }
    
    public final void testCurlies() throws URIException {
        // Firefox allows curlies in the query string portion of a URL only
        // (converts curlies if they are in the path portion ahead of the
        // query string).
        UURI uuri =
            noChangeExpected("http://license.joins.com/igor?one={curly}");
        assertEquals(uuri.getQuery(), "one={curly}");
        assertEquals(UURIFactory.
                getInstance("http://license.joins.com/igor{curly}.html").
                    toString(),
            "http://license.joins.com/igor%7Bcurly%7D.html");
        boolean exception = false;
        try {
            UURIFactory.getInstance("http://license.{curly}.com/igor.html");
        } catch (URIException u) {
            exception = true;
        }
        assertTrue("Did not get exception.", exception);
    }
    
    protected UURI noChangeExpected(final String original)
    throws URIException {
        UURI uuri = UURIFactory.getInstance(original);
        assertEquals(original, uuri.toString());
        return uuri;
    }
    
	public final void testTrimSpaceNBSP() throws URIException {
		final String uri = "   http://archive.org/DIR WITH SPACES/" +
		UURIFactory.NBSP + "home.html    " + UURIFactory.NBSP + "   ";
		final String tgtUri =
			"http://archive.org/DIR%20WITH%20SPACES/%20home.html";
		UURI uuri = UURIFactory.getInstance(uri);
		assertTrue("Not equal " + uuri.toString(),
				uuri.toString().equals(tgtUri));
	}
	
	/**
	 * Test space plus encoding ([ 1010966 ] crawl.log has URIs with spaces in them).
	 * See <a href="http://sourceforge.net/tracker/index.php?func=detail&aid=1010966&group_id=73833&atid=539099">[ 1010966 ] crawl.log has URIs with spaces in them</a>.
	 * @throws URIException
	 */
	public final void testSpaceDoubleEncoding() throws URIException {
		final String uri = "http://www.brook.edu/i.html? %20taxonomy=Politics";
		final String encodedUri =
			"http://www.brook.edu/i.html?%20%20taxonomy=Politics";
		UURI uuri = UURIFactory.getInstance(uri, "ISO-8859-1");
		assertTrue("Not equal " + uuri.toString(),
				uuri.toString().equals(encodedUri));
	}
	
	/**
	 * Test for doubly-encoded sequences.
	 * See <a href="https://sourceforge.net/tracker/index.php?func=detail&aid=966219&group_id=73833&atid=539099">[ 966219 ] UURI doubly-encodes %XX sequences</a>.
	 * @throws URIException
	 */
	public final void testDoubleEncoding() throws URIException {
		final char ae = '\u00E6';
		final String uri = "http://archive.org/DIR WITH SPACES/home" +
		    ae + ".html";
		final String encodedUri =
			"http://archive.org/DIR%20WITH%20SPACES/home%E6.html";
		UURI uuri = UURIFactory.getInstance(uri, "ISO-8859-1");
		assertEquals("single encoding", encodedUri, uuri.toString());
		// Dbl-encodes.
		uuri = UURIFactory.getInstance(uuri.toString(), "ISO-8859-1");
		uuri = UURIFactory.getInstance(uuri.toString(), "ISO-8859-1");
		assertEquals("double encoding", encodedUri, uuri.toString());
		// Do default utf-8 test.
		uuri = UURIFactory.getInstance(uri);
		final String encodedUtf8Uri =
			"http://archive.org/DIR%20WITH%20SPACES/home%C3%A6.html";
		assertEquals("Not equal utf8", encodedUtf8Uri, uuri.toString());      
		// Now dbl-encode.
		uuri = UURIFactory.getInstance(uuri.toString());
		uuri = UURIFactory.getInstance(uuri.toString());
		assertEquals("Not equal (dbl-encoding) utf8", encodedUtf8Uri, uuri.toString());
	}
	
	/**
	 * Test for syntax errors stop page parsing.
	 * @see <a href="https://sourceforge.net/tracker/?func=detail&aid=788219&group_id=73833&atid=539099">[ 788219 ] URI Syntax Errors stop page parsing</a>
	 * @throws URIException
	 */
	public final void testThreeSlashes() throws URIException {
		UURI goodURI = UURIFactory.
		getInstance("http://lcweb.loc.gov/rr/goodtwo.html");
		String uuri = "http:///lcweb.loc.gov/rr/goodtwo.html";
		UURI rewrittenURI = UURIFactory.getInstance(uuri);
		assertTrue("Not equal " + goodURI + ", " + uuri,
				goodURI.toString().equals(rewrittenURI.toString()));
		uuri = "http:////lcweb.loc.gov/rr/goodtwo.html";
		rewrittenURI = UURIFactory.getInstance(uuri);
		assertTrue("Not equal " + goodURI + ", " + uuri,
				goodURI.toString().equals(rewrittenURI.toString()));
		// Check https.
		goodURI = UURIFactory.
		getInstance("https://lcweb.loc.gov/rr/goodtwo.html");
		uuri = "https:////lcweb.loc.gov/rr/goodtwo.html";
		rewrittenURI = UURIFactory.getInstance(uuri);
		assertTrue("Not equal " + goodURI + ", " + uuri,
				goodURI.toString().equals(rewrittenURI.toString()));
	}
	
	public final void testNoScheme() {
		boolean expectedException = false;
		String uuri = "www.loc.gov/rr/european/egw/polishex.html";
		try {
			UURIFactory.getInstance(uuri);
		} catch (URIException e) {
			// Expected exception.
			expectedException = true;
		}
		assertTrue("Didn't get expected exception: " + uuri, 
				expectedException); 
	}
	
	public final void testRelative() throws URIException {
		UURI uuriTgt = UURIFactory.
		getInstance("http://archive.org:83/home.html");
		UURI uri = UURIFactory.
		getInstance("http://archive.org:83/one/two/three.html");
		UURI uuri = UURIFactory.
		getInstance(uri, "/home.html");
		assertTrue("Not equal",
				uuriTgt.toString().equals(uuri.toString()));
	}
	
	/**
	 * Test that an empty uuri does the right thing -- that we get back the
	 * base.
	 *
	 * @throws URIException
	 */
	public final void testRelativeEmpty() throws URIException {
		UURI uuriTgt = UURIFactory.
		getInstance("http://archive.org:83/one/two/three.html");
		UURI uri = UURIFactory.
		getInstance("http://archive.org:83/one/two/three.html");
		UURI uuri = UURIFactory.
		getInstance(uri, "");
		assertTrue("Empty length don't work",
				uuriTgt.toString().equals(uuri.toString()));
	}
	
	public final void testAbsolute() throws URIException {
		UURI uuriTgt = UURIFactory.
		getInstance("http://archive.org:83/home.html");
		UURI uri = UURIFactory.
		getInstance("http://archive.org:83/one/two/three.html");
		UURI uuri = UURIFactory.
		getInstance(uri, "http://archive.org:83/home.html");
		assertTrue("Not equal",
				uuriTgt.toString().equals(uuri.toString()));
	}
	
	/**
	 * Test for [ 962892 ] UURI accepting/creating unUsable URIs (bad hosts).
	 * @see <a href="https://sourceforge.net/tracker/?func=detail&atid=539099&aid=962892&group_id=73833">[ 962892 ] UURI accepting/creating unUsable URIs (bad hosts)</a>
	 */
	public final void testHostWithLessThan() {
		checkExceptionOnIllegalDomainlabel("http://www.betamobile.com</A");
		checkExceptionOnIllegalDomainlabel(
		"http://C|/unzipped/426/spacer.gif");
		checkExceptionOnIllegalDomainlabel("http://www.lycos.co.uk\"/l/b/\"");
	}    
	
	/**
	 * Test for [ 1012520 ] UURI.length() &gt; 2k.
	 * @throws URIException
	 * @see <a href="http://sourceforge.net/tracker/index.php?func=detail&aid=1012520&group_id=73833&atid=539099">[ 1012520 ] UURI.length() &gt; 2k</a>
	 */
	public final void test2kURI() throws URIException {
		final StringBuffer buffer = new StringBuffer("http://a.b");
		final String subPath = "/123456789";
		for (int i = 0; i < 207; i++) {
			buffer.append(subPath);
		}
		// String should be 2080 characters long.  Legal.
		UURIFactory.getInstance(buffer.toString());
		boolean gotException = false;
		// Add ten more characters and make size illegal.
		buffer.append(subPath);
		try {
			UURIFactory.getInstance(buffer.toString()); 
		} catch (URIException e) {
			gotException = true;
		}
		assertTrue("No expected exception complaining about long URI",
				gotException);
	} 
	
	private void checkExceptionOnIllegalDomainlabel(String uuri) {
		boolean expectedException = false;
        try {
			UURIFactory.getInstance(uuri);
		} catch (URIException e) {
			// Expected exception.
			expectedException = true;
		}
		assertTrue("Didn't get expected exception: " + uuri, 
				expectedException); 
	}
	
	/**
	 * Test for doing separate DNS lookup for same host
	 *
	 * @see <a href="https://sourceforge.net/tracker/?func=detail&aid=788277&group_id=73833&atid=539099">[ 788277 ] Doing separate DNS lookup for same host</a>
	 * @throws URIException
	 */
	public final void testHostWithPeriod() throws URIException {
		UURI uuri1 = UURIFactory.
		getInstance("http://www.loc.gov./index.html");
		UURI uuri2 = UURIFactory.
		getInstance("http://www.loc.gov/index.html");
		assertEquals("Failed equating hosts with dot",
				uuri1.getHost(), uuri2.getHost());
	}
	
	/**
	 * Test for NPE in java.net.URI.encode
	 *
	 * @see <a href="https://sourceforge.net/tracker/?func=detail&aid=874220&group_id=73833&atid=539099">[ 874220 ] NPE in java.net.URI.encode</a>
	 * @throws URIException
	 */
	public final void testHostEncodedChars() throws URIException {
		String s = "http://g.msn.co.kr/0nwkokr0/00/19??" +
		"PS=10274&NC=10009&CE=42&CP=949&HL=" +
		"&#65533;&#65533;&#65533;?&#65533;&#65533;";
		assertNotNull("Encoded chars " + s, 
				UURIFactory.getInstance(s));
	}
	
	/**
	 * Test for java.net.URI parses %20 but getHost null
	 *
	 * See <a href="https://sourceforge.net/tracker/?func=detail&aid=927940&group_id=73833&atid=539099">[ 927940 ] java.net.URI parses %20 but getHost null</a>
	 */
	public final void testSpaceInHost() {
		boolean expectedException = false;
		try {
			UURIFactory.getInstance(
					"http://www.local-regions.odpm%20.gov.uk" +
			"/lpsa/challenge/pdf/propect.pdf");
		} catch (URIException e) {
			expectedException = true;
		}
		assertTrue("Did not fail with escaped space.", expectedException);
		
		expectedException = false;
		try {
			UURIFactory.getInstance(
					"http://www.local-regions.odpm .gov.uk" +
			"/lpsa/challenge/pdf/propect.pdf");
		} catch (URIException e) {
			expectedException = true;
		}
		assertTrue("Did not fail with real space.", expectedException);
	}
	
	/**
	 * Test for java.net.URI chokes on hosts_with_underscores.
	 *
	 * @see  <a href="https://sourceforge.net/tracker/?func=detail&aid=808270&group_id=73833&atid=539099">[ 808270 ] java.net.URI chokes on hosts_with_underscores</a>
	 * @throws URIException
	 */
	public final void testHostWithUnderscores() throws URIException {
		UURI uuri = UURIFactory.getInstance(
		"http://x_underscore_underscore.2u.com.tw/nonexistent_page.html");
		assertEquals("Failed get of host with underscore",
				"x_underscore_underscore.2u.com.tw", uuri.getHost());
	}
	
	
	/**
	 * Two dots for igor.
	 */
	public final void testTwoDots() {
		boolean expectedException = false;
		try {
			UURIFactory.getInstance(
			"http://x_underscore_underscore..2u.com/nonexistent_page.html");
		} catch (URIException e) {
			expectedException = true;
		}
		assertTrue("Two dots did not throw exception", expectedException);
	}
	
	/**
	 * Test for java.net.URI#getHost fails when leading digit.
	 *
	 * @see <a href="https://sourceforge.net/tracker/?func=detail&aid=910120&group_id=73833&atid=539099">[ 910120 ] java.net.URI#getHost fails when leading digit.</a>
	 * @throws URIException
	 */
	public final void testHostWithDigit() throws URIException {
		UURI uuri = UURIFactory.
		getInstance("http://0204chat.2u.com.tw/nonexistent_page.html");
		assertEquals("Failed get of host with digit",
				"0204chat.2u.com.tw", uuri.getHost());
	}
	
	/**
	 * Test for Constraining java URI class.
	 *
	 * @see <a href="https://sourceforge.net/tracker/?func=detail&aid=949548&group_id=73833&atid=539099">[ 949548 ] Constraining java URI class</a>
	 */
	public final void testPort() {
		checkBadPort("http://www.tyopaikat.com:a/robots.txt");
		checkBadPort("http://158.144.21.3:80808/robots.txt");
		checkBadPort("http://pdb.rutgers.edu:81.rutgers.edu/robots.txt");
		checkBadPort(
		    "https://webmail.gse.harvard.edu:9100robots.txt/robots.txt");
		checkBadPort(
		    "https://webmail.gse.harvard.edu:0/robots.txt/robots.txt");
	}
	
	/**
	 * Test bad port throws exception.
	 * @param uri URI with bad port to check.
	 */
	private void checkBadPort(String uri) {
		boolean exception = false;
		try {
			UURIFactory.getInstance(uri);
		}
		catch (URIException e) {
			exception = true;
		}
		assertTrue("Didn't throw exception: " + uri, exception);
	}
	
	/**
	 * Preserve userinfo capitalization.
	 * @throws URIException
	 */
	public final void testUserinfo() throws URIException {
        final String authority = "stack:StAcK@www.tyopaikat.com";
        final String uri = "http://" + authority + "/robots.txt";
		UURI uuri = UURIFactory.getInstance(uri);
		assertEquals("Authority not equal", uuri.getAuthority(),
            authority);
        /*
        String tmp = uuri.toString();
        assertTrue("URI not equal", tmp.equals(uri));
        */
	}

	/**
	 * Test user info + port
	 * @throws URIException
	 */
	public final void testUserinfoPlusPort() throws URIException {
		final String userInfo = "stack:StAcK";
        final String authority = "www.tyopaikat.com";
        final int port = 8080;
        final String uri = "http://" + userInfo + "@" + authority + ":" + port 
        	+ "/robots.txt";
		UURI uuri = UURIFactory.getInstance(uri);
		assertEquals("Host not equal", authority,uuri.getHost());
		assertEquals("Userinfo Not equal",userInfo,uuri.getUserinfo());
		assertEquals("Port not equal",port,uuri.getPort());
		assertEquals("Authority wrong","stack:StAcK@www.tyopaikat.com:8080",
				uuri.getAuthority());
		assertEquals("AuthorityMinusUserinfo wrong","www.tyopaikat.com:8080",
				uuri.getAuthorityMinusUserinfo());
		
	}
	
    public final void testRFC3986RelativeChange() throws URIException {
         UURI base = UURIFactory.getInstance("http://a/b/c/d;p?q");
         tryRelative(base, "?y", "http://a/b/c/d;p?y");
    }
	        
    /**
     * Tests from rfc3986
     *
     * <pre>
     *       "g:h"           =  "g:h"
     *       "g"             =  "http://a/b/c/g"
     *       "./g"           =  "http://a/b/c/g"
     *       "g/"            =  "http://a/b/c/g/"
     *       "/g"            =  "http://a/g"
     *       "//g"           =  "http://g"
     *       "?y"            =  "http://a/b/c/d;p?y"
     *       "g?y"           =  "http://a/b/c/g?y"
     *       "#s"            =  "http://a/b/c/d;p?q#s"
     *       "g#s"           =  "http://a/b/c/g#s"
     *       "g?y#s"         =  "http://a/b/c/g?y#s"
     *       ";x"            =  "http://a/b/c/;x"
     *       "g;x"           =  "http://a/b/c/g;x"
     *       "g;x?y#s"       =  "http://a/b/c/g;x?y#s"
     *       ""              =  "http://a/b/c/d;p?q"
     *       "."             =  "http://a/b/c/"
     *       "./"            =  "http://a/b/c/"
     *       ".."            =  "http://a/b/"
     *       "../"           =  "http://a/b/"
     *       "../g"          =  "http://a/b/g"
     *       "../.."         =  "http://a/"
     *       "../../"        =  "http://a/"
     *       "../../g"       =  "http://a/g"
     * </pre>
     *
     * @throws URIException
     */
    public final void testRFC3986Relative() throws URIException {
        UURI base = UURIFactory.getInstance("http://a/b/c/d;p?q");
        tryRelative(base, "g:h",    "g:h");
        tryRelative(base, "g",      "http://a/b/c/g");
        tryRelative(base, "./g",    "http://a/b/c/g");
        tryRelative(base, "g/",     "http://a/b/c/g/");
        tryRelative(base, "/g",     "http://a/g");
        tryRelative(base, "//g",    "http://g");
        tryRelative(base, "?y",     "http://a/b/c/d;p?y");
        tryRelative(base, "g?y",    "http://a/b/c/g?y");
        tryRelative(base, "#s",     "http://a/b/c/d;p?q#s");
        tryRelative(base, "g#s",    "http://a/b/c/g#s");
        tryRelative(base, "g?y#s",  "http://a/b/c/g?y#s");
        tryRelative(base, ";x",     "http://a/b/c/;x");
        tryRelative(base, "g;x",    "http://a/b/c/g;x");
        tryRelative(base, "g;x?y#s","http://a/b/c/g;x?y#s");
        tryRelative(base, "",       "http://a/b/c/d;p?q");
        tryRelative(base, ".",      "http://a/b/c/");
        tryRelative(base, "./",     "http://a/b/c/");
        tryRelative(base, "..",     "http://a/b/");
        tryRelative(base, "../",    "http://a/b/");
        tryRelative(base, "../g",   "http://a/b/g");
        tryRelative(base, "../..",  "http://a/");
        tryRelative(base, "../../", "http://a/");
        tryRelative(base, "../../g","http://a/g");
    }
	    
    protected void tryRelative(UURI base, String relative, String expected) 
    throws URIException {
        UURI uuri = UURIFactory.getInstance(base, relative);
        assertEquals("Derelativized " + relative + " gave " 
              + uuri + " not " + expected,
	                UURIFactory.getInstance(expected),uuri);
    }
	
	/**
	 * Tests from rfc2396 with amendments to accomodate differences
	 * intentionally added to make our URI handling like IEs.
	 *
	 * <pre>
	 *       g:h           =  g:h
	 *       g             =  http://a/b/c/g
	 *       ./g           =  http://a/b/c/g
	 *       g/            =  http://a/b/c/g/
	 *       /g            =  http://a/g
	 *       //g           =  http://g
	 *       ?y            =  http://a/b/c/?y
	 *       g?y           =  http://a/b/c/g?y
	 *       #s            =  (current document)#s
	 *       g#s           =  http://a/b/c/g#s
	 *       g?y#s         =  http://a/b/c/g?y#s
	 *       ;x            =  http://a/b/c/;x
	 *       g;x           =  http://a/b/c/g;x
	 *       g;x?y#s       =  http://a/b/c/g;x?y#s
	 *       .             =  http://a/b/c/
	 *       ./            =  http://a/b/c/
	 *       ..            =  http://a/b/
	 *       ../           =  http://a/b/
	 *       ../g          =  http://a/b/g
	 *       ../..         =  http://a/
	 *       ../../        =  http://a/
	 *       ../../g       =  http://a/g
	 * </pre>
	 *
	 * @throws URIException
	 */
	public final void testRFC2396Relative() throws URIException {
		UURI base = UURIFactory.
		getInstance("http://a/b/c/d;p?q");
		TreeMap<String,String> m = new TreeMap<String,String>();
		m.put("..", "http://a/b/");
		m.put("../", "http://a/b/");
		m.put("../g", "http://a/b/g");
		m.put("../..", "http://a/");
		m.put("../../", "http://a/");
		m.put("../../g", "http://a/g");
		m.put("g#s", "http://a/b/c/g#s");
		m.put("g?y#s ", "http://a/b/c/g?y#s");
		m.put(";x", "http://a/b/c/;x");
		m.put("g;x", "http://a/b/c/g;x");
		m.put("g;x?y#s", "http://a/b/c/g;x?y#s");
		m.put(".", "http://a/b/c/");
		m.put("./", "http://a/b/c/");
		m.put("g", "http://a/b/c/g");
		m.put("./g", "http://a/b/c/g");
		m.put("g/", "http://a/b/c/g/");
		m.put("/g", "http://a/g");
		m.put("//g", "http://g");
	   // CHANGED BY RFC3986
                // m.put("?y", "http://a/b/c/?y");
		m.put("g?y", "http://a/b/c/g?y");
		// EXTRAS beyond the RFC set.
		// TODO: That these resolve to a path of /a/g might be wrong.  Perhaps
		// it should be '/g'?.
		m.put("/../../../../../../../../g", "http://a/g");
		m.put("../../../../../../../../g", "http://a/g");
		m.put("../G", "http://a/b/G");
		for (Iterator<String> i = m.keySet().iterator(); i.hasNext();) {
			String key = (String)i.next();
			String value = (String)m.get(key);
			UURI uuri = UURIFactory.getInstance(base, key);
			assertTrue("Unexpected " + key + " " + value + " " + uuri,
					uuri.equals(UURIFactory.getInstance(value)));
		}
	}
	
	/**
	 * A UURI should always be without a 'fragment' segment, which is
	 * unused and irrelevant for network fetches. 
	 *  
	 * See [ 970666 ] #anchor links not trimmed, and thus recrawled 
	 * 
	 * @throws URIException
	 */
	public final void testAnchors() throws URIException {
		UURI uuri = UURIFactory.
		getInstance("http://www.example.com/path?query#anchor");
		assertEquals("Not equal", "http://www.example.com/path?query",
				uuri.toString());
	}
    

    /**
     * Ensure that URI strings beginning with a colon are treated
     * the same as browsers do (as relative, rather than as absolute
     * with zero-length scheme). 
     * 
     * @throws URIException
     */
    public void testStartsWithColon() throws URIException {
        UURI base = UURIFactory.getInstance("http://www.example.com/path/page");
        UURI uuri = UURIFactory.getInstance(base,":foo");
        assertEquals("derelativize starsWithColon",
                uuri.getURI(),
                "http://www.example.com/path/:foo");
    }
    
    /**
     * Ensure that stray trailing '%' characters do not prevent
     * UURI instances from being created, and are reasonably 
     * escaped when encountered. 
     *
     * @throws URIException
     */
    public void testTrailingPercents() throws URIException {
        String plainPath = "http://www.example.com/path%";
        UURI plainPathUuri = UURIFactory.getInstance(plainPath);
        assertEquals("plainPath getURI", plainPath, plainPathUuri.getURI());
        assertEquals("plainPath getEscapedURI", 
                "http://www.example.com/path%", // browsers don't escape '%'
                plainPathUuri.getEscapedURI());
        
        String partiallyEscapedPath = "http://www.example.com/pa%20th%";
        UURI partiallyEscapedPathUuri = UURIFactory.getInstance(
                partiallyEscapedPath);
//        assertEquals("partiallyEscapedPath getURI", 
//                "http://www.example.com/pa th%", // TODO: is this desirable?
////              partiallyEscapedPath,
//                partiallyEscapedPathUuri.getURI());
        assertEquals("partiallyEscapedPath getEscapedURI", 
                "http://www.example.com/pa%20th%",
                partiallyEscapedPathUuri.getEscapedURI());
        
        String plainQueryString = "http://www.example.com/path?q=foo%";
        UURI plainQueryStringUuri = UURIFactory.getInstance(
                plainQueryString);
//        assertEquals("plainQueryString getURI", 
//                plainQueryString,
//                plainQueryStringUuri.getURI());
        assertEquals("plainQueryString getEscapedURI", 
                "http://www.example.com/path?q=foo%",
                plainQueryStringUuri.getEscapedURI());        
        
        String partiallyEscapedQueryString = 
            "http://www.example.com/pa%20th?q=foo%";
        UURI partiallyEscapedQueryStringUuri = UURIFactory.getInstance(
                partiallyEscapedQueryString);
        assertEquals("partiallyEscapedQueryString getURI", 
                "http://www.example.com/pa th?q=foo%",
                partiallyEscapedQueryStringUuri.getURI());
        assertEquals("partiallyEscapedQueryString getEscapedURI", 
                "http://www.example.com/pa%20th?q=foo%",
                partiallyEscapedQueryStringUuri.getEscapedURI());  
    }
    
    /**
     * Ensure that stray '%' characters do not prevent
     * UURI instances from being created, and are reasonably 
     * escaped when encountered. 
     *
     * @throws URIException
     */
    public void testStrayPercents() throws URIException {
        String oneStray = "http://www.example.com/pa%th";
        UURI oneStrayUuri = UURIFactory.getInstance(oneStray);
        assertEquals("oneStray getURI", oneStray, oneStrayUuri.getURI());
        assertEquals("oneStray getEscapedURI", 
                "http://www.example.com/pa%th", // browsers don't escape '%'
                oneStrayUuri.getEscapedURI());
        
        String precededByValidEscape = "http://www.example.com/pa%20th%way";
        UURI precededByValidEscapeUuri = UURIFactory.getInstance(
                precededByValidEscape);
        assertEquals("precededByValidEscape getURI", 
                "http://www.example.com/pa th%way", // getURI interprets escapes
                precededByValidEscapeUuri.getURI());
        assertEquals("precededByValidEscape getEscapedURI", 
                "http://www.example.com/pa%20th%way",
                precededByValidEscapeUuri.getEscapedURI());
        
        String followedByValidEscape = "http://www.example.com/pa%th%20way";
        UURI followedByValidEscapeUuri = UURIFactory.getInstance(
                followedByValidEscape);
        assertEquals("followedByValidEscape getURI", 
                "http://www.example.com/pa%th way", // getURI interprets escapes
                followedByValidEscapeUuri.getURI());
        assertEquals("followedByValidEscape getEscapedURI", 
                "http://www.example.com/pa%th%20way",
                followedByValidEscapeUuri.getEscapedURI());        
    }
    
    public void testEscapingNotNecessary() throws URIException {
        String escapesUnnecessary = 
            "http://www.example.com/misc;reserved:chars@that&don't=need"
            +"+escaping$even,though!you(might)initially?think#so";
        // expect everything but the #fragment
        String expected = escapesUnnecessary.substring(0, escapesUnnecessary
                .length() - 3);
        assertEquals("escapes unnecessary", 
                expected, 
                UURIFactory.getInstance(escapesUnnecessary).toString());
    }
    
    public void testIdn() throws URIException {
        // See http://www.josefsson.org/idn.php.
        // http://räksmörgås.josefßon.org/
        String idn1 = "http://r\u00e4ksm\u00f6rg\u00e5s.josef\u00dfon.org/";
        String puny1 = "http://xn--rksmrgs-5wao1o.josefsson.org/";
        assertEquals("encoding of " + idn1, puny1, UURIFactory
                .getInstance(idn1).toString());
        // http://www.pølse.dk/
        String idn2 = "http://www.p\u00f8lse.dk/";
        String puny2 = "http://www.xn--plse-gra.dk/";
        assertEquals("encoding of " + idn2, puny2, UURIFactory
                .getInstance(idn2).toString());
    }
    
    public void testNewLineInURL() throws URIException {
    	UURI uuri = UURIFactory.getInstance("http://www.ar\rchive\n." +
    	    "org/i\n\n\r\rndex.html");
    	assertEquals("http://www.archive.org/index.html", uuri.toString());
    }
    
    public void testTabsInURL() throws URIException {
        UURI uuri = UURIFactory.getInstance("http://www.ar\tchive\t." +
            "org/i\t\r\n\tndex.html");
        assertEquals("http://www.archive.org/index.html", uuri.toString());
    }
    
    public void testQueryEscaping() throws URIException {
        UURI uuri = UURIFactory.getInstance(
            "http://www.yahoo.com/foo?somechars!@$%^&*()_-+={[}]|\'\";:/?.>,<");
        assertEquals(
            // tests in FF1.5 indicate it only escapes " < > 
            "http://www.yahoo.com/foo?somechars!@$%^&*()_-+={[}]|\'%22;:/?.%3E,%3C",
            uuri.toString());
    }
    
    /**
     * Check that our 'normalization' does same as Nutch's
     * Below before-and-afters were taken from the nutch urlnormalizer-basic
     * TestBasicURLNormalizer class  (December 2006, Nutch 0.9-dev).
     * @throws URIException
     */
    public void testSameAsNutchURLFilterBasic() throws URIException {
        assertEquals(UURIFactory.getInstance(" http://foo.com/ ").toString(),
            "http://foo.com/");

        // check that protocol is lower cased
        assertEquals(UURIFactory.getInstance("HTTP://foo.com/").toString(),
            "http://foo.com/");
        
        // check that host is lower cased
        assertEquals(UURIFactory.
                getInstance("http://Foo.Com/index.html").toString(),
            "http://foo.com/index.html");
        assertEquals(UURIFactory.
                getInstance("http://Foo.Com/index.html").toString(),
            "http://foo.com/index.html");

        // check that port number is normalized
        assertEquals(UURIFactory.
                getInstance("http://foo.com:80/index.html").toString(),
            "http://foo.com/index.html");
        assertEquals(UURIFactory.getInstance("http://foo.com:81/").toString(),
            "http://foo.com:81/");

        // check that null path is normalized
        assertEquals(UURIFactory.getInstance("http://foo.com").toString(),
            "http://foo.com/");

        // check that references are removed
        assertEquals(UURIFactory.
                getInstance("http://foo.com/foo.html#ref").toString(),
            "http://foo.com/foo.html");

        //     // check that encoding is normalized
        //     normalizeTest("http://foo.com/%66oo.html", "http://foo.com/foo.html");

        // check that unnecessary "../" are removed
        assertEquals(UURIFactory.
                getInstance("http://foo.com/aa/../").toString(),
            "http://foo.com/" );
        assertEquals(UURIFactory.
                getInstance("http://foo.com/aa/bb/../").toString(),
            "http://foo.com/aa/");

        /* We fail this one.  Here we produce: 'http://foo.com/'.
        assertEquals(UURIFactory.
                getInstance("http://foo.com/aa/..").toString(),
            "http://foo.com/aa/..");
         */
        
        assertEquals(UURIFactory.
            getInstance("http://foo.com/aa/bb/cc/../../foo.html").toString(),
                "http://foo.com/aa/foo.html");
        assertEquals(UURIFactory.
            getInstance("http://foo.com/aa/bb/../cc/dd/../ee/foo.html").
                toString(),
                    "http://foo.com/aa/cc/ee/foo.html");
        assertEquals(UURIFactory.
            getInstance("http://foo.com/../foo.html").toString(),
                "http://foo.com/foo.html" );
        assertEquals(UURIFactory.
            getInstance("http://foo.com/../../foo.html").toString(),
                "http://foo.com/foo.html" );
        assertEquals(UURIFactory.
            getInstance("http://foo.com/../aa/../foo.html").toString(),
                "http://foo.com/foo.html" );
        assertEquals(UURIFactory.
            getInstance("http://foo.com/aa/../../foo.html").toString(),
                "http://foo.com/foo.html" );
        assertEquals(UURIFactory.
                getInstance("http://foo.com/aa/../bb/../foo.html/../../").
                    toString(),
            "http://foo.com/" );
        assertEquals(UURIFactory.getInstance("http://foo.com/../aa/foo.html").
            toString(), "http://foo.com/aa/foo.html" );
        assertEquals(UURIFactory.
                getInstance("http://foo.com/../aa/../foo.html").toString(),
            "http://foo.com/foo.html" );
        assertEquals(UURIFactory.
                getInstance("http://foo.com/a..a/foo.html").toString(),
            "http://foo.com/a..a/foo.html" );
        assertEquals(UURIFactory.
                getInstance("http://foo.com/a..a/../foo.html").toString(),
            "http://foo.com/foo.html" );
        assertEquals(UURIFactory.
            getInstance("http://foo.com/foo.foo/../foo.html").toString(),
                 "http://foo.com/foo.html" );
    }
    
    public void testHttpSchemeColonSlash() {
    	boolean exception = false;
    	try {
    		UURIFactory.getInstance("https:/");
    	} catch (URIException e) {
    		exception = true;
    	}
    	assertTrue("Didn't throw exception when one expected", exception);
    	exception = false;
    	try {
    		UURIFactory.getInstance("http://");
    	} catch (URIException e) {
    		exception = true;
    	}
    	assertTrue("Didn't throw exception when one expected", exception);
    }
    
    public void testNakedHttpsSchemeColon() {
        boolean exception = false;
        try {
            UURIFactory.getInstance("https:");
        } catch (URIException e) {
            exception = true;
        }
        assertTrue("Didn't throw exception when one expected", exception);
        exception = false;
        try {
            UURI base = UURIFactory.getInstance("http://www.example.com");
            UURIFactory.getInstance(base, "https:");
        } catch (URIException e) {
            exception = true;
        }
        assertTrue("Didn't throw exception when one expected", exception);
    }
    
    /**
     * Test motivated by [#HER-616] The UURI class may throw 
     * NullPointerException in getReferencedHost()
     * 
     * @throws URIException
     */
    public void testMissingHttpColon() throws URIException {
        String suspectUri = "http//www.test.foo";
        UURI base = UURIFactory.getInstance("http://www.example.com");
        boolean exceptionThrown = false; 
        try {
            UURI badUuri = UURIFactory.getInstance(suspectUri);
            badUuri.getReferencedHost(); // not reached
        } catch (URIException e) {
            // should get relative-uri-no-base exception
            exceptionThrown = true;
        } finally {
            assertTrue("expected exception not thrown",exceptionThrown);
        }
        UURI goodUuri = UURIFactory.getInstance(base,suspectUri);
        goodUuri.getReferencedHost();
    }
    
    /**
     * A UURI's string representation should be same after a 
     * serialization roundtrip. 
     *  
     * @throws URIException
     */
    public final void testSerializationRoundtrip() throws URIException {
        UURI uuri = UURIFactory.
            getInstance("http://www.example.com/path?query#anchor");
        UURI uuri2 = (UURI) SerializationUtils.deserialize(
                SerializationUtils.serialize(uuri));
        assertEquals("Not equal", uuri.toString(), uuri2.toString());
        uuri = UURIFactory.
            getInstance("file://///boo_hoo/wwwroot/CMS/Images1/Banner.gif");
        uuri2 = (UURI) SerializationUtils.deserialize(
            SerializationUtils.serialize(uuri));
        assertEquals("Not equal", uuri.toString(), uuri2.toString());
    }
    
    /**
     * A UURI's string representation should be same after a 
     * toCustomString-getInstance roundtrip. 
     *  
     * @throws URIException
     */
    public final void testToCustomStringRoundtrip() throws URIException {
        UURI uuri = UURIFactory.
            getInstance("http://www.example.com/path?query#anchor");
        UURI uuri2 = UURIFactory.getInstance(uuri.toCustomString());
        assertEquals("Not equal", uuri.toString(), uuri2.toString());
        // TODO: fix
        // see [HER-1470] UURI String roundtrip (UURIFactory.getInstance(uuri.toString()) results in different URI for file: (and perhaps other) URIs
        // http://webteam.archive.org/jira/browse/HER-1470
//        uuri = UURIFactory.
//            getInstance("file://///boo_hoo/wwwroot/CMS/Images1/Banner.gif");
//        uuri2 = UURIFactory.getInstance(uuri.toCustomString());
//        assertEquals("Not equal", uuri.toString(), uuri2.toString());
    }
}
