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

package org.archive.modules.writer;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;

import org.archive.io.WriterPool;
import org.archive.io.WriterPoolMember;
import org.archive.io.WriterPoolSettings;
import org.archive.io.warc.WARCRecordInfo;
import org.archive.io.warc.WARCWriter;
import org.archive.io.warc.WARCWriterPoolSettingsData;
import org.archive.modules.CrawlMetadata;
import org.archive.modules.CrawlURI;
import org.archive.modules.CrawlURI.FetchType;
import org.archive.modules.ProcessorTestBase;
import org.archive.modules.fetcher.DefaultServerCache;
import org.archive.net.UURIFactory;
import org.archive.spring.ConfigPath;
import org.archive.uid.RecordIDGenerator;
import org.archive.uid.UUIDGenerator;
import org.archive.util.FileUtils;
import org.archive.util.TmpDirTestCase;

/**
 * Unit test for {@link WARCWriterProcessor}.
 *
 * @contributor pjack
 * @contributor kenji
 */
public class WARCWriterProcessorTest extends ProcessorTestBase {

    RecordIDGenerator generator = new UUIDGenerator();
    @Override
    protected Object makeModule() throws Exception {
        WARCWriterProcessor result = newTestWarcWriter("WARCWriterProcessorTest");
        result.start();
        return result;
    }

    public static WARCWriterProcessor newTestWarcWriter(String name) throws IOException {
        File tmp = TmpDirTestCase.tmpDir();
        tmp = new File(tmp, name);
        FileUtils.ensureWriteableDirectory(tmp);

        WARCWriterProcessor result = new WARCWriterProcessor();
        result.setDirectory(new ConfigPath("test",tmp.getAbsolutePath()));
        result.setServerCache(new DefaultServerCache());
        CrawlMetadata metadata = new CrawlMetadata();
        metadata.afterPropertiesSet();
        result.setMetadataProvider(metadata);
        return result;
    }

    @Override
    protected void verifySerialization(Object first, byte[] firstBytes, 
            Object second, byte[] secondBytes) throws Exception {

    }

    /**
     * test if {@link WARCWriterProcessor} recovers on I/O error.
     */
    public void testResilientOnError() throws Exception {
        // override setupPool() to use test version of WARCWriter.
        final WARCWriterProcessor wwp = new WARCWriterProcessor() {
            protected void setupPool(AtomicInteger serialNo) {
                setPool(new TestWriterPool(this, 1));
            }
        };
        wwp.start();
        final CrawlURI curi = new CrawlURI(UURIFactory.getInstance("http://test.com/"));
        // necessary to pass shouldProcess() test.
        curi.setFetchStatus(200);
        curi.setContentSize(1);
        // necessary to pass shouldWrite() test.
        curi.setFetchType(FetchType.HTTP_GET);
        // make a first call. FailWARCWriter throws an IOException
        // upon first call to getPosition() - this situation can be
        // easily overlooked as method name does not suggest it's
        // writing anything to disk.
        wwp.process(curi);
        Collection<Throwable> failures1 = curi.getNonFatalFailures();
        assertEquals(1, failures1.size());

        // make second call. if the exception during previous call
        // caused any inconsistency, most likely outcome is second
        // call never returns.
        final Thread me = Thread.currentThread();
        Thread th = new Thread() {
            public void run() {
                // WARCWriterProcessor#process() will never
                // throw InterruptedException
                try {
                    wwp.process(curi);
                    // let parent thread know I'm done!
                    me.interrupt();
                    Thread.sleep(500); 
                } catch (InterruptedException ex) {
                }
            };
        };
        th.start();
        // wait 5 seconds for th to finish. it should not
        // take this long to finish.
        try {
            th.join(5000);
        } catch (InterruptedException ex) {
            // ok, th finished
            return;
        }
        fail("second process() call got blocked too long");
    }

    public void testStats() throws IOException, InterruptedException {
        WARCWriterProcessor wwp = new WARCWriterProcessor();
        wwp.setMetadataProvider(new CrawlMetadata());
        DefaultServerCache serverCache = new DefaultServerCache();
        serverCache.getHostFor("test.com").setIP(InetAddress.getLoopbackAddress(), -1);
        wwp.setServerCache(serverCache);
        File workDir = new File(TmpDirTestCase.tmpDir(), "WARCWriterProcessorTest-testStats");
        org.apache.commons.io.FileUtils.deleteDirectory(workDir);
        wwp.setDirectory(new ConfigPath(null, workDir.getPath()));
        wwp.start();

        final CrawlURI curi = new CrawlURI(UURIFactory.getInstance("http://test.com/"));
        String responseBody = "<html><head><title>test.com</title></head>\r\n"
                + "<body><h1>test.com</h1></body></html>\r\n";
        String responseHeader = "HTTP/1.1 200 OK\r\n"
                + "Content-Type: text/html\r\n"
                + "Content-Length: " + responseBody.length() + "\r\n"
                + "\r\n";
        byte[] responseBytes = (responseHeader + responseBody).getBytes("ASCII");

        curi.setRecorder(getRecorder());
        curi.getRecorder().inputWrap(new ByteArrayInputStream(responseBytes));
        curi.getRecorder().getRecordedInput().readFully();
        curi.getRecorder().close();

        curi.setFetchStatus(200);
        curi.setFetchType(FetchType.HTTP_GET);
        curi.setContentSize(responseBytes.length);

        wwp.process(curi);

        System.out.println("warcsDir=" + workDir);
        File warc = new File(workDir, "warcs").listFiles(new FileFilter() {
            public boolean accept(File pathname) {
                return pathname.getName().endsWith(".warc.gz.open");
            }
        })[0];
        System.out.println("warc=" + warc);
        System.out.println("stats=" + wwp.getStats());

        // stats={request={numRecords=1, totalBytes=257, contentBytes=0, sizeOnDisk=10}, metadata={numRecords=1, totalBytes=333, contentBytes=35, sizeOnDisk=10}, response={numRecords=1, totalBytes=217, contentBytes=0, sizeOnDisk=10}, totals={numRecords=3, totalBytes=807, contentBytes=35, sizeOnDisk=30}, warcinfo={numRecords=0, totalBytes=0, contentBytes=0, sizeOnDisk=0}}
        assertEquals(1, wwp.getStats().get("warcinfo").get("numRecords").get());
        assertEquals(1, wwp.getStats().get("response").get("numRecords").get());
        assertEquals(1, wwp.getStats().get("request").get("numRecords").get());
        assertEquals(1, wwp.getStats().get("metadata").get("numRecords").get());
        assertEquals(4, wwp.getStats().get("totals").get("numRecords").get());
        assertEquals(responseBytes.length, wwp.getStats().get("response").get("contentBytes").get());

        // XXX fails currently, needs https://github.com/iipc/webarchive-commons/pull/51
        // assertEquals(warc.length(), wwp.getStats().get("totals").get("sizeOnDisk").get());
    }

    /**
     * WARCWriter whose getPosition() always fails.
     * It simulates disk full during last write() (it didn't fail
     * because byte are kept in internal buffer and got flushed by
     * getPosition()'s calling flush().
     */
    public static class FailWARCWriter extends WARCWriter {
        public FailWARCWriter(AtomicInteger serial, WARCWriterPoolSettingsData settings) {
            super(serial, settings);
        }

        @Override
        public void writeRecord(WARCRecordInfo recordInfo) throws IOException {
            throw new IOException("pretend no space left on device");
        }
    }
    /**
     * replacement WriterPool that injects FailWARCWriter
     * @contributor kenji
     */
    public class TestWriterPool extends WriterPool {
        public TestWriterPool(WriterPoolSettings settings, int maxActive) {
            super(new AtomicInteger(), settings, maxActive, 100);
        }
        @SuppressWarnings("unchecked")
        @Override
        protected WriterPoolMember makeWriter() {
            return new FailWARCWriter(serialNo, 
                    new WARCWriterPoolSettingsData("","",10,false,Arrays.asList(new File(".")),Collections.EMPTY_LIST,generator));
        }
    }

}
