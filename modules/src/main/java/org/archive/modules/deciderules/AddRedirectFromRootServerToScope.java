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
package org.archive.modules.deciderules;

import java.util.logging.Logger;
import org.apache.commons.httpclient.URIException;
import org.archive.modules.CrawlURI;
import org.archive.net.UURI;


public class AddRedirectFromRootServerToScope 
extends PredicatedDecideRule {

    private static final long serialVersionUID = 3L;

    private static final Logger LOGGER =
	        Logger.getLogger(AddRedirectFromRootServerToScope.class.getName());
    private static final String SLASH = "/";

    public AddRedirectFromRootServerToScope() {
    }

    @Override
    protected boolean evaluate(CrawlURI uri) {
        UURI via = uri.getVia();
        if (via == null) {
            return false;
        }
        try {
            String chost = uri.getUURI().getHostBasename();
            if (chost == null) {
                return false;
            }
            
            String viaHost = via.getHostBasename();
            if (viaHost == null) {
                return false;
            }
            
            if (chost.equals(viaHost) && uri.isLocation() 
                    && via.getPath().equals(SLASH)) {
                uri.setSeed(true);
                LOGGER.info("Adding " + uri + " to seeds via " + via);
                return true;
            }
        } catch (URIException e) {
            e.printStackTrace();
        }
        return false;
    }


}
