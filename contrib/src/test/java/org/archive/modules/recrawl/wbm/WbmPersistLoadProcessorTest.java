package org.archive.modules.recrawl.wbm;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import junit.framework.TestCase;

import org.apache.http.HttpResponse;
import org.apache.http.ProtocolVersion;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.message.BasicHttpResponse;
import org.apache.http.message.BasicStatusLine;
import org.archive.modules.CoreAttributeConstants;
import org.archive.modules.CrawlURI;
import org.archive.modules.ProcessResult;
import org.archive.modules.recrawl.FetchHistoryHelper;
import org.archive.modules.recrawl.FetchHistoryProcessor;
import org.archive.modules.recrawl.RecrawlAttributeConstants;
import org.archive.net.UURIFactory;
import org.archive.util.Base32;
import org.archive.util.DateUtils;
import org.easymock.EasyMock;

import com.google.common.util.concurrent.ExecutionList;

/**
 * unit test for {@link WbmPersistLoadProcessor}.
 * 
 * TODO:
 * <ul>
 * <li>test for pathological cases: illegal chars, incorrect length, etc.
 * <li>test if connection is properly released back to the pool for all possible cases.
 * </ul>
 * @author kenji
 *
 */
public class WbmPersistLoadProcessorTest extends TestCase {

  public void testBuildURL() throws Exception {
    WbmPersistLoadProcessor t = new WbmPersistLoadProcessor();
    t.setQueryURL("http://web.archive.org/cdx/search/cdx?url=$u&startDate=$s&limit=1");
    final String URL = "http://archive.org/";
    String url = t.buildURL(URL);
    System.err.println(url);
    assertTrue("has encode URL", Pattern.matches(".*[&?]url="+URLEncoder.encode(URL, "UTF-8")+"([&].*)?", url));
    assertTrue("has startDate", Pattern.matches(".*[&?]startDate=\\d{14}([&].*)?", url));
    assertTrue("has limit", Pattern.matches(".*[&?]limit=\\d+([&].*)?", url));
    //assertTrue("has last=true", Pattern.matches(".*[&?]last=true([&].*)?", url));
  }
  
  /**
   * stub HttpResponse for normal case.
   * @author kenji
   *
   */
  public static class TestNormalHttpResponse extends BasicHttpResponse {
    public static final String EXPECTED_TS = "20121101155310";
    public static final String EXPECTED_HASH = "GHN5VKF3TBKNSEZTASOM23BJRTKFFNJK";
    public TestNormalHttpResponse() {
      super(new BasicStatusLine(new ProtocolVersion("HTTP", 1, 0), 200, "OK"));
      setEntity(new ByteArrayEntity(
	  ("org,archive)/ "+EXPECTED_TS+" http://archive.org/ text/html 200 "+
	      EXPECTED_HASH+" - - 6908 982548871 "+
	      "google.es-20121101-155506/IA-FOC-google.es-20121101073708-00001.warc.gz\n"
	  ).getBytes()
      ));
    }
  }
  
  protected Map<String, Object> getFetchHistory(CrawlURI curi, int idx) {
    Map<String, Object> data = curi.getData();
    @SuppressWarnings("unchecked")
    Map<String, Object>[] historyArray = (Map[])data.get(RecrawlAttributeConstants.A_FETCH_HISTORY);
    assertNotNull(historyArray);
    Map<String, Object> history = historyArray[idx];
    return history;
  }
  
  private byte[] sha1Digest(String text) throws NoSuchAlgorithmException {
      MessageDigest md = MessageDigest.getInstance("sha1");
      return md.digest(text.getBytes());
  }

  /**
   * this test is disabled because it talks to production CDX server.
   * @throws Exception
   */
  public void _testInnerProcessResultSingleShotWithMock() throws Exception {
    // because AbstractHttpClient marks most execute(...) methods final, it is very tiresome to implement
    // a stub by implementing HttpClient interface. So I use EasyMock.
    HttpClient client = EasyMock.createMock(HttpClient.class);
    HttpResponse testResponse = new TestNormalHttpResponse();
    EasyMock.expect(client.execute((HttpUriRequest)EasyMock.notNull())).andReturn(testResponse);
    EasyMock.replay(client);
    
    final String CONTENT_DIGEST_SCHEME = "sha1:";
    WbmPersistLoadProcessor t = new WbmPersistLoadProcessor();
    t.setHttpClient(client);
    t.setContentDigestScheme(CONTENT_DIGEST_SCHEME);
    CrawlURI curi = new CrawlURI(UURIFactory.getInstance("http://archive.org/"));
    
    // put history entry newer than being loaded (i.e. loaded history entry will not be used for FetchHistoryProcessor
    // check below.
    long expected_ts = DateUtils.parse14DigitDate(TestNormalHttpResponse.EXPECTED_TS).getTime();
    Map<String, Object>[] fetchHistory = (Map[])curi.getData().get(RecrawlAttributeConstants.A_FETCH_HISTORY);
    if (fetchHistory == null) {
      fetchHistory = new HashMap[2];
      curi.getData().put(RecrawlAttributeConstants.A_FETCH_HISTORY, fetchHistory);
    }
    final byte[] digestValue0 = sha1Digest("0");
    final byte[] digestValue1 = sha1Digest("1");
    fetchHistory[0] = new HashMap<String, Object>();
    fetchHistory[0].put(FetchHistoryHelper.A_TIMESTAMP, expected_ts + 2000);
    fetchHistory[0].put(CoreAttributeConstants.A_FETCH_BEGAN_TIME, expected_ts + 2000);
    fetchHistory[0].put(RecrawlAttributeConstants.A_CONTENT_DIGEST,
        CONTENT_DIGEST_SCHEME + Base32.encode(digestValue0));
    fetchHistory[1] = new HashMap<String, Object>();
    fetchHistory[1].put(FetchHistoryHelper.A_TIMESTAMP, expected_ts - 2000);
    fetchHistory[1].put(RecrawlAttributeConstants.A_CONTENT_DIGEST,
        CONTENT_DIGEST_SCHEME + Base32.encode(digestValue1));
    
    ProcessResult result = t.innerProcessResult(curi);
    assertEquals("result is PROCEED", ProcessResult.PROCEED, result);
    
    // newly loaded history entry should fall in between two existing entries (index=1)
    Map<String, Object> history = getFetchHistory(curi, 1);
    assertNotNull("history", history);
    String hash = (String)history.get(RecrawlAttributeConstants.A_CONTENT_DIGEST);
    assertEquals("CONTENT_DIGEST", CONTENT_DIGEST_SCHEME+TestNormalHttpResponse.EXPECTED_HASH, hash);
    
    Long ts = (Long)history.get(FetchHistoryHelper.A_TIMESTAMP);
    assertNotNull("ts is non-null", ts);
    assertEquals("'ts' has expected timestamp", expected_ts, ts.longValue());

    // Check compatibility with FetchHistoryProcessor.
    // TODO: This is not testing WbmPersistLoadProcessor - only testing stub fetchHistory
    // setup above (OK as long as it matches WbmPersistLoadProcessor). We need a separate
    // test method.
    curi.setFetchStatus(200);
    curi.setFetchBeginTime(System.currentTimeMillis());
    // FetchHistoryProcessor once failed for a revisit case. We'd need to test other cases
    // too (TODO).
    curi.setContentDigest("sha1", digestValue0);
    FetchHistoryProcessor fhp = new FetchHistoryProcessor();
    fhp.process(curi);
  }
  
  public void testInnerProcessResultSingleShotWithRealServer() throws Exception {
    WbmPersistLoadProcessor t = new WbmPersistLoadProcessor();
    //CrawlURI curi = new CrawlURI(UURIFactory.getInstance("http://archive.org/"));
    CrawlURI curi = new CrawlURI(UURIFactory.getInstance("http://www.mext.go.jp/null.gif"));
    ProcessResult result = t.innerProcessResult(curi);
    Map<String, Object> history = getFetchHistory(curi, 0);
    assertNotNull("getFetchHistory returns non-null", history);
    String hash = (String)history.get(RecrawlAttributeConstants.A_CONTENT_DIGEST);
    assertNotNull("CONTENT_DIGEST is non-null", hash);
    assertTrue("CONTENT_DIGEST starts with scheme", hash.startsWith(t.getContentDigestScheme()));
    assertEquals("CONTENT_DIGEST is a String of length 32", 32, hash.substring(t.getContentDigestScheme().length()).length());
    
    assertEquals("should always return PROCEED", ProcessResult.PROCEED, result);
  }
  
  public static class LoadTask implements Runnable {
    private WbmPersistLoadProcessor p;
    private String uri;
    public LoadTask(WbmPersistLoadProcessor p, String uri) {
      this.p = p;
      this.uri = uri;
    }
    @Override
    public void run() {
      try {
	    CrawlURI curi = new CrawlURI(UURIFactory.getInstance(this.uri));
	    p.innerProcessResult(curi);
	    //System.err.println(curi.toString());
      } catch (Exception ex) {
	    ex.printStackTrace();
      }
    }
  }
  
  /**
   * test for performance.
   * not named as test case. run this through main().
   * @throws Exception
   */
  public void measureInnerProcessResultMany() throws Exception {
    final WbmPersistLoadProcessor t = new WbmPersistLoadProcessor();
    InputStream is = getClass().getResourceAsStream("/test-url-list.txt");
    BufferedReader br = new BufferedReader(new InputStreamReader(is));
    String line;
    int nurls = 0;
    ExecutorService executor = Executors.newFixedThreadPool(100);
    ExecutionList tasks = new ExecutionList();
    while ((line = br.readLine()) != null) {
      tasks.add(new LoadTask(t, line), executor);
      nurls++;
    }
    long t0 = System.currentTimeMillis();
    tasks.execute();
    executor.awaitTermination(30, TimeUnit.SECONDS);
    long el = System.currentTimeMillis() - t0;
    System.err.println(nurls + " urls, time=" + el + "ms (" + (nurls / (el / 1000.0)) + " URI/s");
  }
  
  public static void main() throws Exception {
    (new WbmPersistLoadProcessorTest()).measureInnerProcessResultMany();
  }
}
