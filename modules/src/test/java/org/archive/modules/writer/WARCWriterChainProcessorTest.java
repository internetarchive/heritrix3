package org.archive.modules.writer;

import java.io.File;
import java.io.IOException;

import org.archive.modules.CrawlMetadata;
import org.archive.modules.fetcher.DefaultServerCache;
import org.archive.spring.ConfigPath;
import org.archive.util.FileUtils;
import org.archive.util.TmpDirTestCase;

public class WARCWriterChainProcessorTest extends WARCWriterProcessorTest {

    @Override
    protected Object makeModule() throws Exception {
        WARCWriterChainProcessor result = makeTestWARCWriterChainProcessor();
        result.start();
        return result;
    }

    public static WARCWriterChainProcessor makeTestWARCWriterChainProcessor()
            throws IOException {
        File tmp = TmpDirTestCase.tmpDir();
        tmp = new File(tmp, WARCWriterChainProcessorTest.class.getSimpleName());
        FileUtils.ensureWriteableDirectory(tmp);

        WARCWriterChainProcessor result = new WARCWriterChainProcessor();
        result.setDirectory(new ConfigPath("test", tmp.getAbsolutePath()));
        result.setServerCache(new DefaultServerCache());
        CrawlMetadata metadata = new CrawlMetadata();
        metadata.afterPropertiesSet();
        result.setMetadataProvider(metadata);
        return result;
    }
}
