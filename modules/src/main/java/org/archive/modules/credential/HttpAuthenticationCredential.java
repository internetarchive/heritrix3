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

import java.util.Iterator;
import java.util.Set;
import java.util.logging.Logger;

import org.archive.modules.CrawlURI;

/**
 * A Basic/Digest HTTP Authentication (RFC2617) credential.
 *
 * (Previously named "Rfc2617Credential".)
 * 
 * @author stack
 * @version $Revision$, $Date$
 */
public class HttpAuthenticationCredential extends Credential {
    private static final long serialVersionUID = 4L;

    private static Logger logger =
        Logger.getLogger(HttpAuthenticationCredential.class.getName());


    /** Basic/Digest Auth realm. */
    protected String realm = "Realm";
    public String getRealm() {
        return this.realm;
    }
    public void setRealm(String realm) {
        this.realm = realm;
    }

    /** Login. */
    protected String login = "login";
    public String getLogin() {
        return this.login;
    }
    public void setLogin(String login) {
        this.login = login;
    }

    /** Password. */
    protected String password = "password";
    public String getPassword() {
        return this.password;
    }
    public void setPassword(String password) {
        this.password = password;
    }

    /**
     * Constructor.
     */
    public HttpAuthenticationCredential() {
    }

    public boolean isPrerequisite(CrawlURI curi) {
        // Return false.  Later when we implement preemptive
        // rfc2617, this will change.
        return false;
    }

    public boolean hasPrerequisite(CrawlURI curi) {
        // Return false.  Later when we implement preemptive
        // rfc2617, this will change.
        return false;
    }

    public String getPrerequisite(CrawlURI curi) {
        // Return null.  Later when we implement preemptive
        // rfc2617, this will change.
        return null;
    }

    public String getKey() {
        return getRealm();
    }

    public boolean isEveryTime() {
        return true;
    }

    public boolean isPost() {
        // Return false.  This credential type doesn't care whether posted or
        // get'd.
        return false;
    }

    /**
     * Convenience method that does look up on passed set using realm for key.
     *
     * @param rfc2617Credentials Set of Rfc2617 credentials.  If passed set is
     * not pure Rfc2617Credentials then will be ClassCastExceptions.
     * @param realm Realm to find in passed set.
     * @param context Context to use when searching the realm.
     * @return Credential of passed realm name else null.  If more than one
     * credential w/ passed realm name, and there shouldn't be, we return first
     * found.
     */
    public static HttpAuthenticationCredential getByRealm(Set<Credential> rfc2617Credentials,
            String realm, CrawlURI context) {

        HttpAuthenticationCredential result = null;
        if (rfc2617Credentials == null || rfc2617Credentials.size() <= 0) {
            return result;
        }
        if (rfc2617Credentials != null && rfc2617Credentials.size() > 0) {
            for (Iterator<Credential> i = rfc2617Credentials.iterator(); i.hasNext();) {
                HttpAuthenticationCredential c = (HttpAuthenticationCredential)i.next();

                // empty realm field means the credential can be used for any realm specified by server
                if (c.getRealm() == null || c.getRealm().isEmpty()) {
                    result = c;
                    break;
                }
                
                if (c.getRealm().equals(realm)) {
                    result = c;
                    break;
                }
            }
        }
        return result;
    }
}
