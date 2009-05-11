/*
 * MatchesFilePatternDecideRule
 *
 * $Id$
 *
 * Created on Mar 11, 2004
 *
 * Copyright (C) 2004 Internet Archive.
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

import java.util.regex.Pattern;

/**
 * Compares suffix of a passed CrawlURI, UURI, or String against a regular
 * expression pattern, applying its configured decision to all matches.
 *
 * Several predefined patterns are available for convenience. Choosing
 * 'custom' makes this the same as a regular MatchesRegExpDecideRule. 
 *
 * @author Igor Ranitovic
 */
public class MatchesFilePatternDecideRule extends MatchesRegExpDecideRule {

    public static enum Preset { 
        
        
        ALL(".*(?i)(\\.(bmp|gif|jpe?g|png|tiff?|mid|mp2|mp3|mp4|wav|avi|mov" + 
                "|mpeg|ram|rm|smil|wmv|doc|pdf|ppt|swf))$"),
                
        IMAGES(".*(?i)(\\.(bmp|gif|jpe?g|png|tiff?))$"), 

        AUDIO(".*(?i)(\\.(mid|mp2|mp3|mp4|wav))$"),
        
        VIDEO(".*(?i)(\\.(avi|mov|mpeg|ram|rm|smil|wmv))$"), 

        MISC(".*(?i)(\\.(doc|pdf|ppt|swf))$"), 

        CUSTOM(null);
        
        final private Pattern pattern;
        
        Preset(String regexp) {
            if (regexp == null) {
                pattern = null;
            } else {
                pattern = Pattern.compile(regexp);
            }
        }
        
        
        public Pattern getPattern() {
            return pattern;
        }
    }
        
    private static final long serialVersionUID = 3L;

    {
        setUsePreset(Preset.ALL);
    }
    public Preset getUsePreset() {
        return (Preset) kp.get("usePreset");
    }
    public void setUsePreset(Preset preset) {
        kp.put("usePreset",preset); 
    }

    /**
     * Usual constructor.
     */
    public MatchesFilePatternDecideRule() {
    }

    /**
     * Use a preset if configured to do so.
     * @param o Context
     * @return Regex to use.
     * 
     * @see org.archive.crawler.filter.URIRegExpFilter#getRegexp(Object)
     */
    @Override
    public Pattern getRegex() {
        Preset preset = getUsePreset();
        if (preset == Preset.CUSTOM) {
            return getRegex();
        }
        return preset.getPattern();
    }
}
