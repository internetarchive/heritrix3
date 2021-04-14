package org.archive.modules.recrawl.hbase;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.time.Instant;

import org.apache.hadoop.hbase.CellUtil;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.util.Bytes;
import org.archive.modules.CrawlURI;
import org.archive.modules.CrawlURI.FetchType;
import org.archive.modules.recrawl.FetchHistoryHelper;
import org.archive.modules.recrawl.RecrawlAttributeConstants;
import org.archive.net.UURIFactory;
import org.junit.Before;
import org.junit.Test;

public class MultiColumnRecrawlDataSchemaTest {

  private static final long NOW = Instant.now().toEpochMilli();
  private CrawlURI crawlUri;
  private MultiColumnRecrawlDataSchema schema;

  @Before
  public void setUp() throws Exception {
    crawlUri = new CrawlURI(UURIFactory.getInstance("https://www.test.example.com/"));
    crawlUri.setFetchType(FetchType.HTTP_GET);
    schema = new MultiColumnRecrawlDataSchema();
  }

  @Test
  public void itPutsLastModifiedHeaderIfPresent() {
    String lastModified = "Mon, 12 Apr 2021 01:00:000 UTC";
    crawlUri.putHttpResponseHeader(RecrawlAttributeConstants.A_LAST_MODIFIED_HEADER, lastModified);
    crawlUri.setFetchCompletedTime(NOW);

    Put put = schema.createPut(crawlUri);
    assertFalse(put.get(schema.columnFamily, MultiColumnRecrawlDataSchema.COLUMN_LAST_MODIFIED).isEmpty());
    assertEquals(Bytes.toLong(CellUtil.cloneValue(put.get(schema.columnFamily, MultiColumnRecrawlDataSchema.COLUMN_LAST_MODIFIED).get(0))), FetchHistoryHelper.parseHttpDate(lastModified));
  }

  @Test
  public void itPutsCrawlFetchedTimeIfLastModifiedHeaderIsNull() {
    crawlUri.putHttpResponseHeader(RecrawlAttributeConstants.A_LAST_MODIFIED_HEADER, null);
    crawlUri.setFetchCompletedTime(NOW);

    Put put = schema.createPut(crawlUri);
    assertFalse(put.get(schema.columnFamily, MultiColumnRecrawlDataSchema.COLUMN_LAST_MODIFIED).isEmpty());
    assertEquals(Bytes.toLong(CellUtil.cloneValue(put.get(schema.columnFamily, MultiColumnRecrawlDataSchema.COLUMN_LAST_MODIFIED).get(0))), NOW / 1000);
  }

  @Test
  public void itPutsCrawlFetchedTimeIfLastModifiedHeaderIsMissing() {
    crawlUri.setFetchCompletedTime(NOW);

    Put put = schema.createPut(crawlUri);
    assertFalse(put.get(schema.columnFamily, MultiColumnRecrawlDataSchema.COLUMN_LAST_MODIFIED).isEmpty());
    assertEquals(Bytes.toLong(CellUtil.cloneValue(put.get(schema.columnFamily, MultiColumnRecrawlDataSchema.COLUMN_LAST_MODIFIED).get(0))), NOW / 1000);
  }

  @Test
  public void itSkipsLastModifiedIfBothHeaderAndFetchCompletedTimeAreMissing() {
    Put put = schema.createPut(crawlUri);
    assertTrue(put.get(schema.columnFamily, MultiColumnRecrawlDataSchema.COLUMN_LAST_MODIFIED).isEmpty());
  }
}
