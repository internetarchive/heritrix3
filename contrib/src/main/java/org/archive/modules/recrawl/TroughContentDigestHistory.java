package org.archive.modules.recrawl;

import static org.archive.modules.recrawl.RecrawlAttributeConstants.A_ORIGINAL_DATE;
import static org.archive.modules.recrawl.RecrawlAttributeConstants.A_ORIGINAL_URL;
import static org.archive.modules.recrawl.RecrawlAttributeConstants.A_WARC_RECORD_ID;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.ConcurrentHashMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.archive.crawler.event.CrawlStateEvent;
import org.archive.modules.CrawlURI;
import org.archive.modules.writer.WARCWriterChainProcessor;
import org.archive.spring.ConfigPath;
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

    protected ConfigPath cacheDir = new ConfigPath("Trough sqlite dedup cache directory","dedupCache");
    public ConfigPath getCacheDir() {
        return cacheDir;
    }
    public void setCacheDir(ConfigPath dir) {
        this.cacheDir = dir;
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
    protected static final String WRITE_SQL_TMPL =
            "insert or ignore into dedup (digest_key, url, date, id)";

    // row count that avoids table scan if no deletes
    protected static final String COUNT_SQL =
            "SELECT MAX(_ROWID_) FROM dedup LIMIT 1;";
    protected static final String SELECT_ALL_SQL =
            "SELECT * FROM dedup;";
    protected ConcurrentHashMap<String, Object> segmentCache = new ConcurrentHashMap<String, Object>();
    // Sync dedup db to trough when rows exceeds
    protected static final int TROUGH_SYNC_MAX_BATCH = 400;

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

    @Override
    public void store(CrawlURI curi) {
        if (!curi.hasContentDigestHistory() || curi.getContentDigestHistory().isEmpty()) {
            return;
        }

        if (getSegmentId().isEmpty() || getCacheDir().toString().length()==0) {
            logger.log(Level.WARNING, "no segment id or path found for url " + curi);
            return;
        }
        try {
            if (!Files.exists(Paths.get(getCacheDir().getFile().getAbsolutePath())))
                org.archive.util.FileUtils.ensureWriteableDirectory(getCacheDir().getFile());
        }
        catch (IOException e) {
            logger.log(Level.WARNING, "Unable to create dedup scratch directory: "+getCacheDir().getFile().getAbsolutePath());
        }

        if (!Files.exists(Paths.get(getCacheDir().getFile().getAbsolutePath(), getSegmentId() + ".sql"))) {
            segmentCache.putIfAbsent(getSegmentId(),new Object());
            synchronized (segmentCache.get(getSegmentId())) {
                if (!Files.exists(Paths.get(getCacheDir().getFile().getAbsolutePath(), getSegmentId() + ".sql")))
                    createLocalSQLiteDB();
            }
        }

        insertLocalSQLiteDB(curi);
    }
    public void syncToTrough(String segmentId) {
        logger.log(Level.FINE, "syncing local sqlite db to trough " + segmentId);
        Path dbFilePath = Paths.get(getCacheDir().getFile().getAbsolutePath(),segmentId+".sql");
        Path dbSyncFilePath = Paths.get(getCacheDir().getFile().getAbsolutePath(),segmentId+".sql.sync");
        String dbUrl="jdbc:sqlite:"+dbFilePath.toString();
        String dbSyncUrl="jdbc:sqlite:"+dbSyncFilePath.toString();

        int totalRows=0;
        ResultSet dedupRows = null;
        synchronized (segmentCache.get(segmentId)) {
            try (Connection conn = DriverManager.getConnection(dbUrl);
                 PreparedStatement rowCountStatement = conn.prepareStatement(COUNT_SQL)) {
                ResultSet rs = rowCountStatement.executeQuery();
                rs.next();
                totalRows = rs.getInt(1);
            } catch (SQLException e) {
                logger.log(Level.WARNING, "problem reading row count info from local sqlite segment " + segmentId, e);
            }
            if (totalRows > TROUGH_SYNC_MAX_BATCH) {
                try {
                    Files.move(dbFilePath, dbSyncFilePath, StandardCopyOption.REPLACE_EXISTING); //TODO: delete
                    createLocalSQLiteDB();
                } catch (IOException e) {
                    logger.log(Level.WARNING, "problem moving aside local sqlite segment " + segmentId, e);
                }
            }
        }

        if(totalRows > TROUGH_SYNC_MAX_BATCH) {
            StringBuffer sqlTmpl = new StringBuffer();
            sqlTmpl.append(WRITE_SQL_TMPL + " values (%s, %s, %s, %s)");
            for(int i=1; i < totalRows; i++){
                sqlTmpl.append(", (%s, %s, %s, %s)");
            }
            Object[] flattenedValues = new Object[4 * totalRows];

            int rowId=0;
            try (Connection conn = DriverManager.getConnection(dbSyncUrl)) {
                PreparedStatement dedupReadSelect = conn.prepareStatement(SELECT_ALL_SQL);
                dedupRows = dedupReadSelect.executeQuery();

                while (dedupRows.next()) {
                    for (int i=0; i<4; i++) {
                        flattenedValues[(4 * rowId) + i] = dedupRows.getObject(i+1);
                    }
                    rowId++;
                }
            } catch (SQLException e) {
                logger.log(Level.WARNING, "problem reading dedup info from local sqlite segment " + getSegmentId(), e);
            }
            if(rowId > 0) {
                try {
                    troughClient().write(segmentId, sqlTmpl.toString(), flattenedValues);
                } catch (Exception e) {
                    logger.log(Level.WARNING, "problem posting batch of " + totalRows + " dedup urls to trough segment " + segmentId, e);
                }
            }

        }
    }
    public void insertLocalSQLiteDB(CrawlURI curi) {
        Map<String, Object> hist = curi.getContentDigestHistory();
 //TODO in memory dedup-segmentid for table name
        Path dbFilePath = Paths.get(getCacheDir().getFile().getAbsolutePath(),getSegmentId()+".sql");
        String dbUrl="jdbc:sqlite:"+dbFilePath.toString();
        int totalRows=0;
        synchronized (segmentCache.get(getSegmentId())) {
            try (Connection conn = DriverManager.getConnection(dbUrl);
                 PreparedStatement pstmt = conn.prepareStatement(WRITE_SQL_TMPL + " values (?, ?, ?, ?);")) {
                pstmt.setString(1, persistKeyFor(curi));
                pstmt.setObject(2, hist.get(A_ORIGINAL_URL));
                pstmt.setObject(3, hist.get(A_ORIGINAL_DATE));
                pstmt.setObject(4, hist.get(A_WARC_RECORD_ID));
                pstmt.executeUpdate();

                PreparedStatement rowCountStatement = conn.prepareStatement(COUNT_SQL);
                ResultSet rs = rowCountStatement.executeQuery();
                rs.next();
                totalRows = rs.getInt(1);
            } catch (Exception e) {
                logger.log(Level.WARNING, "problem writing dedup info to local sqlite segment " + getSegmentId() + " for url " + curi, e);
            }
        }
        if(totalRows > TROUGH_SYNC_MAX_BATCH) {
            syncToTrough(getSegmentId());
        }
    }
    public void createLocalSQLiteDB() {
        Path dbFilePath = Paths.get(getCacheDir().getFile().getAbsolutePath(),getSegmentId()+".sql");
        String dbUrl="jdbc:sqlite:"+dbFilePath.toString();
        try (Connection conn = DriverManager.getConnection(dbUrl); Statement stmt = conn.createStatement()) {
            stmt.execute(SCHEMA_SQL);
        } catch (SQLException e) {
            logger.log(Level.WARNING, "Problem creating SQLite dedup db shard " + dbUrl, e);
        }
    }
}
