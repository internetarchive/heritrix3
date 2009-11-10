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
package org.archive.modules.canonicalize;

/**
 * A rule to apply canonicalizing a url.
 * @author stack
 * @version $Date$, $Revision$
 */
public interface CanonicalizationRule {
    /**
     * Apply this canonicalization rule.
     * 
     * @param url Url string we apply this rule to.
     * @param context An object that will provide context for the settings
     * system.  The UURI of the URL we're canonicalizing is an example of
     * an object that provides context.
     * @return Result of applying this rule to passed <code>url</code>.
     */
    public String canonicalize(String url);

    /**
     * @return Name of this rule.
     */
//    public String getName();
    
    /**
     * @param context An object that will provide context for the settings
     * system.  The UURI of the URL we're canonicalizing is an example of
     * an object that provides context.
     * @return True if this rule is enabled and to be run.
     */
    public boolean getEnabled();
}
