package org.archive.modules.writer;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

import org.archive.modules.CrawlMetadata;
import org.archive.modules.fetcher.DefaultServerCache;
import org.archive.spring.ConfigPath;
import org.archive.util.FileUtils;
import org.junit.jupiter.api.io.TempDir;

public class WARCWriterChainProcessorTest extends WARCWriterProcessorTest {
    @TempDir
    Path tempDir;

    @Override
    protected Object makeModule() throws Exception {
        WARCWriterChainProcessor result = makeTestWARCWriterChainProcessor(tempDir);
        result.start();
        return result;
    }

    public static WARCWriterChainProcessor makeTestWARCWriterChainProcessor(Path tempDir)
            throws IOException {
        File tmp = tempDir.toFile();
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
