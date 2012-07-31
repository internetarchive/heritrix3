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
package org.archive.modules.net;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Serializable;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.io.IOUtils;
import org.archive.bdb.AutoKryo;
import org.archive.io.ReadSource;

/**
 * Utility class for parsing and representing 'robots.txt' format 
 * directives, into a list of named user-agents and map from user-agents 
 * to RobotsDirectives. 
 */
public class Robotstxt implements Serializable {
    static final long serialVersionUID = 7025386509301303890L;
    private static final Logger logger =
        Logger.getLogger(Robotstxt.class.getName());

    protected static final long MAX_SIZE = 500*1024;
    
    // all user agents contained in this robots.txt
    // in order of declaration
    // TODO: consider discarding irrelevant entries
    protected LinkedList<String> namedUserAgents = new LinkedList<String>();
    // map user-agents to directives
    protected Map<String,RobotsDirectives> agentsToDirectives = 
        new HashMap<String,RobotsDirectives>();
    protected RobotsDirectives wildcardDirectives = null; 
    
    protected boolean hasErrors = false;
    
    protected static RobotsDirectives NO_DIRECTIVES = new RobotsDirectives();
    /** empty, reusable instance for all sites providing no rules */
    public static Robotstxt NO_ROBOTS = new Robotstxt();
    
    public Robotstxt() {
    }

    public Robotstxt(BufferedReader reader) throws IOException {
        initializeFromReader(reader);
    }

    public Robotstxt(ReadSource customRobots) {
        BufferedReader reader = new BufferedReader(customRobots.obtainReader());
        try {
            initializeFromReader(reader);
        } catch (IOException e) {
            logger.log(Level.SEVERE,
                    "robots ReadSource problem: potential for inadvertent overcrawling",
                    e);
        } finally {
            IOUtils.closeQuietly(reader); 
        }
    }

    protected void initializeFromReader(BufferedReader reader) throws IOException {
        String read;
        long charCount = 0;
        // current is the disallowed paths for the preceding User-Agent(s)
        RobotsDirectives current = null;
        // whether a non-'User-Agent' directive has been encountered
        boolean hasDirectivesYet = false; 
        while (reader != null) {
            // we count characters instead of bytes because the byte count isn't easily available 
            if (charCount >= MAX_SIZE) {
                logger.warning("processed " + charCount + " characters, ignoring the rest (see HER-1990)");
                reader.close();
                reader = null;
                continue;
            }

            do {
                read = reader.readLine();
                if (read != null) { 
                    charCount += read.length();
                }
                // Skip comments & blanks
            } while (read != null && charCount < MAX_SIZE
                    && ((read = read.trim()).startsWith("#") || read.length() == 0));
            if (read == null) {
                reader.close();
                reader = null;
            } else {
                // remove any html markup
                read = read.replaceAll("<[^>]+>","");
                int commentIndex = read.indexOf("#");
                if (commentIndex > -1) {
                    // Strip trailing comment
                    read = read.substring(0, commentIndex);
                }
                read = read.trim();
                if (read.matches("(?i)^User-agent:.*")) {
                    String ua = read.substring(11).trim().toLowerCase();
                    if (current == null || hasDirectivesYet ) {
                        // only create new rules-list if necessary
                        // otherwise share with previous user-agent
                        current = new RobotsDirectives();
                        hasDirectivesYet = false; 
                    }
                    if (ua.equals("*")) {
                        wildcardDirectives = current;
                    } else {
                        namedUserAgents.addLast(ua);
                        agentsToDirectives.put(ua, current);
                    }
                    continue;
                }
                if (read.matches("(?i)Disallow:.*")) {
                    if (current == null) {
                        // buggy robots.txt
                        hasErrors = true;
                        continue;
                    }
                    String path = read.substring(9).trim();
                    // tolerate common error of ending path with '*' character
                    // (not allowed by original spec; redundant but harmless with 
                    // Google's wildcarding extensions -- which we don't yet fully
                    // support). 
                    if(path.endsWith("*")) {
                        path = path.substring(0,path.length()-1); 
                    }
                    current.addDisallow(path);
                    hasDirectivesYet = true; 
                    continue;
                }
                if (read.matches("(?i)Crawl-delay:.*")) {
                    if (current == null) {
                        // buggy robots.txt
                        hasErrors = true;
                        continue;
                    }
                    // consider a crawl-delay as sufficient to end a grouping of
                    // User-Agent lines
                    hasDirectivesYet = true;
                    String val = read.substring(12).trim();
                    try {
                        val = val.split("[^\\d\\.]+")[0];
                        current.setCrawlDelay(Float.parseFloat(val));
                    } catch (ArrayIndexOutOfBoundsException e) {
                        // ignore 
                    } catch (NumberFormatException nfe) {
                        // ignore
                    }
                    continue;
                }
                if (read.matches("(?i)Allow:.*")) {
                    if (current == null) {
                        // buggy robots.txt
                        hasErrors = true;
                        continue;
                    }
                    String path = read.substring(6).trim();
                    // tolerate common error of ending path with '*' character
                    // (not allowed by original spec; redundant but harmless with 
                    // Google's wildcarding extensions -- which we don't yet fully
                    // support). 
                    if(path.endsWith("*")) {
                        path = path.substring(0,path.length()-1); 
                    }
                    current.addAllow(path);
                    hasDirectivesYet = true;
                    continue;
                }
                // unknown line; do nothing for now
            }
        }
    }

    /**
     * Does this policy effectively allow everything? (No 
     * disallows or timing (crawl-delay) directives?)
     * @return
     */
    public boolean allowsAll() {
        // TODO: refine so directives that are all empty are also 
        // recognized as allowing all
        return agentsToDirectives.isEmpty();
    }
    
    public List<String> getNamedUserAgents() {
        return namedUserAgents;
    }

    /**
     * Return the RobotsDirectives, if any, appropriate for the given User-Agent
     * string. If useFallbacks is true, a wildcard ('*') directives or the default
     * of NO_DIRECTIVES will be returned, as appropriate, if there is no better
     * match. If useFallbacks is false, a null will be returned if no declared
     * directives targeted the given User-Agent.
     * 
     * @param ua String User-Agent to lookup
     * @param useFallbacks if true, fall-back to wildcard directives or 
     * default allow as needed
     * @return directives to use, or null if useFallbacks is false and no 
     * non-wildcard directives match the supplied User-Agent
     */
    public RobotsDirectives getDirectivesFor(String ua, boolean useFallbacks) {
        // find matching ua
        for(String uaListed : namedUserAgents) {
            if(ua.indexOf(uaListed)>-1) {
                return agentsToDirectives.get(uaListed);
            }
        }
        if(useFallbacks==false) {
            return null; 
        }
        if (wildcardDirectives!=null) {
            return wildcardDirectives;
        }
        // no applicable user-agents, so empty directives
        return NO_DIRECTIVES; 
    }

    /**
     * Return directives to use for the given User-Agent, resorting to wildcard
     * rules or the default no-directives if necessary.
     * 
     * @param userAgent String User-Agent to lookup
     * @return directives to use
     */
    public RobotsDirectives getDirectivesFor(String userAgent) {
        return getDirectivesFor(userAgent, true);
    }
    
    // Kryo support
    public static void autoregisterTo(AutoKryo kryo) {
        kryo.register(Robotstxt.class);
        kryo.autoregister(HashMap.class);
        kryo.autoregister(LinkedList.class);
        kryo.autoregister(RobotsDirectives.class);
        kryo.setRegistrationOptional(true); 
    }
}
