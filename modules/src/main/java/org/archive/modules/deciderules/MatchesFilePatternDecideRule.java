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

import java.util.regex.Pattern;

/**
 * Compares suffix of a passed CrawlURI, UURI, or String against a regular
 * expression pattern, applying its configured decision to all matches.
 *
 * Several predefined patterns are available for convenience. Choosing
 * 'custom' makes this the same as a regular MatchesRegexDecideRule. 
 *
 * @author Igor Ranitovic
 */
public class MatchesFilePatternDecideRule extends MatchesRegexDecideRule {

    public static enum Preset { 
        
        ALL(".*(?i)(\\.(bmp|gif|jpe?g|png|svg|tiff?|aac|aiff?|m3u|m4a|midi?" +
                "|mp2|mp3|mp4|mpa|ogg|ra|ram|wav|wma|asf|asx|avi|flv|mov|mp4" + 
                "|mpeg|mpg|qt|ram|rm|smil|wmv|doc|pdf|ppt|swf))$"),
                    
        IMAGES(".*(?i)(\\.(bmp|gif|jpe?g|png|svg|tiff?))$"),

        AUDIO(".*(?i)(\\.(aac|aiff?|m3u|m4a|midi?|mp2|mp3|mp4|mpa|ogg|ra|ram|wav|wma))$"),
        
        VIDEO(".*(?i)(\\.(asf|asx|avi|flv|mov|mp4|mpeg|mpg|qt|ram|rm|smil|wmv))$"),
            
        MISC(".*(?i)(\\.(doc|pdf|ppt|swf))$"), 

        CUSTOM(null);
        
        final private Pattern pattern;
        
        Preset(String regex) {
            if (regex == null) {
                pattern = null;
            } else {
                pattern = Pattern.compile(regex);
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
     * @return regex to use.
     */
    @Override
    public Pattern getRegex() {
        Preset preset = getUsePreset();
        if (preset == Preset.CUSTOM) {
            return super.getRegex();
        }
        return preset.getPattern();
    }
}
