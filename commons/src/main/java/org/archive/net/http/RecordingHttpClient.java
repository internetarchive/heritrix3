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

package org.archive.net.http;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;

import org.apache.http.ConnectionReuseStrategy;
import org.apache.http.HttpException;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpRequestRetryHandler;
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
import org.archive.modules.fetcher.HostResolver;
import org.archive.util.Recorder;

public class RecordingHttpClient extends DefaultHttpClient {
    public RecordingHttpClient() {
        super();
        
        // never retry requests (heritrix handles this elsewhere)
        setHttpRequestRetryHandler(new HttpRequestRetryHandler() {
            @Override
            public boolean retryRequest(IOException exception, int executionCount,
                    HttpContext context) {
                return false;
            }
        });
        
        // never reuse (no keep-alive)
        setReuseStrategy(new ConnectionReuseStrategy() {
            @Override
            public boolean keepAlive(HttpResponse response, HttpContext context) {
                return false;
            }
        });
    }
    
    @Override
    protected ClientConnectionManager createClientConnectionManager() {
        return new BasicClientConnectionManager(SchemeRegistryFactory.createDefault()) {
            @Override
            protected ClientConnectionOperator createConnectionOperator(SchemeRegistry schreg) {
                return new RecordingClientConnectionOperator(schreg);
            }
        };
    }
    
    protected static class RecordingClientConnectionOperator extends DefaultClientConnectionOperator {
        protected RecordingClientConnectionOperator(SchemeRegistry schemes) {
            this(schemes, new DnsResolver() {
                @Override
                public InetAddress[] resolve(String host) throws UnknownHostException {
                    // XXX this is how the old system does it, but seems ugly, tangles up modules with engine, etc
                    Thread currentThread = Thread.currentThread();
                    if (currentThread instanceof HostResolver) {
                        HostResolver resolver = (HostResolver)currentThread;
                        return new InetAddress[] {resolver.resolve(host)};
                    } else {
                        return null;
                    }
                }
            });
        }
    
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