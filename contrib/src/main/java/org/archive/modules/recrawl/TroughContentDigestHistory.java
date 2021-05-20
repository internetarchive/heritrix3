package org.archive.modules.recrawl;

import static org.archive.modules.recrawl.RecrawlAttributeConstants.A_ORIGINAL_DATE;
import static org.archive.modules.recrawl.RecrawlAttributeConstants.A_ORIGINAL_URL;
import static org.archive.modules.recrawl.RecrawlAttributeConstants.A_WARC_RECORD_ID;

import java.net.MalformedURLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.archive.crawler.event.CrawlStateEvent;
import org.archive.modules.CrawlURI;
import org.archive.modules.writer.WARCWriterChainProcessor;
import org.archive.spring.HasKeyedProperties;
import org.archive.spring.KeyedProperties;
import org.archive.trough.TroughClient;
import org.archive.trough.TroughClient.TroughNoReadUrlException;
import org.springframework.context.ApplicationListener;

/**
 * AbstractContentDigestHistory implementation for trough.
 * 
 * <p>To use, define a {@code TroughContentDigestHistory} top-level bean in your
 * crawler-beans.cxml, then add {@link ContentDigestHistoryLoader} and
 * {@link ContentDigestHistoryStorer} to your fetch chain, sandwiching the
 * {@link WARCWriterChainProcessor}. In other words, follow the directions at
 * <a href="https://github.com/internetarchive/heritrix3/wiki/Duplication%20Reduction%20Processors">https://github.com/internetarchive/heritrix3/wiki/Duplication%20Reduction%20Processors</a>
 * but replace the {@link BdbContentDigestHistory} bean with a
 * {@code TroughContentDigestHistory} bean.
 * 
 * <p>To understand how to use trough as a client, see:
 * <ul>
 * <li> <a href="https://github.com/internetarchive/trough/wiki/Some-Notes-About-Trough">https://github.com/internetarchive/trough/wiki/Some-Notes-About-Trough</a>
 * <li> <a href="https://github.com/internetarchive/trough/blob/repl/trough/client.py">https://github.com/internetarchive/trough/blob/repl/trough/client.py</a>
 * </ul>
 * 
 * @see <a href="https://github.com/internetarchive/warcprox/blob/c70bf2e2b93/warcprox/dedup.py#L480">trough dedup implementation in warcprox</a>
 */
public class TroughContentDigestHistory extends AbstractContentDigestHistory implements HasKeyedProperties, ApplicationListener<CrawlStateEvent> {
    private static final Logger logger = Logger.getLogger(TroughContentDigestHistory.class.getName());

    protected KeyedProperties kp = new KeyedProperties();
    public KeyedProperties getKeyedProperties() {
        return kp;
    }

    public void setSegmentId(String segmentId) {
        kp.put("segmentId", segmentId);
    }
    public String getSegmentId() {
        return (String) kp.get("segmentId");
    }

    /**
     * @param rethinkUrl url with schema rethinkdb:// pointing to
     *          trough configuration database
     */
    public void setRethinkUrl(String rethinkUrl) {
        kp.put("rethinkUrl", rethinkUrl);
    }
    public String getRethinkUrl() {
        return (String) kp.get("rethinkUrl");
    }

    protected TroughClient troughClient = null;

    protected TroughClient troughClient() throws MalformedURLException {
        if (troughClient == null) {
            troughClient = new TroughClient(getRethinkUrl(), 60 * 60);
            troughClient.start();
        }
        return troughClient;
    }

    protected static final String SCHEMA_ID = "warcprox-dedup-v1";
    protected static final String SCHEMA_SQL = "create table dedup (\n"
            + "    digest_key varchar(100) primary key,\n"
            + "    url varchar(2100) not null,\n"
            + "    date varchar(100) not null,\n"
            + "    id varchar(100));\n"; // warc record id

    @Override
    public void onApplicationEvent(CrawlStateEvent event) {
        switch(event.getState()) {
        case PREPARING:
            try {
                // initializes TroughClient and starts promoter thread as a side effect
                troughClient().registerSchema(SCHEMA_ID, SCHEMA_SQL);
            } catch (Exception e) {
                // can happen. hopefully someone else has registered it
                logger.log(Level.SEVERE, "will try to continue after problem registering schema " + SCHEMA_ID, e);
            }
            break;

        case FINISHED:
            /*
             * We do this here at FINISHED rather than at STOPPING time to make
             * sure every URL has had a chance to have its dedup info stored,
             * before committing the trough segment to permanent storage.
             *
             * (When modules implement org.springframework.context.Lifecycle,
             * stop() is called in the STOPPING phase, before all URLs are
             * necessarily finished processing.)
             */
            if (troughClient != null) {
                troughClient.stop();
                troughClient.promoteDirtySegments();
                troughClient = null;
            }
            break;

        default:
        }
    }

    @Override
    public void load(CrawlURI curi) {
        // make this call in all cases so that the value is initialized and
        // WARCWriterProcessor knows it should put the info in there
        HashMap<String, Object> contentDigestHistory = curi.getContentDigestHistory();

        try {
            String sql = "select * from dedup where digest_key = %s";
            List<Map<String, Object>> results = troughClient().read(getSegmentId(), sql, new String[] {persistKeyFor(curi)});
            if (!results.isEmpty()) {
                Map<String,Object> hist = new HashMap<String, Object>();
                hist.put(A_ORIGINAL_URL, results.get(0).get("url"));
                hist.put(A_ORIGINAL_DATE, results.get(0).get("date"));
                hist.put(A_WARC_RECORD_ID, results.get(0).get("id"));

                if (logger.isLoggable(Level.FINER)) {
                    logger.finer("loaded history by digest " + persistKeyFor(curi)
                                 + " for uri " + curi + " - " + hist);
                }
                contentDigestHistory.putAll(hist);
            }
        } catch (TroughNoReadUrlException e) {
            // this is totally normal at the beginning of the crawl, for example
            logger.log(Level.FINE, "problem retrieving dedup info from trough segment " + getSegmentId() + " for url " + curi, e);
        } catch (Exception e) {
            logger.log(Level.WARNING, "problem retrieving dedup info from trough segment " + getSegmentId() + " for url " + curi, e);
        }
    }

    protected static final String WRITE_SQL_TMPL = 
            "insert or ignore into dedup (digest_key, url, date, id) values (%s, %s, %s, %s);";

    @Override
    public void store(CrawlURI curi) {
        if (!curi.hasContentDigestHistory() || curi.getContentDigestHistory().isEmpty()) {
            return;
        }
        Map<String,Object> hist = curi.getContentDigestHistory();

        try {
            String digestKey = persistKeyFor(curi);
            Object url = hist.get(A_ORIGINAL_URL);
            Object date = hist.get(A_ORIGINAL_DATE);
            Object recordId = hist.get(A_WARC_RECORD_ID);
            Object[] values = new Object[] { digestKey, url, date, recordId };
            troughClient().write(getSegmentId(), WRITE_SQL_TMPL, values, SCHEMA_ID);
        } catch (Exception e) {
            logger.log(Level.WARNING, "problem writing dedup info to trough segment " + getSegmentId() + " for url " + curi, e);

        }
    }
}
