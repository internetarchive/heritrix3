package org.archive.modules.extractor;

import org.apache.commons.io.IOUtils;
import org.archive.crawler.framework.CrawlController;
import org.archive.crawler.io.UriProcessingFormatter;
import org.archive.crawler.reporting.CrawlerLoggerModule;
import org.archive.format.warc.WARCConstants;
import org.archive.io.warc.WARCRecordInfo;
import org.archive.modules.CrawlURI;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.Channels;
import java.util.ArrayList;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

public class ExtractorYoutubeDLTest extends ContentExtractorTestBase {

    protected String getTestUri() {
        return "https://www.youtube.com/watch?v=i08NNO-DPgg";
    }
    protected String getTestResourceFileName() {
        return "ExtractorYoutubeDL.json";
    }
    protected String getTestResourceSha1() { return "WFD7RIFCGNFVAWBEWLF6T2HXPXDEZY45"; }

    /**
     * Test that we have the expected WARC Metadata Record given a json output from yt-dlp
     * @throws Exception
     */
    public void testBuildRecord() throws Exception {
        CrawlURI testUri = CrawlURI.fromHopsViaString(getTestUri());
        InputStream is = this.getClass().getClassLoader().getResourceAsStream(getTestResourceFileName());
        byte[] json_results = IOUtils.toByteArray(is);
        ExtractorYoutubeDL ex = (ExtractorYoutubeDL)extractor;
        OutputStream os = Channels.newOutputStream(ex.getLocalTempFile().getChannel());
        IOUtils.write(json_results, os);
        WARCRecordInfo record = ex.buildRecord(testUri, null);

        assertEquals(record.getUrl(),"youtube-dl:" + getTestUri());
        assertEquals(record.getType(), WARCConstants.WARCRecordType.metadata);
        assertEquals(record.getMimetype(),"application/vnd.youtube-dl_formats+json;charset=utf-8");

        //Test input file is the same content as the content to be written to warc
        byte[] output_array = IOUtils.toByteArray(record.getContentStream());
        long json_len = json_results.length;
        long out_len = output_array.length;
        org.junit.Assert.assertArrayEquals(json_results, output_array);
    }

    /**
     * Test that the resuling log line is as expected, and the resulting hash string matches
     * @throws Exception
     */
    public void testPostWrite() throws Exception {

        CrawlURI testUri = CrawlURI.fromHopsViaString(getTestUri());
        InputStream is = this.getClass().getClassLoader().getResourceAsStream(getTestResourceFileName());
        byte[] json_results = IOUtils.toByteArray(is);
        ExtractorYoutubeDL ex = (ExtractorYoutubeDL)extractor;
        OutputStream os = Channels.newOutputStream(ex.getLocalTempFile().getChannel());
        IOUtils.write(json_results, os);

        WARCRecordInfo record = ex.buildRecord(testUri, null);

        ex.controller.setLoggerModule(new CrawlerLoggerModule() {
            @Override
            public Logger getUriProcessing() {
                Logger logger = Logger.getLogger(ExtractorYoutubeDL.class.getName());

                logger.setLevel(Level.ALL);
                return logger;
            }
        });
        Logger logger = ex.controller.getLoggerModule().getUriProcessing();
        TestLogHandler logHandler = new TestLogHandler();
        logger.addHandler(logHandler);
        UriProcessingFormatter formatter = new UriProcessingFormatter(true);

        ex.setLogMetadataRecord(false);
        ex.postWrite(record, testUri);
        assert(logHandler.getLines().length == 0);

        ex.setLogMetadataRecord(true);
        ex.postWrite(record, testUri);
        LogRecord[] logLines = logHandler.getLines();
        assert(logHandler.getLines().length>0);
        String message = formatter.format(logHandler.getLines()[0]);
        String expected_crawl_log_line = "   204     434699 youtube-dl:https://www.youtube.com/watch?v=i08NNO-DPgg I https://www.youtube.com/watch?v=i08NNO-DPgg application/vnd.youtube-dl_formats+json #000 - sha1:WFD7RIFCGNFVAWBEWLF6T2HXPXDEZY45 - youtube-dl: {\"contentSize\":434699}";
        assert(message.contains(expected_crawl_log_line));



    }

    @Override
    protected Extractor makeExtractor() {
        CrawlController controller = new CrawlController();
        ExtractorYoutubeDL ex = new ExtractorYoutubeDL();
        ex.setCrawlController(controller);

        UriErrorLoggerModule ulm = new UnitTestUriLoggerModule();
        ex.setLoggerModule(ulm);

        return ex;
    }
}

/**
 * Helper class to let us inspect the individual LogRecords
 */
class TestLogHandler extends Handler
{
    ArrayList<LogRecord> logLines;
    public TestLogHandler() {
        super();
        this.logLines = new ArrayList<LogRecord>();
    }

    public LogRecord[] getLines() {
        return this.logLines.toArray(new LogRecord[]{});
    }

    public void publish(LogRecord record) {
        this.logLines.add(record);
    }

    public void close(){}
    public void flush(){}
}