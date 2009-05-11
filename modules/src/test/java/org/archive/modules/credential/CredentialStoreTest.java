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


import java.util.logging.Logger;

import org.archive.state.ModuleTestBase;

/**
 * Test add, edit, delete from credential store.
 *
 * @author stack
 * @version $Revision$, $Date$
 */
public class CredentialStoreTest extends ModuleTestBase {
    protected static Logger logger =
        Logger.getLogger("org.archive.crawler.datamodel.CredentialTest");

    final public void testCredentials() throws Exception {
        if(true) return;
//        MemorySheetManager manager = new MemorySheetManager();
//        SingleSheet global = manager.getGlobalSheet();
//        SingleSheet domain = manager.addSingleSheet("domain");
//        SingleSheet hostSingle = manager.addSingleSheet("hostSingle");
//        manager.associate(hostSingle, Collections.singleton("org.archive"));
//        manager.associate(domain, Collections.singleton("org."));
//        Sheet host = manager.findConfig("org.archive.foo");
        
        CredentialStore store = new CredentialStore();
        //FIXME:SPRINGY
//        store.setCredentials(new HashMap<K, V>());
//        global.set(store, CredentialStore.CREDENTIALS, 
//                new SettingsMap<Credential>(global, Credential.class));
//        domain.set(store, CredentialStore.CREDENTIALS, 
//                new SettingsMap<Credential>(domain, Credential.class));
//        hostSingle.set(store, CredentialStore.CREDENTIALS, 
//                new SettingsMap<Credential>(hostSingle, Credential.class));
        
//        writeCrendentials(store, global, "global");
//        writeCrendentials(store, domain, "domain");
//        writeCrendentials(store, hostSingle, "host");
//        List types = CredentialStore.getCredentialTypes();
//        
//        
//        
//        List<String> globalNames = checkContextNames(store, global, types.size());
//        checkContextNames(store, domain, types.size() * 2); // should be global + domain
//        checkContextNames(store, host, types.size() * 3); // should be global + domain + host

        //FIXME:SPRINGY
//        Key<Map<String,Credential>> k = CredentialStore.CREDENTIALS;
//        Map<String,Credential> defMap = global.resolveEditableMap(store, k);
//        for (String name: globalNames) {
//            defMap.remove(name);
//        }
        // Should be only host and domain objects at deepest scope.
//        checkContextNames(store, host, types.size() * 2);
    }

//    private List<String> checkContextNames(CredentialStore store, 
//            Sheet sheet, int size) {
//        Map<String,Credential> map = store.getCredentials();
//
//        List<String> names = new ArrayList<String>(size);
//        names.addAll(map.keySet());
//        assertEquals("Not enough names", size, map.size());
//        return names;
//    }
//
//    private void writeCredentials(CredentialStore store, SingleSheet context,
//                String prefix) throws Exception {
//        Map<String,Credential> map = new SettingsMap<Credential>(context, 
//                Credential.class);
////        context.set(store, CredentialStore.CREDENTIALS, map);
//        store.setCredentials(map);
//        
//        List<Class> types = CredentialStore.getCredentialTypes();
//        for (Class cl: types) {
//            Credential c = (Credential)cl.newInstance();
//            map.put(prefix + "." + cl.getName(), c);
//            assertNotNull("Failed create of " + cl, c);
//            logger.info("Created " + cl.getName());
//        }
//    }
}
