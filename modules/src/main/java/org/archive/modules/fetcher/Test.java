package org.archive.modules.fetcher;
import java.util.ArrayList;
import java.util.List;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.archive.modules.CrawlMetadata;
import org.archive.modules.CrawlURI;
import org.archive.net.UURIFactory;


public class Test {

    public static void main(String [] args) {
        
        //doNormal();
        //doH3("http://www.trec.ualberta.ca/robots.txt");
        doH3("http://www.trec.ualberta.ca/");
        
    }

    public static void doNormal() {
        try {
            CloseableHttpClient httpclient = HttpClients.createDefault();
            
            HttpGet httpget = new HttpGet("http://www.trec.ualberta.ca/");
            
            System.out.println(httpclient.execute(httpget));
        }
        catch (Exception exc) {
            exc.printStackTrace();
        }
    }
    
    public static void doH3(String url) {
        
        
        List<String> headers = new ArrayList<String>();
        headers.add("Accept: text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
        
        FetchHTTP fetcher = new FetchHTTP();
        
        
        fetcher.setSendIfModifiedSince(false);
        fetcher.setSendIfNoneMatch(false);
        fetcher.setTimeoutSeconds(7200);
        fetcher.setAcceptHeaders(headers);
        
        
        SimpleCookieStore scs = new SimpleCookieStore();
        
        scs.prepare();
        
        fetcher.setCookieStore(scs);
        fetcher.setServerCache(new DefaultServerCache());
        CrawlMetadata uap = new CrawlMetadata();
        uap.setOperator("a");
        uap.setOperatorContactUrl("http://a.com");
        uap.setUserAgentTemplate("Mozilla/5.0 (compatible; archive.org_bot; Archive-It; +@OPERATOR_CONTACT_URL@)");
        fetcher.setUserAgentProvider(uap);

        FetchHTTPRequest req;
        try {
            CrawlURI cauri =
                    new CrawlURI(UURIFactory.getInstance(url));
            
            req = new FetchHTTPRequest(fetcher, cauri);
            
            req.request.addHeader("Accept-Language", "*");
            //req.request.addHeader("User-Agent", "Mozilla/5.0 (compatible; archive.org_bot; Archive-It; +http://archive-it.org/files/site-owners.html)");
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }
        
        HttpResponse response = null;
        try {
            System.out.println(req.request);
            response = req.execute();
            System.out.println(response);
        } catch (Exception e) {
            e.printStackTrace();
        }
        
    }
    
}