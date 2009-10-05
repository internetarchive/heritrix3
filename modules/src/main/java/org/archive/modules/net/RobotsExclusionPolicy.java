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
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.httpclient.URIException;
import org.archive.modules.CrawlURI;

/**
 * RobotsExclusionPolicy represents the actual policy adopted with 
 * respect to a specific remote server, usually constructed from 
 * consulting the robots.txt, if any, the server provided. 
 * 
 * (The similarly named RobotsHonoringPolicy, on the other hand, 
 * describes the strategy used by the crawler to determine to what
 * extent it respects exclusion rules.)
 * 
 * The expiration of policies after a suitable amount of time has
 * elapsed since last fetch is handled outside this class, in 
 * CrawlServer itself. 
 * 
 * TODO: refactor RobotsHonoringPolicy to be a class-per-policy, and 
 * then see if a CrawlServer with a HonoringPolicy and a RobotsTxt
 * makes this mediating class unnecessary. 
 * 
 * @author gojomo
 *
 */
public class RobotsExclusionPolicy implements Serializable {
    private static final long serialVersionUID = 3L;

    private static final Logger logger =
        Logger.getLogger(RobotsExclusionPolicy.class.getName());

    public static enum Type {
        NORMAL, 
        ALLOWALL, 
        DENYALL
    }

    private transient Type type = Type.NORMAL;

    final public static RobotsExclusionPolicy ALLOWALL =
        new RobotsExclusionPolicy(Type.ALLOWALL);
    final public static RobotsExclusionPolicy DENYALL =
        new RobotsExclusionPolicy(Type.DENYALL);

    private Robotstxt robotstxt = null;
    // FIXME?: this 'transient' seems wrong -- likely to cause
    // all non-normal policies to break when CrawlServer
    // go through a serialization/deserialization cycle
    RobotsHonoringPolicy honoringPolicy = null; // was transient

    private String lastUsedUserAgent = null;
    private List<String> userAgentsToTest = null;

    /**
     * @param settings 
     * @param reader
     * @param honoringPolicy
     * @return Robot exclusion policy.
     * @throws IOException
     */
    public static RobotsExclusionPolicy policyFor(
            BufferedReader reader, RobotsHonoringPolicy honoringPolicy)
    throws IOException {
        Robotstxt robots = new Robotstxt(reader);
        return (robots.allowsAll())?
            ALLOWALL:
            new RobotsExclusionPolicy(robots, honoringPolicy);
    }


    /**
     * @param settings 
     * @param u
     * @param d
     * @param honoringPolicy
     */
    public RobotsExclusionPolicy( 
            Robotstxt robotstxt, 
            RobotsHonoringPolicy honoringPolicy) {
        this.robotstxt = robotstxt;
        this.honoringPolicy = honoringPolicy;

        if(honoringPolicy == null) return;

        // If honoring policy is most favored user agent, all rules should be checked
        if(honoringPolicy.isType(RobotsHonoringPolicy.Type.MOST_FAVORED)) {
            userAgentsToTest = robotstxt.getUserAgents();

        // IF honoring policy is most favored of set, then make a list with only the set as members
        } else if(honoringPolicy.isType(RobotsHonoringPolicy.Type.MOST_FAVORED_SET)) {
            userAgentsToTest = new ArrayList<String>();
            Iterator<String> userAgentSet = honoringPolicy.getUserAgents().iterator();
            while(userAgentSet.hasNext()) {
                String userAgent = (String) userAgentSet.next();

                Iterator<String> iter = robotstxt.getUserAgents().iterator();
                while ( iter.hasNext() ) {
                    String ua = (String)iter.next();
                    if (userAgent.indexOf(ua)>-1) {
                        userAgentsToTest.add(ua);
                        break;
                    }
                }
            }
        }
    }

    public RobotsExclusionPolicy(Type type) {
        this(null, null);
        this.type = type;
    }

    public boolean disallows(CrawlURI curi, String userAgent) {
        if (this == ALLOWALL)
            return false;
        if (this == DENYALL)
            return true;

        // In the common case with policy=Classic, the useragent is remembered from uri to uri on
        // the same server
        if((honoringPolicy.isType(RobotsHonoringPolicy.Type.CLASSIC) 
                || honoringPolicy.isType(RobotsHonoringPolicy.Type.CUSTOM))
            && (lastUsedUserAgent == null
            || !lastUsedUserAgent.equals(userAgent))) {

            lastUsedUserAgent = userAgent;
            userAgentsToTest = new ArrayList<String>();
            Iterator<String> iter = robotstxt.getUserAgents().iterator();
            String lowerCaseUserAgent = userAgent.toLowerCase();
            while ( iter.hasNext() ) {
                String ua = (String)iter.next();
                // ua in below is already lowercase. See Robotstxt.java line 60.
                if (lowerCaseUserAgent.indexOf(ua)>-1) {
                    userAgentsToTest.add(ua);
                    break; // consider no more sections
                }
            }
        }

        boolean disallow = false;
        String ua = null;

        // Go thru list of all user agents we might act as
        Iterator<String> uas = userAgentsToTest.iterator();
        while(uas.hasNext()) {
            ua = (String) uas.next();
            String path = null; 
            try {
                 path = curi.getUURI().getPathQuery();
            } catch (URIException e) {
                logger.log(Level.SEVERE,"Failed getPathQuery from " + curi, e);
                disallow = false;
                break;
            }
            if(robotstxt.getDirectivesFor(ua).allows(path)) {
                // at least one applicable set of rules allows
                disallow = false;
                break; 
            } else {
                // at least one applicable set of rules disallows
                // so disallow unless later test allows
                disallow = true; 
            }
        }

        // Are we supposed to masquerade as the user agent to which restrictions
        // we follow?
        if(honoringPolicy.shouldMasquerade() && ua != null && !ua.equals("")) {
            curi.setUserAgent(ua);
        }
        return disallow;
    }

    // Methods for object serialization.

    /** If object is DENYALL or ALLOWALL, only the object identity and type
     * is written in the serialization stream.
     *
     * @param stream the serialization stream.
     * @throws IOException 
     */
    private void writeObject(ObjectOutputStream stream) throws IOException {
        stream.writeInt(type.ordinal());
        if (type == Type.NORMAL) {
            stream.defaultWriteObject();
        }
    }

    /** If object is DENYALL or ALLOWALL, only the object identity and type
     * is read from the serialization stream.
     *
     * @param stream the serialization stream.
     * @throws IOException 
     * @throws ClassNotFoundException 
     */
    private void readObject(ObjectInputStream stream)
            throws IOException, ClassNotFoundException {
        int ordinal = stream.readInt();
        type = Type.values()[ordinal];
        if (type == Type.NORMAL) {
            stream.defaultReadObject();
        }
    }

    /** If object is DENYALL or ALLOWALL, the object is replaced by constants
     * so that check for object equality works.
     * @return Object.
     */
    private Object readResolve() {
        if (type == Type.NORMAL) {
            return this;
        } else if (type == Type.ALLOWALL) {
            return ALLOWALL;
        } else if (type == Type.DENYALL) {
            return DENYALL;
        }
        return null;
    }

    /**
     * Get the crawl-delay that applies to the given user-agent, or
     * -1 (indicating no crawl-delay known) if not internal RobotsTxt
     * instance. 
     * 
     * @param userAgent
     * @return int Crawl-Delay value, or -1 if non available
     */
    public float getCrawlDelay(String userAgent) {
        if (robotstxt==null) {
            return -1;
        }
        return robotstxt.getDirectivesFor(userAgent).getCrawlDelay();
    }
}
