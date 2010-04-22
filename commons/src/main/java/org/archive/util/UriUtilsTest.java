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
            assertEquals("new: " + candidate, 
                    expected, 
                    UriUtils.isLikelyUri(candidate));
            assertEquals("html context: " + candidate, 
                    expected, 
                    UriUtils.isLikelyUri(candidate));
        }
    }
}
