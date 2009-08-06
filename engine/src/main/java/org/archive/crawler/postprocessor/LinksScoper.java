/* LinksScoper
 * 
 * $Id$
 *
 * Created on Oct 2, 2003
 * 
 * Copyright (C) 2003 Internet Archive.
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
 */
package org.archive.crawler.postprocessor;

import static org.archive.modules.SchedulingConstants.HIGH;
import static org.archive.modules.SchedulingConstants.MEDIUM;
import static org.archive.modules.SchedulingConstants.NORMAL;
import static org.archive.modules.fetcher.FetchStatusCodes.S_PREREQUISITE_UNSCHEDULABLE_FAILURE;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.httpclient.URIException;
import org.archive.crawler.framework.Scoper;
import org.archive.modules.CrawlURI;
import org.archive.modules.PostProcessor;
import org.archive.modules.ProcessorURI;
import org.archive.modules.deciderules.DecideResult;
import org.archive.modules.deciderules.DecideRule;
import org.archive.modules.deciderules.RejectDecideRule;
import org.archive.modules.extractor.Hop;
import org.archive.modules.extractor.Link;

/**
 * Determine which extracted links are within scope.
 * TODO: To test scope, requires that Link be converted to
 * a CrawlURI.  Make it so don't have to make a CrawlURI to test
 * if Link is in scope.
 * <p>Since this scoper has to create CrawlURIs, no sense
 * discarding them since later in the processing chain CrawlURIs rather
 * than Links are whats needed scheduling extracted links w/ the
 * Frontier (Frontier#schedule expects CrawlURI, not Link).  This class
 * replaces Links w/ the CrawlURI that wraps the Link in the CrawlURI.
 *
 * @author gojomo
 * @author stack
 */
public class LinksScoper extends Scoper implements PostProcessor {

    private static final long serialVersionUID = -3L;

    private static Logger LOGGER =
        Logger.getLogger(LinksScoper.class.getName());

    /**
     * If enabled, any URL found because a seed redirected to it (original seed
     * returned 301 or 302), will also be treated as a seed.
     */
    {
        setSeedsRedirectNewSeeds(true);
    }
    public boolean getSeedsRedirectNewSeeds() {
        return (Boolean) kp.get("seedsRedirectNewSeeds");
    }
    public void setSeedsRedirectNewSeeds(boolean redirect) {
        kp.put("seedsRedirectNewSeeds",redirect);
    }

    /**
     * DecideRules applied after an URI has been rejected. If the rules return
     * {@link DecideResult#ACCEPT}, the URI is logged (if the logging level is
     * INFO). Depends on {@link Scoper#OVERRIDE_LOGGER} being enabled.
     */
    {
        setLogRejectsRule(new RejectDecideRule());
    }
    public DecideRule getLogRejectsRule() {
        return (DecideRule) kp.get("logRejectsRule");
    }
    public void setLogRejectsRule(DecideRule rule) {
        kp.put("logRejectsRule", rule);
    }
    
    /**
     * Number of hops (of any sort) from a seed up to which a URI has higher
     * priority scheduling than any remaining seed. For example, if set to 1
     * items one hop (link, embed, redirect, etc.) away from a seed will be
     * scheduled with HIGH priority. If set to -1, no preferencing will occur,
     * and a breadth-first search with seeds processed before discovered links
     * will proceed. If set to zero, a purely depth-first search will proceed,
     * with all discovered links processed before remaining seeds. Seed
     * redirects are treated as one hop from a seed.
     */
    {
        setPreferenceDepthHops(-1); // no limit
    }
    public int getPreferenceDepthHops() {
        return (Integer) kp.get("preferenceDepthHops");
    }
    public void setPreferenceDepthHops(int depth) {
        kp.put("preferenceDepthHops",depth);
    }
    
    /**
     * @param name Name of this filter.
     */
    public LinksScoper() {
        super();
    }
    
    
    @Override
    protected boolean shouldProcess(ProcessorURI puri) {
        if (!(puri instanceof CrawlURI)) {
            return false;
        }
        CrawlURI curi = (CrawlURI)puri;
        
        // If prerequisites, nothing to be done in here.
        if (curi.hasPrerequisiteUri()) {
            handlePrerequisite(curi);
            return false;
        }
        
        // Don't extract links of error pages.
        if (curi.getFetchStatus() < 200 || curi.getFetchStatus() >= 400) {
            curi.getOutLinks().clear();
            return false;
        }
        
        if (curi.getOutLinks().isEmpty()) {
            // No outlinks to process.
            return false;
        }
        
        return true;
    }

    
    @Override
    protected void innerProcess(final ProcessorURI puri) {
        CrawlURI curi = (CrawlURI)puri;
        final boolean redirectsNewSeeds = getSeedsRedirectNewSeeds(); 
        int preferenceDepthHops = getPreferenceDepthHops(); 
        
        for (Link wref: curi.getOutLinks()) try {
            int directive = getSchedulingFor(curi, wref, preferenceDepthHops);
            CrawlURI caURI = curi.createCrawlURI(curi.getBaseURI(), 
                    wref, directive, 
                    considerAsSeed(curi, wref, redirectsNewSeeds));
            if (isInScope(caURI)) {
                curi.getOutCandidates().add(caURI);
            }
        } catch (URIException e) {
            loggerModule.logUriError(e, curi.getUURI(), 
                    wref.getDestination().toString());
        }
        curi.getOutLinks().clear();
        
//        Collection<CrawlURI> inScopeLinks = new HashSet<CrawlURI>();
//        for (final Iterator i = curi.getOutObjects().iterator(); i.hasNext();) {
//            Object o = i.next();
//            if(o instanceof Link){
//                final Link wref = (Link)o;
//                try {
//                    final int directive = getSchedulingFor(curi, wref, 
//                        preferenceDepthHops);
//                    final CrawlURI caURI =
//                        curi.createCrawlURI(curi.getBaseURI(), wref, 
//                            directive, 
//                            considerAsSeed(curi, wref, redirectsNewSeeds));
//                    if (isInScope(caURI)) {
//                        inScopeLinks.add(caURI);
//                    }
//                } catch (URIException e) {
//                    getController().logUriError(e, curi.getUURI(), 
//                        wref.getDestination().toString());
//                }
//            } else if(o instanceof CrawlURI){
//                CrawlURI caURI = (CrawlURI)o;
//                if(isInScope(caURI)){
//                    inScopeLinks.add(caURI);
//                }
//            } else {
//                LOGGER.severe("Unexpected type: " + o);
//            }
//        }
//        // Replace current links collection w/ inscopeLinks.  May be
//        // an empty collection.
//        curi.replaceOutlinks(inScopeLinks);
    }
    
    /**
     * The CrawlURI has a prerequisite; apply scoping and update
     * Link to CrawlURI in manner analogous to outlink handling. 
     * @param curi CrawlURI with prereq to consider
     */
    protected void handlePrerequisite(CrawlURI curi) {
        try {
            // Create prerequisite CrawlURI
            CrawlURI caUri =
                curi.createCrawlURI(curi.getBaseURI(),
                    (Link) curi.getPrerequisiteUri());
            int prereqPriority = curi.getSchedulingDirective() - 1;
            if (prereqPriority < 0) {
                prereqPriority = 0;
                LOGGER.severe("Unable to promote prerequisite " + caUri +
                    " above " + curi);
            }
            caUri.setSchedulingDirective(prereqPriority);
            caUri.setForceFetch(true);
// FIXME!!!            getController().setStateProvider(caUri);
            if(isInScope(caUri)) {
                // replace link with CrawlURI
                curi.setPrerequisiteUri(caUri);
            } else {
                // prerequisite is out-of-scope; mark CrawlURI as error,
                // preventinting normal S_DEFERRED handling
                curi.setFetchStatus(S_PREREQUISITE_UNSCHEDULABLE_FAILURE);
            }
       } catch (URIException ex) {
            Object[] array = {curi, curi.getPrerequisiteUri()};
            loggerModule.getUriErrors().log(Level.INFO,ex.getMessage(), array);
        } catch (NumberFormatException e) {
            // UURI.createUURI will occasionally throw this error.
            Object[] array = {curi, curi.getPrerequisiteUri()};
            loggerModule.getUriErrors().log(Level.INFO,e.getMessage(), array);
        }
    }

    protected void outOfScope(CrawlURI caUri) {
        super.outOfScope(caUri);
        if (!LOGGER.isLoggable(Level.INFO)) {
            return;
        }
        DecideRule seq = getLogRejectsRule();
        if (seq.decisionFor(caUri) == DecideResult.ACCEPT) {
            LOGGER.info(caUri.getUURI().toString());
        }
    }
    
    private boolean considerAsSeed(final CrawlURI curi, final Link wref,
            final boolean redirectsNewSeeds) {
        return redirectsNewSeeds && curi.isSeed()
                && wref.getHopType() == Hop.REFER;
    }
    
    /**
     * Determine scheduling for the  <code>curi</code>.
     * As with the LinksScoper in general, this only handles extracted links,
     * seeds do not pass through here, but are given MEDIUM priority.  
     * Imports into the frontier similarly do not pass through here, 
     * but are given NORMAL priority.
     */
    protected int getSchedulingFor(final CrawlURI curi, final Link wref,
            final int preferenceDepthHops) {
        final Hop c = wref.getHopType();
        if (LOGGER.isLoggable(Level.FINEST)) {
            LOGGER.finest(curi + " with path=" + curi.getPathFromSeed() +
                " isSeed=" + curi.isSeed() + " with fetchStatus=" +
                curi.getFetchStatus() + " -> " + wref.getDestination() +
                " type " + c + " with context=" + wref.getContext());
        }

        switch (c) {
            case REFER:
                // Treat redirects somewhat urgently
                // This also ensures seed redirects remain seed priority
                return (preferenceDepthHops >= 0 ? HIGH : MEDIUM);
            default:
                if (preferenceDepthHops == 0)
                    return HIGH;
                    // this implies seed redirects are treated as path
                    // length 1, which I belive is standard.
                    // curi.getPathFromSeed() can never be null here, because
                    // we're processing a link extracted from curi
                if (preferenceDepthHops > 0 && 
                    curi.getPathFromSeed().length() + 1 <= preferenceDepthHops)
                    return HIGH;
                // Everything else normal (at least for now)
                return NORMAL;
        }
    }
}
