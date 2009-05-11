/* RobotsDirectives.java
 *
 * $Id: PrefixSet.java 4947 2007-03-01 04:47:24Z gojomo $
 *
 * Created April 29, 2008
 *
 * Copyright (C) 2008 Internet Archive.
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
package org.archive.modules.net;

import java.io.Serializable;

import org.archive.util.PrefixSet;

/**
 * Represents the directives that apply to a user-agent (or set of
 * user-agents)
 */
public class RobotsDirectives implements Serializable {
    private static final long serialVersionUID = 5386542759286155383L;
    
    PrefixSet disallows = new PrefixSet();
    PrefixSet allows = new PrefixSet();
    int crawlDelay = -1; 

    public boolean allows(String path) {
        if(disallows.containsPrefixOf(path)) {
            return allows.containsPrefixOf(path);
        }
        return true;
    }

    public void addDisallow(String path) {
        if(path.length()==0) {
            // ignore empty-string disallows 
            // (they really mean allow, when alone)
            return;
        }
        disallows.add(path);
    }

    public void addAllow(String path) {
        allows.add(path);
    }

    public void setCrawlDelay(int i) {
        crawlDelay=i;
    }

    public int getCrawlDelay() {
        return crawlDelay;
    }
}