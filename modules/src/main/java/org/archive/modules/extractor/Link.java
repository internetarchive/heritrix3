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

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import org.apache.commons.httpclient.URIException;
import org.archive.modules.CoreAttributeConstants;
import org.archive.modules.CrawlURI;
import org.archive.net.UURI;
import org.archive.net.UURIFactory;


/**
 * Link represents one discovered "edge" of the web graph: the source
 * URI, the destination URI, and the type of reference (represented by the
 * context in which it was found). 
 * 
 * As such, it is a suitably generic item to returned from generic 
 * link-extraction utility code.
 * 
 * @author gojomo
 */
public class Link implements Serializable, Comparable<Link> {
    private static final Logger LOGGER = Logger.getLogger(Link.class.getName());

    private static final long serialVersionUID = 2L;


    /** URI where this Link was discovered */
    private CharSequence source;

    /** URI (absolute) where this Link points */
    private CharSequence destination;
    
    /** context of discovery -- will be an XPath-like element[/@attribute] 
     * fragment for HTML URIs, a header name with trailing ':' for header 
     * values, or one of the stand-in constants when other context is 
     * unavailable */
    private LinkContext context;

    /** hop-type */
    private Hop hop;

    /**
     * Flexible dynamic attributes list.
     * <p>
     * See further {@link Link#getData()}
     */
    protected Map<String,Object> data;

    
    /**
     * Create a Link with the given fields.
     * @param source
     * @param destination
     * @param context
     * @param hopType
     */
    public Link(CharSequence source, CharSequence destination,
            LinkContext context, Hop hop) {
        super();
        this.source = source;
        this.destination = destination;
        this.context = context;
        this.hop = hop;
    }


    /**
     * @return Returns the context.
     */
    public LinkContext getContext() {
        return context;
    }
    /**
     * @return Returns the destination.
     */
    public CharSequence getDestination() {
        return destination;
    }
    /**
     * @return Returns the source.
     */
    public CharSequence getSource() {
        return source;
    }

    /**
     * @return char hopType
     */
    public Hop getHopType() {
        return hop;
    }

    
    @Override
    public String toString() {
        return this.destination + " " + hop.getHopChar() + " " + this.context;
    }
    /** 
     * Attribute list
     * <p>
     * By convention the attribute list is keyed by constants found in the
     * {@link CoreAttributeConstants} interface.  Use this list to carry
     * data or state produced by custom processors rather change the
     * classes {@link CrawlURI} or this class, CrawlURI.
     * <p>
     * This list becomes {@link CrawlURI#getData()} when the Link is promoted to CrawlURI via
     * {@link CrawlURI#createCrawlURI(UURI, Link)} 
     * <p>
     * If the list is null when this method is invoked, a new instance will be created and returned.
     * 
     * @returns a flexible map of key/value pairs for storing
     *   status of this URI for use by other processors. 
    */
    public Map<String, Object> getData() {
        if (data == null) {
            data = new HashMap<String,Object>();
        }
        return data;
    }
    
    /**
     * @return true if data map is not null
     */
    public boolean hasData() {
        return data != null;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Link)) {
            return false;
        }
        Link l = (Link)o;
        return l.source.equals(source) && l.destination.equals(destination)
         && l.context.equals(context) && l.hop.equals(hop);
    }


    @Override
    public int hashCode() {
        int r = 37;
        return r ^ source.hashCode() ^ destination.hashCode()
         ^ context.hashCode() ^ hop.hashCode();
    }

    public static void addRelativeToBase(CrawlURI uri, int max, 
            String newUri, LinkContext context, Hop hop) throws URIException {
        UURI dest = UURIFactory.getInstance(uri.getBaseURI(), newUri);
        add2(uri, max, dest, context, hop);
    }

    
    public static void addRelativeToVia(CrawlURI uri, int max, String newUri,
            LinkContext context, Hop hop) throws URIException {
        UURI relTo = uri.getVia();
        if (relTo == null) {
            if (!uri.getAnnotations().contains("usedBaseForVia")) {
                LOGGER.info("no via where expected; using base instead: " + uri);
                uri.getAnnotations().add("usedBaseForVia");
            }
            relTo = uri.getBaseURI();
        }
        UURI dest = UURIFactory.getInstance(relTo, newUri);
        add2(uri, max, dest, context, hop);
    }

    public static void add(CrawlURI uri, int max, String newUri, 
            LinkContext context, Hop hop) throws URIException {
        UURI dest = UURIFactory.getInstance(newUri);
        add2(uri, max, dest, context, hop);
    }


    private static void add2(CrawlURI uri, int max, UURI dest, 
            LinkContext context, Hop hop) throws URIException {
        if (uri.getOutLinks().size() < max) {
            UURI src = uri.getUURI();
            Link link = new Link(src, dest, context, hop);
            uri.getOutLinks().add(link);
//            return link;
        } else {
            uri.incrementDiscardedOutLinks();
        }
    }

    public int compareTo(Link o) {
        int cmp = source.toString().compareTo(o.source.toString());
        if (cmp==0) {
            cmp = destination.toString().compareTo(o.destination.toString());
        }
        if (cmp==0) {
            cmp = context.toString().compareTo(o.context.toString());
        }
        if (cmp==0) {
            cmp = hop.toString().compareTo(o.hop.toString());
        }
        return cmp;
    }

}
