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

import java.io.IOException;
import java.util.Vector;

import com.anotherbigidea.flash.interfaces.SWFActions;
import com.anotherbigidea.flash.writers.SWFTagTypesImpl;

/**
 * Overwrite action tags, that may hold URI, to use <code>CrawlUriSWFAction
 * <code> action.
 *
 * @author Igor Ranitovic
 */
@SuppressWarnings("unchecked")
public class CustomSWFTags extends SWFTagTypesImpl {
    SWFActions actions;

    public CustomSWFTags(SWFActions a) {
        super(null);
        actions = a;
    }

    public SWFActions tagDefineButton(int id, Vector buttonRecords)
            throws IOException {

        return actions;
    }

    public SWFActions tagDefineButton2(int id, boolean trackAsMenu,
            Vector buttonRecord2s) throws IOException {

        return actions;
    }

    public SWFActions tagDoAction() throws IOException {
        return actions;
    }
}
