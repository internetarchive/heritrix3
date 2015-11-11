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
import java.util.Date;
import java.util.List;

import org.apache.http.client.CookieStore;
import org.apache.http.cookie.Cookie;
import org.apache.http.impl.client.BasicCookieStore;
import org.archive.checkpointing.Checkpoint;
import org.springframework.beans.factory.annotation.Autowired;

/** In-memory cookie store, mostly for testing. */
public class SimpleCookieStore extends AbstractCookieStore implements CookieStore {
    
    protected BasicCookieStore cookies;
    
    @Override
    protected void prepare() {
        cookies = new BasicCookieStore();
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

    @Override
    public List<Cookie> getCookies() {
        return cookies.getCookies();
    }

    @Override
    public boolean clearExpired(Date date) {
        return cookies.clearExpired(date);
    }

    @Override
    public CookieStore cookieStoreFor(String host) {
        return this;
    }

    @Override
    public void addCookieImpl(Cookie cookie) {
        cookies.addCookie(cookie);
    }

    @Override
    public void clear() {
        cookies.clear();
    }
}
