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

import org.apache.ftpserver.FtpServer;
import org.apache.ftpserver.FtpServerFactory;
import org.apache.ftpserver.ftplet.FtpException;
import org.apache.ftpserver.listener.ListenerFactory;
import org.apache.ftpserver.usermanager.impl.BaseUser;
import org.archive.modules.CrawlURI;
import org.archive.modules.ProcessorTestBase;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author pjack
 *
 */
public class FetchFTPTest extends ProcessorTestBase {
    @Test
    public void test() throws Exception {
        FetchFTP fetchFTP = new FetchFTP();
        fetchFTP.start();

        Path tmpDir = Files.createTempDirectory("heritrix-ftp-test");
        byte[] payload = "hello world".getBytes(UTF_8);
        Files.write(tmpDir.resolve("test.txt"), payload);

        int port = allocatePort();
        FtpServer server = startFtpServer(port, tmpDir);
        try {
            CrawlURI curi = makeCrawlURI("ftp://127.0.0.1:" + port + "/test.txt");
            fetchFTP.process(curi);
            assertEquals(payload.length, curi.getContentSize());
            assertEquals(226, curi.getFetchStatus());
            assertEquals("127.0.0.1", curi.getServerIP());
        } finally {
            server.stop();
            Files.delete(tmpDir.resolve("test.txt"));
            Files.delete(tmpDir);
            fetchFTP.stop();
        }
    }

    private FtpServer startFtpServer(int port, Path root) throws FtpException {
        ListenerFactory listenerFactory = new ListenerFactory();
        listenerFactory.setPort(port);
        FtpServerFactory serverFactory = new FtpServerFactory();
        serverFactory.addListener("default", listenerFactory.createListener());
        BaseUser user = new BaseUser();
        user.setName("anonymous");
        user.setHomeDirectory(root.toString());
        serverFactory.getUserManager().save(user);
        FtpServer server = serverFactory.createServer();
        server.start();
        return server;
    }

    private int allocatePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
