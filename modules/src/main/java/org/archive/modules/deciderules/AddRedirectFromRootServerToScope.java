/* AddRedirectFromRootServerToScope
 * 
 * Created on May 25, 2005
 *
 * Copyright (C) 2005 Internet Archive.
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
package org.archive.modules.deciderules;

import java.util.logging.Logger;
import org.apache.commons.httpclient.URIException;
import org.archive.modules.ProcessorURI;
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
    protected boolean evaluate(ProcessorURI uri) {
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
