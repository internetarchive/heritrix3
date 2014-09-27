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
package org.archive.modules.fetcher;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;

import org.apache.http.client.CookieStore;
import org.apache.http.cookie.Cookie;
import org.apache.http.cookie.CookieIdentityComparator;
import org.archive.checkpointing.Checkpoint;
import org.archive.checkpointing.Checkpointable;
import org.archive.spring.ConfigFile;
import org.archive.spring.ConfigPath;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.Lifecycle;

import com.google.common.net.InternetDomainName;

abstract public class AbstractDomainCookieSetStore implements Lifecycle, Checkpointable {

//    private static final Logger logger = 
//            Logger.getLogger(AbstractHostCookieSetStore.class.getName());

    protected static final Comparator<Cookie> cookieComparator = new CookieIdentityComparator();
    
    protected ConfigFile cookiesLoadFile = null;
    public ConfigFile getCookiesLoadFile() {
        return cookiesLoadFile;
    }
    public void setCookiesLoadFile(ConfigFile cookiesLoadFile) {
        this.cookiesLoadFile = cookiesLoadFile;
    }

    protected ConfigPath cookiesSaveFile = null;
    public ConfigPath getCookiesSaveFile() {
        return cookiesSaveFile;
    }
    public void setCookiesSaveFile(ConfigPath cookiesSaveFile) {
        this.cookiesSaveFile = cookiesSaveFile;
    }

    @Override
    public void startCheckpoint(Checkpoint checkpointInProgress) {
        throw new RuntimeException("not implemented");
    }

    @Override
    public void doCheckpoint(Checkpoint checkpointInProgress)
            throws IOException {
        throw new RuntimeException("not implemented");
    }

    @Override
    public void finishCheckpoint(Checkpoint checkpointInProgress) {
        throw new RuntimeException("not implemented");
    }

    @Override
    @Autowired(required=false)
    public void setRecoveryCheckpoint(Checkpoint recoveryCheckpoint) {
        throw new RuntimeException("not implemented");
    }

    protected boolean isRunning = false;
    
    @Override
    public void start() {
        if (isRunning()) {
            return;
        }
        prepare();
//        if (getCookiesLoadFile()!=null) {
//            loadCookies(getCookiesLoadFile());
//        }
        isRunning = true;
    }

    @Override
    public void stop() {
        isRunning = false;
    }

    @Override
    public boolean isRunning() {
        return isRunning;
    }

    public void saveCookies() {
        if (getCookiesSaveFile() != null) {
            // saveCookies(getCookiesSaveFile().getFile().getAbsolutePath());
        }
    }
    
    protected class HostCookieStore implements CookieStore {
        private final List<Cookie> hostCookies;

        protected HostCookieStore(List<Cookie> hostCookies) {
            this.hostCookies = hostCookies;
        }

        @Override
        public List<Cookie> getCookies() {
            return hostCookies;
        }

        @Override
        public boolean clearExpired(Date date) {
            throw new RuntimeException("not implemented");
        }

        @Override
        public void clear() {
            throw new RuntimeException("not implemented");
        }

        @Override
        public void addCookie(Cookie cookie) {
            AbstractDomainCookieSetStore.this.addCookie(cookie);
        }
    }
    
    @SuppressWarnings("unchecked")
    public CookieStore cookieStoreFor(String host) {
        TreeSet<Cookie> hostCookieSet;
        synchronized (this) {
            hostCookieSet = getCookiesByHost().get(host);
        }
        final List<Cookie> hostCookies;
        if (hostCookieSet != null) {
            hostCookies = new ArrayList<Cookie>(hostCookieSet);
        } else {
            hostCookies = Collections.emptyList();
        }
        
        return new HostCookieStore(hostCookies);
    }

    synchronized protected void addCookie(Cookie cookie) {
        String domain = cookie.getDomain();
        if (domain == null) {
            domain = "";
        }
        if (domain.startsWith(".")) {
            domain = domain.substring(1);
        }
        domain = domain.toLowerCase(Locale.ENGLISH);
        
        if (InternetDomainName.isValid(domain)) {
            domain = InternetDomainName.from(domain).topPrivateDomain().toString();
        }

        
        @SuppressWarnings("unchecked")
        TreeSet<Cookie> hostCookieSet = getCookiesByHost().get(domain);
        if (hostCookieSet == null) {
            hostCookieSet = new TreeSet<Cookie>(cookieComparator);
        }
        hostCookieSet.add(cookie);

        getCookiesByHost().put(domain, hostCookieSet);
    }

    abstract protected void prepare();
    @SuppressWarnings("rawtypes")
    abstract protected Map<String, TreeSet> getCookiesByHost();
}
