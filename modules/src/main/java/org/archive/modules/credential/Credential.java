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
package org.archive.modules.credential;

import java.io.Serializable;
import java.util.Iterator;
import java.util.Map;
import java.util.logging.Logger;

import javax.management.AttributeNotFoundException;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpMethod;
import org.archive.modules.CrawlURI;
import org.archive.modules.net.CrawlServer;
import org.archive.modules.net.ServerCache;

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

    private static final long serialVersionUID = 2L;

    private static final Logger logger =
        Logger.getLogger(Credential.class.getName());
    
    /**
     * The root domain this credential goes against: E.g. www.archive.org
     */
    protected String domain = "";
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
     * {@link #attach(CrawlURI, String)}.
     *
     * @param curi CrawlURI to load with credentials.
     */
    public void attach(CrawlURI curi) {
        curi.getCredentials().add(this);
    }

    /**
     * Detach this credential from passed curi.
     *
     * @param curi
     * @return True if we detached a Credential reference.
     */
    public boolean detach(CrawlURI curi) {
        return curi.getCredentials().remove(this);
    }

    /**
     * Detach all credentials of this type from passed curi.
     *
     * @param curi
     * @return True if we detached references.
     */
    public boolean detachAll(CrawlURI curi) {
        boolean result = false;
        Iterator<Credential> iter = curi.getCredentials().iterator();
        while (iter.hasNext()) {
            Credential cred = iter.next();
            if (cred.getClass() ==  this.getClass()) {
                iter.remove();
                result = true;
            }
        }
        return result;
    }

    /**
     * @param curi CrawlURI to look at.
     * @return True if this credential IS a prerequisite for passed
     * CrawlURI.
     */
    public abstract boolean isPrerequisite(CrawlURI curi);

    /**
     * @param curi CrawlURI to look at.
     * @return True if this credential HAS a prerequisite for passed CrawlURI.
     */
    public abstract boolean hasPrerequisite(CrawlURI curi);

    /**
     * Return the authentication URI, either absolute or relative, that serves
     * as prerequisite the passed <code>curi</code>.
     *
     * @param curi CrawlURI to look at.
     * @return Prerequisite URI for the passed curi.
     */
    public abstract String getPrerequisite(CrawlURI curi);

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
     * @param curi CrawlURI to as for context.
     * @param http Instance of httpclient.
     * @param method Method to populate.
     * @param httpAuthChallenges 
     * @return True if added a credentials.
     */
    public abstract boolean populate(CrawlURI curi, HttpClient http,
        HttpMethod method, Map<String, String> httpAuthChallenges);

    /**
     * @param curi CrawlURI to look at.
     * @return True if this credential is to be posted.  Return false if the
     * credential is to be GET'd or if POST'd or GET'd are not pretinent to this
     * credential type.
     */
    public abstract boolean isPost();

    /**
     * Test passed curi matches this credentials rootUri.
     * @param controller
     * @param curi CrawlURI to test.
     * @return True if domain for credential matches that of the passed curi.
     */
    public boolean rootUriMatch(ServerCache cache, 
            CrawlURI curi) {
        String cd = getDomain();

        CrawlServer serv = cache.getServerFor(curi.getUURI());
        String serverName = serv.getName();
//        String serverName = controller.getServerCache().getServerFor(curi).
//            getName();
        logger.fine("RootURI: Comparing " + serverName + " " + cd);
        return cd != null && serverName != null &&
            serverName.equalsIgnoreCase(cd);
    }

}
