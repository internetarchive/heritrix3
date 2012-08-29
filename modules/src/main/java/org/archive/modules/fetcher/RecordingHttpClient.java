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
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.logging.Logger;

import org.apache.http.ConnectionReuseStrategy;
import org.apache.http.HttpException;
import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.Credentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.ClientConnectionOperator;
import org.apache.http.conn.DnsResolver;
import org.apache.http.conn.OperatedClientConnection;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.BasicClientConnectionManager;
import org.apache.http.impl.conn.DefaultClientConnection;
import org.apache.http.impl.conn.DefaultClientConnectionOperator;
import org.apache.http.impl.conn.SchemeRegistryFactory;
import org.apache.http.io.SessionInputBuffer;
import org.apache.http.io.SessionOutputBuffer;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.HttpContext;
import org.archive.modules.net.CrawlHost;
import org.archive.modules.net.ServerCache;
import org.archive.util.Recorder;

/**
 * 
 * @contributor nlevitt
 */
public class RecordingHttpClient extends DefaultHttpClient {
    private ServerCache serverCache;

    /**
     * 
     * @param serverCache
     */
    public RecordingHttpClient(ServerCache serverCache) {
        super();
        
        this.setServerCache(serverCache);
        
        // XXX uhh? see HeritrixHttpMethodRetryHandler ??
//        setHttpRequestRetryHandler(new HttpRequestRetryHandler() {
//            @Override
//            public boolean retryRequest(IOException exception, int executionCount,
//                    HttpContext context) {
//                return false;
//            }
//        });
        
        // never reuse (no keep-alive)
        setReuseStrategy(new ConnectionReuseStrategy() {
            @Override
            public boolean keepAlive(HttpResponse response, HttpContext context) {
                return false;
            }
        });
    }
    
    protected ServerCache getServerCache() {
        return serverCache;
    }

    protected void setServerCache(ServerCache serverCache) {
        this.serverCache = serverCache;
    }

    @Override
    protected ClientConnectionManager createClientConnectionManager() {
        return new BasicClientConnectionManager(SchemeRegistryFactory.createDefault()) {
            @Override
            protected ClientConnectionOperator createConnectionOperator(SchemeRegistry schreg) {
                return new RecordingClientConnectionOperator(schreg, 
                        new ServerCacheResolver(RecordingHttpClient.this.getServerCache()));
            }
        };
    }
    
    // We have separate credentials providers per thread so that FetchHTTP2 can
    // configure credentials, do the fetch, and clear the credentials, without
    // affecting fetches going on in other threads.
    @Override
    protected CredentialsProvider createCredentialsProvider() {
        return new ThreadLocalCredentialsProvider();
    }    
    
    protected class ThreadLocalCredentialsProvider implements CredentialsProvider {
        protected ThreadLocal<CredentialsProvider> threadCreds = new ThreadLocal<CredentialsProvider>() {
            @Override
            protected CredentialsProvider initialValue() {
                return RecordingHttpClient.super.createCredentialsProvider();
            }
        };

        @Override
        public void setCredentials(AuthScope authscope, Credentials credentials) {
            threadCreds.get().setCredentials(authscope, credentials);
        }

        @Override
        public Credentials getCredentials(AuthScope authscope) {
            return threadCreds.get().getCredentials(authscope);
        }

        @Override
        public void clear() {
            threadCreds.get().clear();
        }
    }

    /**
     * Implementation of {@link DnsResolver} that uses the server cache which is
     * normally expected to have been populated by FetchDNS.
     * 
     * @contributor nlevitt
     */
    public static class ServerCacheResolver implements DnsResolver {
        protected static Logger logger = Logger.getLogger(DnsResolver.class.getName());
        protected ServerCache serverCache;

        public ServerCacheResolver(ServerCache serverCache) {
            this.serverCache = serverCache;
        }

        @Override
        public InetAddress[] resolve(String host) throws UnknownHostException {
            CrawlHost crawlHost = this.serverCache.getHostFor(host);
            if (crawlHost != null) {
                InetAddress ip = crawlHost.getIP();
                if (ip != null) {
                    return new InetAddress[] {ip};
                }
            }

            logger.info("host " + host + " is not in serverCache, allowing java to resolve it");
            return new InetAddress[] {InetAddress.getByName(host)};
        }
    }

    /**
     * 
     * @contributor nlevitt
     */
    protected static class RecordingClientConnectionOperator extends DefaultClientConnectionOperator {
        public RecordingClientConnectionOperator(SchemeRegistry schemes,
                DnsResolver dnsResolver) {
            super(schemes, dnsResolver);
        }

        @Override
        public OperatedClientConnection createConnection() {
            return new DefaultClientConnection() {
                @Override
                protected SessionInputBuffer createSessionInputBuffer(Socket socket, int buffersize, HttpParams params) throws IOException {
                    return new RecordingSocketInputBuffer(socket, buffersize, params);
                }
                
                @Override
                protected SessionOutputBuffer createSessionOutputBuffer(Socket socket, int buffersize, HttpParams params) throws IOException {
                    return new RecordingSocketOutputBuffer(socket, buffersize, params);
                }
                
                @Override
                public void receiveResponseEntity(HttpResponse response)
                        throws HttpException, IOException {
                    // XXX is this null check necessary? what happens if proxied, etc?
                    Recorder recorder = Recorder.getHttpRecorder();
                    if (recorder != null) {
                        recorder.markContentBegin();
                    }
                    
                    super.receiveResponseEntity(response);
                }
            };
        }
    }

}