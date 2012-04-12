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

import org.archive.modules.CrawlURI;

/** 
 * Pseudo-extractor that suppresses link-extraction of likely trap pages,
 * by noticing when content's digest is identical to that of its 'via'. 
 *
 * @author gojomo
 *
 */
public class TrapSuppressExtractor extends ContentExtractor  {
    @SuppressWarnings("unused")
    private static final long serialVersionUID = -1028783453022579530L;

    /** ALIst attribute key for carrying-forward content-digest from 'via'*/
    public static String A_VIA_DIGEST = "via-digest";
    
    protected long numberOfCURIsHandled = 0;
    protected long numberOfCURIsSuppressed = 0;

    /**
     * Usual constructor. 
     * @param name
     */
    public TrapSuppressExtractor() {
    }

    protected boolean shouldExtract(CrawlURI uri) {
        return true; 
    }
    
    protected boolean innerExtract(CrawlURI curi){
        numberOfCURIsHandled++;

        String currentDigest = curi.getContentDigestSchemeString();
        String viaDigest = null;
        if(curi.containsDataKey(A_VIA_DIGEST)) {
            viaDigest = (String) curi.getData().get(A_VIA_DIGEST);
        }
        
        if(currentDigest!=null) {
            curi.makeHeritable(A_VIA_DIGEST);
            if(currentDigest.equals(viaDigest)) {
                curi.getAnnotations().add("trapSuppressExtractor");
                numberOfCURIsSuppressed++;
                // mark as already-extracted -- suppressing further extraction
                return true;
            }
            // already consulted; so clobber with current value to be 
            // inherited
            curi.getData().put(A_VIA_DIGEST, currentDigest);
        }
        return false; 
    }
}
