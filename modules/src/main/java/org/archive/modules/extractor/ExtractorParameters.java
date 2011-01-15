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
 
package org.archive.modules.extractor;

/**
 * Bean interface for parameters consulted by multiple Extractors, and
 * thus provided by some shared object. Because the original shared setting
 * was a cap on the number of discovered outlinks per page, this was
 * traditionally provided by the Frontier in crawls. As the number of settings
 * here grow, that may need to change. 
 * 
 * @contributor gojomo
 */
public interface ExtractorParameters {
    /**
     * The maximum number of outlinks to discover from any URI's content. 
     * Additional outlinks will be ignored, but the URI should be annotated as
     * having yielded more than the maximum. 
     * @return in maximum outlinks to discover 
     */
    public int getMaxOutlinks();
    /**
     * Whether each extractor should make an independent decision as to whether
     * it can extract links from a URI's content (when value is true), or 
     * whether a previous  extractor's success (marking the URI as 
     * hasBeenLinkExtracted) should cancel later extractors (when value is 
     * false). 
     * @return boolean whether to extract without regard to prior extractor's success
     */
    public boolean getExtractIndependently();
    /**
     * Whether to extract links from responses with a 404 'not found' response
     * code.
     */
    public boolean getExtract404s(); 
}
