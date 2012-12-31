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
import java.io.Reader;
import java.util.Date;
import java.util.List;

import org.apache.http.client.CookieStore;
import org.apache.http.cookie.Cookie;
import org.apache.http.impl.client.BasicCookieStore;
import org.archive.checkpointing.Checkpoint;

/** In-memory cookie store, mostly for testing. */
public class SimpleCookieStore extends AbstractCookieStore {
    protected CookieStore basicCookieStore = new BasicCookieStore();

    @Override
    public void startCheckpoint(Checkpoint checkpointInProgress) {
        throw new RuntimeException("not implemented");
    }

    @Override
    public void setRecoveryCheckpoint(Checkpoint recoveryCheckpoint) {
        throw new RuntimeException("not implemented");
    }

    @Override
    public void finishCheckpoint(Checkpoint checkpointInProgress) {
        throw new RuntimeException("not implemented");
    }

    @Override
    public void doCheckpoint(Checkpoint checkpointInProgress)
            throws IOException {
        throw new RuntimeException("not implemented");
    }

    @Override
    public List<Cookie> getCookies() {
        return basicCookieStore.getCookies();
    }

    @Override
    public boolean clearExpired(Date date) {
        return basicCookieStore.clearExpired(date);
    }

    @Override
    public void clear() {
        basicCookieStore.clear();
    }

    @Override
    public void addCookie(Cookie cookie) {
        basicCookieStore.addCookie(cookie);
    }

    @Override
    protected void saveCookies(String absolutePath) {
        throw new RuntimeException("not implemented");
    }

    @Override
    protected void prepare() {
    }

    @Override
    protected void loadCookies(Reader reader) {
        throw new RuntimeException("not implemented");
    }
}