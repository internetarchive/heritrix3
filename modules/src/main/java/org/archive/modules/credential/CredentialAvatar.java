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
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Logger;

import org.archive.modules.ProcessorURI;

/**
 * A credential representation.
 *
 * Added to the CrawlServer upon successful authentication.  Used as a marker
 * of successful authentication event and for carrying credential
 * payload to be used subsequently doing preemptive authentications (e.g.
 * For case of RFC2617, needs to be offered everytime we're accessing inside
 * a protected area).  Also carried by the ProcessorURI when cycling through
 * processing chain trying a credential to see if it will authenticate.
 *
 * <p>This class exists because its not safe to keep references
 * to the settings derived Credential classes so instead of keeping references
 * to credential classes, we carry around this avatar.
 *
 * <p>Scope for avatars is crawlserver.  Only used within a CrawlServer
 * scope.
 *
 * <p>Immutable.
 *
 * @author stack
 * @version $Revision$, $Date$
 */
public class CredentialAvatar
implements Serializable {

    private static final long serialVersionUID = 3L;

    private static final Logger logger =
        Logger.getLogger(CredentialAvatar.class.getName());

    /**
     * Key for this credential avatar.
     */
    private final String key;

    /**
     * Type represented by this avatar.
     */
    private final Class<?> type;

    /**
     * Data.
     *
     * May be null.
     * 
     * <p>This used to be an Object and I used to store in here
     * the httpclient AuthScheme but AuthScheme is not serializable
     * and so there'd be trouble getting this payload to lie down
     * in a bdb database.  Changed it to String.  That should be
     * generic enough for credential purposes.
     */
    private final String payload;


    /**
     * Constructor.
     * @param type Type for this credential avatar.
     * @param key Key for this credential avatar.
     */
    public CredentialAvatar(Class<?> type, String key) {
        this(type, key, null);
    }

    /**
     * Constructor.
     * @param type Type for this credential avatar.
     * @param key Key for this credential avatar.
     * @param payload Data credential needs rerunning or preempting.  May be
     * null and then just the presence is used as signifier of successful
     * auth.
     */
    public CredentialAvatar(Class<?> type, String key, String payload) {
        if (!checkType(type)) {
            throw new IllegalArgumentException("Type is unrecognized: " +
                type);
        }
        this.key = key;
        this.type = type;
        this.payload = payload;
    }

    /**
     * Shutdown default constructor.
     */
    @SuppressWarnings("unused")
    private CredentialAvatar() {
        super();
        this.key = null;
        this.type = null;
        this.payload = null;
    }

    /**
     * @param candidateType Type to check.
     * @return True if this is a known credential type.
     */
    protected boolean checkType(Class<?> candidateType) {
        boolean result = false;
        List<Class<?>> types = CredentialStore.getCredentialTypes();
        for (Iterator<Class<?>> i = types.iterator(); i.hasNext();) {
            if (i.next().equals(candidateType)) {
                result = true;
                break;
            }
        }
        return result;
    }

    /**
     * @return Returns the payload. May be null.
     */
    public String getPayload() {
        return this.payload;
    }

    /**
     * @return Returns the key.
     */
    public String getKey() {
        return this.key;
    }

    /**
     * @return Type represented by this avatar.
     */
    public Class<?> getType() {
        return this.type;
    }

	/**
	 * @param otherType Class to match.
	 * @return True if this credential avatar is of same type.
	 */
	public boolean match(Class<?> otherType) {
		return this.type.equals(otherType);
	}

    /**
     * @param otherType Credential to match.
     * @param otherKey Key to test.
     * @return True if this is avatar for passed credential.
     */
    public boolean match(Class<?> otherType, String otherKey) {
        return match(otherType) &&
            (otherKey != null && this.key != null &&
            		this.key.equals(otherKey));
    }

    public String toString() {
        return getType() + "." + this.getKey();
    }

    /**
     * @param handler Settings handler.
     * @param curi ProcessorURI to use for context.
     * @return The credential this avatar represents.
     */
    public Credential getCredential(CredentialStore cs, ProcessorURI curi) {
        Credential result = null;

        if (cs == null) {
            logger.severe("No credential store for " + curi);
            return result;
        }

        Collection<Credential> all = cs.getAll();
        if (all == null) {
            logger.severe("Have CredentialAvatar " + toString() +
                " but no collection: " + curi);
            return result;
        }

        for (Credential c: all) {
            if (!this.type.isInstance(c)) {
                continue;
            }
            String credKey = c.getKey();
            if (credKey != null && credKey.equals(getKey())) {
                result = c;
                break;
            }
        }

        if (result == null) {
            logger.severe("Have CredentialAvatar " + toString() +
                " but no corresponding credential: " + curi);
        }

        return result;
    }
}
