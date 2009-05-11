/* Credential
 *
 * Created on Apr 1, 2004
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
package org.archive.modules.credential;

import java.io.Serializable;
import java.util.Iterator;
import java.util.Set;
import java.util.logging.Logger;

import javax.management.AttributeNotFoundException;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpMethod;
import org.archive.modules.ProcessorURI;
import org.archive.modules.net.CrawlServer;
import org.archive.modules.net.ServerCache;
import org.archive.modules.net.ServerCacheUtil;

/**
 * Credential type.
 *
 * Let this be also a credential in the JAAS sense to in that this is what
 * gets added to a subject on successful authentication since it contains
 * data needed to authenticate (realm, login, password, etc.).
 *
 * <p>Settings system assumes that subclasses implement a constructor that
 * takes a name only.
 *
 * @author stack
 * @version $Revision$, $Date$
 */
public abstract class Credential implements Serializable {

    private static final Logger logger =
        Logger.getLogger(Credential.class.getName());

    
    /**
     * The root domain this credential goes against: E.g. www.archive.org
     */
    String domain = "";
    /**
     * @param context Context to use when searching for credential domain.
     * @return The domain/root URI this credential is to go against.
     * @throws AttributeNotFoundException If attribute not found.
     */
    public String getDomain() {
        return this.domain;
    }
    public void setDomain(String domain) {
        this.domain = domain;
    }
    
    /**
     * Constructor.
     */
    public Credential() {
    }

    /**
     * @param context Context to use when searching for credential domain.
     * @param domain New domain.
     * @throws AttributeNotFoundException
     * @throws InvalidAttributeValueException
     */
    /*
    public void setCredentialDomain(CrawlerSettings context, String domain)
    throws InvalidAttributeValueException, AttributeNotFoundException {
        setAttribute(context, new Attribute(ATTR_CREDENTIAL_DOMAIN, domain));
    }
    */

    /**
     * Attach this credentials avatar to the passed <code>curi</code> .
     *
     * Override if credential knows internally what it wants to attach as
     * payload.  Otherwise, if payload is external, use the below
     * {@link #attach(ProcessorURI, String)}.
     *
     * @param curi ProcessorURI to load with credentials.
     */
    public void attach(ProcessorURI curi) {
        attach(curi, null);
    }

    /**
     * Attach this credentials avatar to the passed <code>curi</code> .
     *
     * @param curi ProcessorURI to load with credentials.
     * @param payload Payload to carry in avatar.  Usually credentials.
     */
    public void attach(ProcessorURI curi, String payload) {
        CredentialAvatar ca = (payload == null )?
                new CredentialAvatar(this.getClass(), getKey()):
                new CredentialAvatar(this.getClass(), getKey(), payload);
        curi.getCredentialAvatars().add(ca);
    }

    /**
     * Detach this credential from passed curi.
     *
     * @param curi
     * @return True if we detached a Credential reference.
     */
    public boolean detach(ProcessorURI curi) {
        boolean result = false;
        Set<CredentialAvatar> avatars = curi.getCredentialAvatars();
        if (avatars.isEmpty()) {
            logger.severe("This curi " + curi + " has no cred when it should");
        } 

        Iterator<CredentialAvatar> iter = avatars.iterator();
        while (iter.hasNext()) {            
            CredentialAvatar ca = iter.next();
            if (ca.match(getClass(), getKey())) {
                iter.remove();
                result = true;
            }
        }
        
        return result;
    }

    /**
     * Detach all credentials of this type from passed curi.
     *
     * @param curi
     * @return True if we detached references.
     */
    public boolean detachAll(ProcessorURI curi) {
        boolean result = false;
        Set<CredentialAvatar> avatars = curi.getCredentialAvatars();
        if (avatars.isEmpty()) {
            logger.severe("This curi " + curi +" has no creds when it should.");
            return false;
        }
        Iterator<CredentialAvatar> iter = avatars.iterator();
        while (iter.hasNext()) {
            CredentialAvatar ca = iter.next();
            if (ca.match(getClass())) {
                iter.remove();
                result = true;
            }
        }
        return result;
    }

    /**
     * @param curi ProcessorURI to look at.
     * @return True if this credential IS a prerequisite for passed
     * ProcessorURI.
     */
    public abstract boolean isPrerequisite(ProcessorURI curi);

    /**
     * @param curi ProcessorURI to look at.
     * @return True if this credential HAS a prerequisite for passed ProcessorURI.
     */
    public abstract boolean hasPrerequisite(ProcessorURI curi);

    /**
     * Return the authentication URI, either absolute or relative, that serves
     * as prerequisite the passed <code>curi</code>.
     *
     * @param curi ProcessorURI to look at.
     * @return Prerequisite URI for the passed curi.
     */
    public abstract String getPrerequisite(ProcessorURI curi);

    /**
     * @param context Context to use when searching for credential domain.
     * @return Key that is unique to this credential type.
     * @throws AttributeNotFoundException
     */
    public abstract String getKey();


    /**
     * @return True if this credential is of the type that needs to be offered
     * on each visit to the server (e.g. Rfc2617 is such a type).
     */
    public abstract boolean isEveryTime();

    /**
     * @param curi ProcessorURI to as for context.
     * @param http Instance of httpclient.
     * @param method Method to populate.
     * @param payload Avatar payload to use populating the method.
     * @return True if added a credentials.
     */
    public abstract boolean populate(ProcessorURI curi, HttpClient http,
        HttpMethod method, String payload);

    /**
     * @param curi ProcessorURI to look at.
     * @return True if this credential is to be posted.  Return false if the
     * credential is to be GET'd or if POST'd or GET'd are not pretinent to this
     * credential type.
     */
    public abstract boolean isPost();

    /**
     * Test passed curi matches this credentials rootUri.
     * @param controller
     * @param curi ProcessorURI to test.
     * @return True if domain for credential matches that of the passed curi.
     */
    public boolean rootUriMatch(ServerCache cache, 
            ProcessorURI curi) {
        String cd = getDomain();

        CrawlServer serv = ServerCacheUtil.getServerFor(cache, curi.getUURI());
        String serverName = serv.getName();
//        String serverName = controller.getServerCache().getServerFor(curi).
//            getName();
        logger.fine("RootURI: Comparing " + serverName + " " + cd);
        return cd != null && serverName != null &&
            serverName.equalsIgnoreCase(cd);
    }

}
