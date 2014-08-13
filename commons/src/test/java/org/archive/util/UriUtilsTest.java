/* UriUtilsTest
 *
 * $Id: ArchiveUtilsTest.java 5052 2007-04-10 02:26:52Z gojomo $
 *
 * Copyright (C) 2010 Internet Archive.
 *
 * This file is part of the Heritrix web crawler (crawler.archive.org).
 *
 * Heritrix is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser Public License as published by
 * the Free Software Foundation; either version 2.1 of the License, or
 * any later version.
 *
 * Heritrix is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser Public License for more details.
 *
 * You should have received a copy of the GNU Lesser Public License
 * along with Heritrix; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package org.archive.util;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

/**
 * JUnit test suite for UriUtils. 
 * 
 * Several of the tests for the 'legacy' (H1 through at least 1.14.4) 
 * heuristics are disabled by renaming, because those heuristics have known 
 * failures; however, until more experience with the new heuristics is 
 * collected, H1 still uses them for consistency. 
 * 
 * @contributor gojomo
 * @version $Id: ArchiveUtilsTest.java 5052 2007-04-10 02:26:52Z gojomo $
 */
public class UriUtilsTest extends TestCase {

    public UriUtilsTest(final String testName) {
        super(testName);
    }

    /**
     * run all the tests for ArchiveUtilsTest
     * 
     * @param argv
     *            the command line arguments
     */
    public static void main(String argv[]) {
        junit.textui.TestRunner.run(suite());
    }

    public static Test suite() {
        return new TestSuite(UriUtilsTest.class);
    }

    /** image URIs that should be considered likely URIs **/
    static String[] urisRelativeImages = { 
        "photo.jpg", 
        "./photo.jpg",
        "../photo.jpg", 
        "images/photo.jpg", 
        "../../images/photo.jpg" };

    /** check that plausible relative image URIs return true with legacy tests */
    public void xestLegacySimpleImageRelatives() {
        legacyTryAll(urisRelativeImages, true);
    }
    
    /** check that plausible relative image URIs return true with new tests */
    public void testNewSimpleImageRelatives() {
        tryAll(urisRelativeImages,true); 
    }

    /** absolute URIs that should be considered likely URIs **/
    static String[] urisAbsolute = { 
        "http://example.com",
        "http://example.com/", "http://www.example.com",
        "http://www.example.com/", "http://www.example.com/about",
        "http://www.example.com/about/",
        "http://www.example.com/about/index.html", "https://example.com",
        "https://example.com/", "https://www.example.com",
        "https://www.example.com/", "https://www.example.com/about",
        "https://www.example.com/about/",
        "https://www.example.com/about/index.html",
        "ftp://example.com/public/report.pdf",
        "http://a.example.com/combiner/c?js=analytics/sOmni.js,analytics/analytics.js,analytics/zf.js,analytics/externalnielsen.js",
        "http://l.example.com/jn/util/anysize/74*74c-86400,http%3A%2F%2Fl.example.com%2Fa%2Fi%2Fus%2Fshine%2Fmoreon%2F74.upallnight.jpg",
        // TODO: other schemes? mailto?
    };

    /** check that absolute URIs return true with legacy tests */
    public void testLegacyAbsolutes() {
        legacyTryAll(urisAbsolute,true);
    }
    
    /** check that absolute URIs return true with new tests */
    public void testAbsolutes() {
        tryAll(urisAbsolute,true);
    }
    
    protected static String[] urisRelative = new String[] {
        "default.asp?type=1",
        "\\/add\\/page?.crumb=O2.eArRHJUUWRkVHN6L0Y.&frompg=p1",
        "/wiki/Ficheiro:Wikiversity-logo.svg",
        "cssp!gelui-1/overlay",
        "/wiki/%E0%B4%B8%E0%B4%B9%E0%B4%BE%E0%B4%AF%E0%B4%82:To_Read_in_Malayalam",
        "/wiki/Wikiversity:Why_create_an_account%3F",
    };
    public void testRelatives() {
        tryAll(urisRelative, true);
    }

    /** path-absolute images URIs that should be considered likely URIs **/
    static String[] urisPathAbsoluteImages = { 
        "/photo.jpg", 
        "/images/photo.jpg", 
    };
    
    /** check that path-absolute image URIs return true with legacy tests*/
    public void testLegacySimpleImagePathAbsolutes() {
        legacyTryAll(urisPathAbsoluteImages, true); 
    }
    
    /** check that path-absolute image URIs return true with new tests*/
    public void testSimpleImagePathAbsolutes() {
        tryAll(urisPathAbsoluteImages, true); 
    }
    
    /** URI-like strings risking false positives that should NOT be likely URIs **/
    static String[] notUrisNaiveFalsePositives = {
        "0.99",
        "3.14157",
        "text/javascript"
    };
    
    /** check that typical false-positives of the naive test are not deemed URIs */
    public void xestLegacyNaiveFalsePositives() {
        legacyTryAll(notUrisNaiveFalsePositives, false); 
    }
    
    /** check that typical false-positives of the naive test are not deemed URIs */
    public void testNaiveFalsePositives() {
        tryAll(notUrisNaiveFalsePositives, false); 
    }
    
    /** strings that should not be considered likely URIs **/
    static String[] notUrisNaive = {
        "foo bar",
        "<script>foo=bar</script>",
        "item\t$0.99\tred",
    };
    
    /** check that strings that fail naive test are not deemed URIs legacy tests*/
    public void testLegacyNaiveNotUris() {
        legacyTryAll(notUrisNaive, false); 
    }
    
    /** check that strings that fail naive test are not deemed URIs new tests*/
    public void testNaiveNotUris() {
        tryAll(notUrisNaive, false); 
    }

    protected static final String[] unusualCharacterFalsePositives = new String[] {
        "),f=document.getElementsByTagName(",
        "window.location.href='/'",
        "location='http://example.com/blah/'",
        "http://example.com/intent/user?screen_name='+p.user+'",
        ").append(",
        "[\\x3cb\\x3e−\\x3c/b\\x3e]",
        "http://demo.example.net/panama.php?cgroup=ron728x90&pid=\"+pid+\"&uid=\"+uid+\"&rid=\"+rid+\"&kw=10&cx=10&bh=10",
    };
    public void testUnusualCharacterFalsePositives() {
        tryAll(unusualCharacterFalsePositives, false);
    }
    
    protected static final String[] mimetypesFalsePositives = new String[] {
        "text/javascript",
        "text/css", 
        "application/x-shockwave-flash", 
        "text/javaScript", 
        "text/html", 
        "application/x-www-form-urlencoded", 
        "text/xml", 
        "text/plain", 
        "application/x-mplayer2", 
        "application/json", 
        "image/jpeg", 
        "image/x-icon", 
        "audio/mpeg", 
        "image/gif", 
        "audio/ogg", 
        "video/quicktime", 
        "audio/x-pn-realaudio-plugin", 
    };
    public void testMimetypesFalsePositives() {
        tryAll(mimetypesFalsePositives, false);
    }

    protected static final String[] startsOrEndsWithPlusFalsePositives = new String[] {
        "+resp.result+",
        ";overlay.style.width=viewport_dimensions.width+",
        "+_ti;bb.src=",
    };
    public void testStartsOrEndsWithPlusFalsePositives() {
        tryAll(startsOrEndsWithPlusFalsePositives, false);
    }
    
    protected static final String[] doubleSlashFalsePositives = new String[] {
        ".//*",
        "http://example.com/monkey//foo/whatever"
    };
    public void testDoubleSlashFalsePositives() {
        tryAll(startsOrEndsWithPlusFalsePositives, false);
    }

    /**
     * Test that all supplied candidates give the expected result, for each of 
     * the 'legacy' (H1) likely-URI-tests
     * 
     * @param candidates String[] to test
     * @param expected desired answer
     */
    protected void legacyTryAll(String[] candidates, boolean expected) {
        for (String candidate : candidates) {
            assertEquals("javascript context: " + candidate, 
                    expected, 
                    UriUtils.isLikelyUriJavascriptContextLegacy(candidate));
            assertEquals("html context: " + candidate, 
                    expected, 
                    UriUtils.isLikelyUriHtmlContextLegacy(candidate));
        }
    }
    
    /**
     * Test that all supplied candidates give the expected results, for 
     * the 'new' heuristics now in this class. 
     * @param candidates String[] to test
     * @param expected desired answer
     */
    protected void tryAll(String[] candidates, boolean expected) {
        for (String candidate : candidates) {
            assertEquals(candidate, expected, UriUtils.isLikelyUri(candidate));
        }
    }
}
