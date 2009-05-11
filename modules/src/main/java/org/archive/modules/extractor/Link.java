/* Link
*
* $Id$
*
* Created on Mar 7, 2005
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
package org.archive.modules.extractor;

import java.io.Serializable;

import org.apache.commons.httpclient.URIException;
import org.archive.modules.ProcessorURI;
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
public class Link implements Serializable {

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

    public static void addRelativeToBase(ProcessorURI uri, int max, 
            String newUri, LinkContext context, Hop hop) throws URIException {
        UURI dest = UURIFactory.getInstance(uri.getUURI(), newUri);
        add2(uri, max, dest, context, hop);
    }

    
    public static void addRelativeToVia(ProcessorURI uri, int max,
            String newUri, LinkContext context, Hop hop) throws URIException {
        UURI dest = UURIFactory.getInstance(uri.getVia(), newUri);
        add2(uri, max, dest, context, hop);
    }


    public static void add(ProcessorURI uri, int max, String newUri, 
            LinkContext context, Hop hop) throws URIException {
        UURI dest = UURIFactory.getInstance(newUri);
        add2(uri, max, dest, context, hop);
    }


    private static void add2(ProcessorURI uri, int max, UURI dest, 
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

}
