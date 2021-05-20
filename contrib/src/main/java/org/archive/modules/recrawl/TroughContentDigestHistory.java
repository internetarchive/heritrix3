package org.archive.modules.recrawl;

import static org.archive.modules.recrawl.RecrawlAttributeConstants.A_ORIGINAL_DATE;
import static org.archive.modules.recrawl.RecrawlAttributeConstants.A_ORIGINAL_URL;
import static org.archive.modules.recrawl.RecrawlAttributeConstants.A_WARC_RECORD_ID;

import java.net.MalformedURLException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Enumeration;
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


    {
        setTroughSyncMaxBatchSize(400);
    }
    /**
     * @param troughSyncMaxBatchSize number of dedup rows to buffer before syncing to Trough.
     */
    public void setTroughSyncMaxBatchSize(int troughSyncMaxBatchSize) { kp.put("troughSyncMaxBatchSize", troughSyncMaxBatchSize); }
    public int getTroughSyncMaxBatchSize() { return (int) kp.get("troughSyncMaxBatchSize"); }

    {
        setTroughPromotionInterval(3600);
    }
    /**
     * @param troughPromotionInterval number of seconds between runs of the Trough Client promoter thread.
     */
    public void setTroughPromotionInterval(int troughPromotionInterval) { kp.put("troughPromotionInterval", troughPromotionInterval); }
    public int getTroughPromotionInterval() { return (int) kp.get("troughPromotionInterval"); }

    protected TroughClient troughClient = null;

    protected TroughClient troughClient() throws MalformedURLException {
        if (troughClient == null) {
            troughClient = new TroughClient(getRethinkUrl(), getTroughPromotionInterval());
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
    protected static final String WRITE_SQL_TMPL =
            "insert or ignore into dedup (digest_key, url, date, id)";

    // row count that avoids table scan if no deletes
    protected static final String COUNT_SQL =
            "SELECT MAX(_ROWID_) FROM dedup LIMIT 1;";
    protected static final String DROP_TABLE_DEDUP_SQL =
            "DROP table dedup;";
    protected static final String SELECT_ALL_SQL =
            "SELECT * FROM dedup;";
    protected static final String DEDUP_QUERY_SQL =
            "SELECT * FROM dedup WHERE digest_key = ? LIMIT 1";

    protected ConcurrentHashMap<String, Object> segmentCache = new ConcurrentHashMap<String, Object>();
    protected Connection dedupDbConnection = null;
    @Override
    public void onApplicationEvent(CrawlStateEvent event) {
        switch(event.getState()) {
        case PREPARING:
            try {
                dedupDbConnection = DriverManager.getConnection("jdbc:sqlite::memory:");
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
                //force all in memory segments to sync to trough before final promotion
                Enumeration<String> segments = segmentCache.keys();
                while(segments.hasMoreElements()){
                    String segmentId = segments.nextElement();
                    syncToTrough(segmentId,true);
                }
                troughClient.stop();
                troughClient.promoteDirtySegments();
                troughClient = null;
            }
            dedupDbConnection = null;
            break;

        default:
        }
    }

    @Override
    public void load(CrawlURI curi) {
        // make this call in all cases so that the value is initialized and
        // WARCWriterProcessor knows it should put the info in there
        HashMap<String, Object> contentDigestHistory = curi.getContentDigestHistory();

        //Check in-memory segment first
        boolean memoryDedupHit = false;
        if(segmentCache.containsKey(getSegmentId())) {
            String segmentDedupQuerySql = segmentizeDedupTableName(getSegmentId(), DEDUP_QUERY_SQL);
            try(PreparedStatement dedupQueryStatement = dedupDbConnection.prepareStatement(segmentDedupQuerySql)) {
                dedupQueryStatement.setString(1, persistKeyFor(curi));
                ResultSet rs = dedupQueryStatement.executeQuery();
                while(rs.next()) {
                    Map<String, Object> hist = new HashMap<String, Object>();
                    hist.put(A_ORIGINAL_URL, rs.getString("url"));
                    hist.put(A_ORIGINAL_DATE, rs.getString("date"));
                    hist.put(A_WARC_RECORD_ID, rs.getString("id"));
                    if (logger.isLoggable(Level.FINER)) {
                        logger.finer("loaded in-memory history by digest " + persistKeyFor(curi)
                                + " for uri " + curi + " - " + hist);
                    }
                    contentDigestHistory.putAll(hist);
                    memoryDedupHit=true;
                    break;
                }
            } catch (Exception e) {
                logger.log(Level.WARNING, "problem querying in-memory dedup in " + getSegmentId() + " for url " + curi + " sql: "+segmentDedupQuerySql, e);
            }
        }
        if(!memoryDedupHit) {
            try {
                String sql = "select * from dedup where digest_key = %s";
                List<Map<String, Object>> results = troughClient().read(getSegmentId(), sql, new String[]{persistKeyFor(curi)});
                if (!results.isEmpty()) {
                    Map<String, Object> hist = new HashMap<String, Object>();
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
    }

    @Override
    public void store(CrawlURI curi) {
        if (!curi.hasContentDigestHistory() || curi.getContentDigestHistory().isEmpty()) {
            return;
        }

        if (getSegmentId().isEmpty()) {
            logger.log(Level.WARNING, "no segment id found for url " + curi);
            return;
        }

        if (!segmentCache.containsKey(getSegmentId())) {
            synchronized (getSegmentId()) {
                if (!segmentCache.containsKey(getSegmentId())) {
                    segmentCache.putIfAbsent(getSegmentId(),new Object());
                    createDedupCacheDbTable(getSegmentId());
                }
            }
        }

        insertSQLiteCacheDB(getSegmentId(), curi);
    }
    protected String segmentizeDedupTableName(String segmentId, String sql)
    {
        String tableName = "dedup_" + segmentId.replaceAll("-","_");
        return sql.replace(" dedup"," \""+tableName+"\"");
    }
    public void syncToTrough(String segmentId, boolean forceSync) {
        logger.log(Level.FINE, "syncing local sqlite db to trough " + segmentId);

        int totalRows=0;
        ResultSet dedupRows = null;
        int rowsRead=0;
        Object[] flattenedValues = null;
        synchronized (segmentCache.get(segmentId)) {
            String segmentCountSql = segmentizeDedupTableName(segmentId, COUNT_SQL);
            try (PreparedStatement rowCountStatement = dedupDbConnection.prepareStatement(segmentCountSql)) {
                ResultSet rs = rowCountStatement.executeQuery();
                rs.next();
                totalRows = rs.getInt(1);
            } catch (SQLException e) {
                logger.log(Level.WARNING, "problem reading row count info from local sqlite segment " + segmentId, e);
            }
            if (forceSync || totalRows > getTroughSyncMaxBatchSize()) {
                /*
                Trough takes a single object array for the values we insert, so an array of size (N columns * X rows).
                We read each row in column by column, then repeat for each row.
                 */
                flattenedValues = new Object[4 * totalRows];
                String segmentSelectAllSql = segmentizeDedupTableName(segmentId, SELECT_ALL_SQL);
                try (PreparedStatement dedupReadSelect = dedupDbConnection.prepareStatement(segmentSelectAllSql)) {
                    dedupRows = dedupReadSelect.executeQuery();
                    while (dedupRows.next()) {
                        for (int i=0; i<4; i++) {
                            flattenedValues[(4 * rowsRead) + i] = dedupRows.getObject(i+1);
                        }
                        rowsRead++;
                    }
                } catch (SQLException e) {
                    logger.log(Level.WARNING, "problem reading dedup info from local sqlite segment " + segmentId, e);
                }
                //Delete the cache db and rebuild it
                String segmentDropTableSql = segmentizeDedupTableName(segmentId, DROP_TABLE_DEDUP_SQL);
                try {
                    PreparedStatement dropTableStatement = dedupDbConnection.prepareStatement(segmentDropTableSql);
                    dropTableStatement.execute();
                    createDedupCacheDbTable(segmentId);
                } catch (SQLException e) {
                    logger.log(Level.WARNING, "problem removing cache db table " + segmentId + " sql: " + segmentDropTableSql, e);
                }
            }
        }

        if(rowsRead > 0) {
            //Store dedup values into Trough
            StringBuffer sqlTmpl = new StringBuffer();
            //don't alter the table name since we're dealing with trough now
            sqlTmpl.append(WRITE_SQL_TMPL + " values (%s, %s, %s, %s)");
            for(int i=1; i < rowsRead; i++){
                sqlTmpl.append(", (%s, %s, %s, %s)");
            }
            try {
                troughClient().write(segmentId, sqlTmpl.toString(), flattenedValues, SCHEMA_ID);
            } catch (Exception e) {
                logger.log(Level.WARNING, "problem posting batch of " + rowsRead + " dedup urls to trough segment " + segmentId, e);
            }
        }
    }
    public void insertSQLiteCacheDB(String segmentId, CrawlURI curi) {
        Map<String, Object> hist = curi.getContentDigestHistory();
        int totalRows=0;
        String segmentWriteSqlTemplate = segmentizeDedupTableName(segmentId, WRITE_SQL_TMPL);
        synchronized (segmentCache.get(segmentId)) {

            try (PreparedStatement pstmt = dedupDbConnection.prepareStatement(segmentWriteSqlTemplate + " values (?, ?, ?, ?);")) {
                pstmt.setString(1, persistKeyFor(curi));
                pstmt.setObject(2, hist.get(A_ORIGINAL_URL));
                pstmt.setObject(3, hist.get(A_ORIGINAL_DATE));
                pstmt.setObject(4, hist.get(A_WARC_RECORD_ID));
                pstmt.executeUpdate();
            } catch (Exception e) {
                logger.log(Level.WARNING, "problem writing dedup info to local sqlite segment " + segmentId + " for url " + curi + " sql: " + segmentWriteSqlTemplate, e);
            }
        }
        String segmentCountSql = segmentizeDedupTableName(segmentId, COUNT_SQL);
        try(PreparedStatement rowCountStatement = dedupDbConnection.prepareStatement(segmentCountSql)) {
            ResultSet rs = rowCountStatement.executeQuery();
            rs.next();
            totalRows = rs.getInt(1);
        } catch (Exception e) {
            logger.log(Level.WARNING, "problem getting row count after dedup insert " + segmentId + " for url " + curi + " sql: "+segmentCountSql, e);
        }
        if(totalRows > getTroughSyncMaxBatchSize()) {
            syncToTrough(getSegmentId(), false);
        }
    }
    public void createDedupCacheDbTable(String segmentId) {
        String segmentSchema = segmentizeDedupTableName(segmentId, SCHEMA_SQL);
        try (Statement stmt = dedupDbConnection.createStatement()) {
            stmt.execute(segmentSchema);
        } catch (SQLException e) {
            logger.log(Level.WARNING, "Problem creating SQLite dedup db shard " + segmentId + " with schema "+ segmentSchema, e);
        }
    }
}
