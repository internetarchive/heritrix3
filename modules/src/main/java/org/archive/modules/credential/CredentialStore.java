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
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import javax.management.AttributeNotFoundException;
import javax.management.MBeanException;
import javax.management.ReflectionException;

import org.archive.modules.CrawlURI;
import org.archive.spring.HasKeyedProperties;
import org.archive.spring.KeyedProperties;

/**
 * Front door to the credential store.
 *
 * Come here to get at credentials.
 *
 * <p>See <a
 * href="http://crawler.archive.org/proposals/auth/#credentialstoredesign">Credential
 * Store Design</a>.
 *
 * @author stack
 * @version $Revision$, $Date$
 */
public class CredentialStore implements Serializable, HasKeyedProperties {

    private static final long serialVersionUID = 3L;

    @SuppressWarnings("unused")
    private static Logger logger = Logger.getLogger(
        "org.archive.crawler.datamodel.CredentialStore");

    protected KeyedProperties kp = new KeyedProperties();
    public KeyedProperties getKeyedProperties() {
        return kp;
    }
    
    /**
     * Credentials used by heritrix authenticating. See
     * http://crawler.archive.org/proposals/auth/ for background.
     * 
     * @see http://crawler.archive.org/proposals/auth/
     */
    {
        setCredentials(new HashMap<String, Credential>());
    }
    @SuppressWarnings("unchecked")
    public Map<String,Credential> getCredentials() {
        return (Map<String,Credential>) kp.get("credentials");
    }
    public void setCredentials(Map<String,Credential> map) {
        kp.put("credentials",map);
    }
    
    /**
     * List of possible credential types as a List.
     *
     * This types are inner classes of this credential type so they cannot
     * be created without their being associated with a credential list.
     */
    private static final List<Class<?>> credentialTypes;
    // Initialize the credentialType data member.
    static {
        // Array of all known credential types.
        Class<?> [] tmp = {HtmlFormCredential.class, HttpAuthenticationCredential.class};
        credentialTypes = Collections.unmodifiableList(Arrays.asList(tmp));
    }

    /**
     * Constructor.
     */
    public CredentialStore() {
    }

    /**
     * @return Unmodifable list of credential types.
     */
    public static List<Class<?>> getCredentialTypes() {
        return CredentialStore.credentialTypes;
    }


    /**
     * @param context Pass a ProcessorURI.  Used to set
     * context.
     * @return An iterator or null.
     */
    public Collection<Credential> getAll() {
        Map<String,Credential> map = getCredentials();
        return map.values();
    }

    /**
     * @param context  Used to set context.
     * @param name Name to give the manufactured credential.  Should be unique
     * else the add of the credential to the list of credentials will fail.
     * @return Returns <code>name</code>'d credential.
     * @throws AttributeNotFoundException
     * @throws MBeanException
     * @throws ReflectionException
     */
    public Credential get(/*StateProvider*/Object context, String name) {
        return getCredentials().get(name);
    }


    /**
     * Return set made up of all credentials of the passed
     * <code>type</code>.
     *
     * @param context   Used to set context.  
     * @param type Type of the list to return.  Type is some superclass of
     * credentials.
     * @return Unmodifable sublist of all elements of passed type.
     */
    public Set<Credential> subset(CrawlURI context, Class<?> type) {
        return subset(context, type, null);
    }

    /**
     * Return set made up of all credentials of the passed
     * <code>type</code>.
     *
     * @param context  Used to set context.  
     * @param type Type of the list to return.  Type is some superclass of
     * credentials.
     * @param rootUri RootUri to match.  May be null.  In this case we return
     * all.  Currently we expect the CrawlServer name to equate to root Uri.
     * Its not.  Currently it doesn't distingush between servers of same name
     * but different ports (e.g. http and https).
     * @return Unmodifable sublist of all elements of passed type.
     */
    public Set<Credential> subset(CrawlURI context, Class<?> type, String rootUri) {
        Set<Credential> result = null;
        for (Credential c: getAll()) {
            if (!type.isInstance(c)) {
                continue;
            }
            if (rootUri != null) {
                String cd = c.getDomain();
                if (cd == null) {
                    continue;
                }
                if (!rootUri.equalsIgnoreCase(cd)) {
                    continue;
                }
            }
            if (result == null) {
                result = new HashSet<Credential>();
            }
            result.add(c);
        }
        return result;
    }
}
