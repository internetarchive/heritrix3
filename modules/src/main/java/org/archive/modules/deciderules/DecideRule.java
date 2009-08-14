/* Copyright (C) 2006 Internet Archive.
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
 *
 * DecideRule.java
 * Created on October 5, 2006
 *
 * $Header$
 */
package org.archive.modules.deciderules;


import java.io.Serializable;

import org.archive.modules.ProcessorURI;
import org.archive.spring.HasKeyedProperties;
import org.archive.spring.KeyedProperties;

public abstract class DecideRule implements Serializable, HasKeyedProperties {
    protected KeyedProperties kp = new KeyedProperties();
    public KeyedProperties getKeyedProperties() {
        return kp;
    }
    
    {
        setEnabled(true);
    }
    public boolean getEnabled() {
        return (Boolean) kp.get("enabled");
    }
    public void setEnabled(boolean enabled) {
        kp.put("enabled",enabled);
    }

    protected String comment = "";
    public String getComment() {
        return comment;
    }
    public void setComment(String comment) {
        this.comment = comment;
    }
    
    public DecideRule() {

    }
    
    public DecideResult decisionFor(ProcessorURI uri) {
        if (!getEnabled()) {
            return DecideResult.NONE;
        }
        DecideResult result = innerDecide(uri);
        if (result == DecideResult.NONE) {
            return result;
        }

        return result;
    }
    
    
    protected abstract DecideResult innerDecide(ProcessorURI uri);
    
    
    public DecideResult onlyDecision(ProcessorURI uri) {
        return null;
    }

    public boolean accepts(ProcessorURI uri) {
        return DecideResult.ACCEPT == decisionFor(uri);
    }
    
}
