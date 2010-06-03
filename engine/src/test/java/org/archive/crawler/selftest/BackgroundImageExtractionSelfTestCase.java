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
package org.archive.crawler.selftest;


import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;


/**
 * Test the crawler can find background images in pages.
 *
 * @author stack
 * @version $Id$
 */
public class BackgroundImageExtractionSelfTestCase
    extends SelfTestBase
{


    /**
     * Files to find as a set.
     */
    final private static Set<String> EXPECTED = Collections.unmodifiableSet(
            new HashSet<String>(Arrays.asList(new String[] {
            "index.html", "example-background-image.jpeg", "robots.txt", 
            "favicon.ico"
    })));
    
    @Override
    protected void verify() throws Exception {
        Set<String> files = filesInArcs();
        assertTrue(EXPECTED.equals(files));
    }
    
    // TODO TESTME
    
    
//    /**
//     * The name of the background image the crawler is supposed to find.
//     */
//    private static final String IMAGE_NAME = "example-background-image.jpeg";
//
//    private static final String JPEG = "image/jpeg";
//
//
//    /**
//     * Read ARC file for the background image the file that contained it.
//     *
//     * Look that there is only one instance of the background image in the
//     * ARC and that it is of the same size as the image in the webapp dir.
//     */
//    public void testBackgroundImageExtraction()
//    {
//        String relativePath = getTestName() + '/' + IMAGE_NAME;
//        String url = getSelftestURLWithTrailingSlash() + relativePath;
//        File image = new File(getHtdocs(), relativePath);
//        assertTrue("Image exists", image.exists());
//        List [] metaDatas = getMetaDatas();
//        boolean found = false;
//        ARCRecordMetaData metaData = null;
//        for (int mi = 0; mi < metaDatas.length; mi++) {
//			List list = metaDatas[mi];
//			for (final Iterator i = list.iterator(); i.hasNext();) {
//				metaData = (ARCRecordMetaData) i.next();
//				if (metaData.getUrl().equals(url)
//						&& metaData.getMimetype().equalsIgnoreCase(JPEG)) {
//					if (!found) {
//						found = true;
//					} else {
//						fail("Found a 2nd instance of " + url);
//					}
//				}
//			}
//		}
//    }
}