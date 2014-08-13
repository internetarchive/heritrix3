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
 
package org.archive.crawler.spring;

import java.util.List;

import org.springframework.beans.factory.annotation.Required;

/**
 * SheetAssociation applied on the basis of matching SURT prefixes. 
 * 
 * @contributor gojomo
 */
public class SurtPrefixesSheetAssociation extends SheetAssociation {
    protected List<String> surtPrefixes;

    public List<String> getSurtPrefixes() {
        return surtPrefixes;
    }
    @Required
    public void setSurtPrefixes(List<String> surtPrefixes) {
        this.surtPrefixes = surtPrefixes;
    }
}
