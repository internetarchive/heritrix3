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

import java.util.List;

import org.archive.httpclient.ConfigurableX509TrustManager.TrustLevel;
import org.archive.modules.Processor;
import org.archive.modules.credential.CredentialStore;
import org.archive.modules.deciderules.DecideRule;
import org.archive.modules.net.ServerCache;

abstract public class AbstractFetchHTTP extends Processor {
    abstract public void setAcceptHeaders(List<String> headers);
    abstract public void setIgnoreCookies(boolean ignoreCookies);
    abstract public CredentialStore getCredentialStore();
    abstract public ServerCache getServerCache();
    abstract public void setAcceptCompression(boolean acceptCompression);
    abstract public void setHttpBindAddress(String address);
    abstract public void setHttpProxyHost(String host);
    abstract public void setHttpProxyPort(int port);
    abstract public void setHttpProxyUser(String user);
    abstract public void setHttpProxyPassword(String password);
    abstract public void setMaxFetchKBSec(int maxFetchKBSec);
    abstract public void setMaxLengthBytes(long maxLengthBytes);
    abstract public void setSendRange(boolean sendRange);
    abstract public void setSendIfModifiedSince(boolean sendIfModifiedSince);
    abstract public void setSendIfNoneMatch(boolean sendIfNoneMatch);
    abstract public void setSendReferer(boolean sendReferer);
    abstract public void setShouldFetchBodyRule(DecideRule rule);
    abstract public void setSoTimeoutMs(int timeout);
    abstract public void setTimeoutSeconds(int timeout);
    abstract public void setSslTrustLevel(TrustLevel trustLevel);
    abstract public void setUseHTTP11(boolean useHTTP11);
    abstract public void setUserAgentProvider(UserAgentProvider provider);
    abstract public void setSendConnectionClose(boolean sendClose);
}
