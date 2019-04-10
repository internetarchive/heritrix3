package org.archive.trough;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.IOUtils;
import org.archive.util.ArchiveUtils;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import com.rethinkdb.RethinkDB;
import com.rethinkdb.RethinkDBConstants;
import com.rethinkdb.gen.ast.ReqlExpr;
import com.rethinkdb.net.Connection;

public class TroughClient {
    public static String sqlValue(Object o) {
        if (o == null) {
            return "null";
        } else if (o instanceof Date) {
            return "datetime('" + ArchiveUtils.getLog14Date((Date) o) + "')";
        } else if (o instanceof Boolean) {
            if ((Boolean) o) {
                return "1";
            } else {
                return "0";
            }
        } else if (o instanceof Number) {
            return o.toString();
        } else {
            // the only character that needs escaped in sqlite string literals
            // is single-quote, which is escaped as two single-quotes
            return "'" + o.toString().replaceAll("'", "''") + "'";
        }
    }

    private static final Logger logger = Logger.getLogger(TroughClient.class.getName());

    protected static final RethinkDB r = RethinkDB.r;

    protected static final int SIX_HOURS_MS = 6 * 60 * 60 * 1000;
    protected static final int TEN_MINUTES_MS = 10 * 60 * 1000;
    protected static final String JSON_MIMETYPE = "application/json";
    protected static final String SQL_MIMETYPE = "application/sql";

    protected Random rand = new Random();
    protected Map<String,String> writeUrlCache;
    protected Map<String,String> readUrlCache;
    protected Set<String> dirtySegments;
    protected String[] rethinkServers;
    protected String rethinkDb;
    protected Integer promotionInterval;
    protected Thread promotrix;

    public class TroughException extends IOException {
        private static final long serialVersionUID = 1L;
        public TroughException(String msg) {
            super(msg);
        }
        public TroughException(Exception e) {
            super(e);
        }
        public TroughException(String msg, Throwable cause) {
            super(msg, cause);
        }
    }

    public class TroughNoReadUrlException extends TroughException {
        private static final long serialVersionUID = 1L;
        public TroughNoReadUrlException(String msg) {
            super(msg);
        }
    }

    protected class Promotrix implements Runnable {
        @Override
        public void run() {
            while (true) {
                try {
                    Thread.sleep(promotionInterval * 1000);
                } catch (InterruptedException e) {
                    logger.info("promoter thread shutting down");
                    return;
                }

                try {
                    promoteDirtySegments();
                } catch (Exception e) {
                    logger.log(Level.WARNING, "continuing after unexpected exception promoting dirty segments", e);
                }
            }
        }
    }

    public TroughClient(String rethinkdbTroughUrl) throws MalformedURLException {
        this(rethinkdbTroughUrl, null);
    }

    protected static HttpURLConnection httpRequest(String method, String url, String contentType, String payload, int timeout) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();

        connection.setConnectTimeout(timeout);
        connection.setReadTimeout(timeout);

        connection.setRequestMethod(method);

        if (contentType != null) {
            connection.setRequestProperty("Content-Type", contentType);
        }
        if (payload != null) {
            connection.setDoOutput(true);
            byte[] payloadBytes = payload.getBytes("UTF-8");
            logger.finer(method + " " + url + " " + payloadBytes.length + " bytes " + contentType);
            connection.setRequestProperty("Content-Length", Integer.toString(payloadBytes.length));
            connection.getOutputStream().write(payloadBytes);
            connection.getOutputStream().flush();
            connection.getOutputStream().close();
        } else {
            logger.finer(method + " " + url);
        }

        return connection;
    }

    @SuppressWarnings("unchecked")
    public void promote(String segmentId) throws IOException, TroughException {
        String url = segmentManagerUrl("promote");

        JSONObject payload = new JSONObject();
        payload.put("segment", segmentId);

        logger.info("promoting segment " + segmentId + " to permanent storage in hdfs: posting " + payload + " to " + url);
        HttpURLConnection connection = httpRequest("POST", url, JSON_MIMETYPE, payload.toString(), SIX_HOURS_MS);

        if (connection.getResponseCode() != 200) {
            throw new TroughException(
                    "received " + connection.getResponseCode() + ": " + responsePayload(connection)
                    + " in response to POST " + url + " with data " + payload.toString());
        }
    }

    protected static String responsePayload(HttpURLConnection connection) throws IOException {
        InputStream in;
        try {
            in = connection.getInputStream();
        } catch (IOException e) {
            in = connection.getErrorStream();
        }
        String result = IOUtils.toString(in, "UTF-8");
        return result;
    }

    /**
     * Run a rethinkdb query. {@code retries=0} means try once. Default is 9
     * retries (10 tries).
     * 
     * @return result of {@code reql.run()}
     * @throws TroughException
     */
    protected Object rethinkQuery(ReqlExpr reql, Integer retries) throws TroughException {
        logger.fine("querying rethinkdb: " + reql);
        if (retries == null || retries < 0) {
            retries = 9;
        }

        Exception lastE = null;
        for (int i = 0; i <= retries; i++) {
            int whichServer = rand.nextInt(rethinkServers.length);
            Connection conn = null;
            try {
                String[] hostPort = rethinkServers[whichServer].split(":", 2);
                String host = hostPort[0];
                int port = RethinkDBConstants.DEFAULT_PORT;
                if (hostPort.length == 2) {
                    port = Integer.valueOf(hostPort[1]);
                }
                conn = r.connection().hostname(host).port(port).db(rethinkDb).connect();
                Object result = reql.run(conn);
                return result;
            } catch (Exception e) {
                logger.warning("rethinkdb query failed (server=" + rethinkServers[whichServer] + "; " + i + " retries left)");
                lastE = e;
            } finally {
                if (conn != null) {
                    conn.close();
                }
            }
        }

        throw new TroughException(lastE);
    }

    protected String segmentManagerUrl(String subpath) throws TroughException {
        String base = segmentManagerUrl();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        if (subpath.startsWith("/")) {
            subpath = subpath.substring(1);
        }
        return base + "/" + subpath;
    }

    protected String segmentManagerUrl() throws TroughException {
        ReqlExpr reql = r.table("services").optArg("read_mode", "majority").getAll("trough-sync-master")
                .filter(svc -> r.now().sub(svc.g("last_heartbeat")).lt(svc.g("ttl")));
        @SuppressWarnings("unchecked")
        Iterable<Map<String, Object>> results = (Iterable<Map<String, Object>>) rethinkQuery(reql, null);
        for (Map<String,Object> result: results) {
            return (String) result.get("url");
        }
        throw new TroughException("no healthy trough-sync-master in rethinkdb?");
    }

    /**
     * 
     * @param rethinkdbTroughUrl url with schema rethinkdb:// pointing to
     *          trough configuration database
     * @param promotionInterval if specified, {@code TroughClient} will spawn a
     *          thread that "promotes" (pushed to hdfs) "dirty" trough segments
     *          (segments that have received writes) periodically, sleeping for
     *          {@code promotionInterval} seconds between cycles 
     * @throws MalformedURLException 
     */
    public TroughClient(String rethinkdbTroughUrl, Integer promotionInterval) throws MalformedURLException {
        parseRethinkdbUrl(rethinkdbTroughUrl);
        writeUrlCache = new HashMap<String, String>();
        readUrlCache = new HashMap<String, String>();
        dirtySegments = new HashSet<String>();
        this.promotionInterval = promotionInterval;
    }

    public void start() {
        if (promotionInterval != null) {
            promotrix = new Thread(new Promotrix(), "TroughClient-promotrix");
            promotrix.setDaemon(true);
            promotrix.start();
        }
    }

    public void stop() {
        if (promotrix != null) {
            promotrix.interrupt();
            try {
                promotrix.join(60 * 1000);
            } catch (InterruptedException e) {
            }
            if (promotrix.isAlive()) {
                logger.warning(promotrix + " is still running after interrupting and waiting one minute; "
                        + "it should die once the active promotion finishes");
            }
            promotrix = null;
        }
    }

    /**
     * Parses a url like this rethinkdb://server1:port,server2:port/database/table
     * Sets fields {@code rethinkServers}, {@code rethinkDb}, {@code rethinkTable}
     * @throws MalformedURLException 
     */
    protected void parseRethinkdbUrl(String input) throws MalformedURLException {
        Matcher m = Pattern.compile("^rethinkdb://([^/]+)/([^/]+)$").matcher(input);
        if (!m.matches()) {
            throw new MalformedURLException("failed to parse as rethinkdb url: " + input);
        }

        rethinkServers = m.group(1).split(",");
        rethinkDb = m.group(2);
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> read(String segmentId, String sqlTmpl, Object[] values) throws IOException {
        String readUrl = readUrl(segmentId);

        String[] sqlValues = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            sqlValues[i] = sqlValue(values[i]);
        }
        String sql = String.format(sqlTmpl, (Object[]) sqlValues);

        HttpURLConnection connection;
        try {
            connection = httpRequest("POST", readUrl, SQL_MIMETYPE, sql, TEN_MINUTES_MS);

            if (connection.getResponseCode() != 200) {
                throw new TroughException(
                        "unexpected response " + connection.getResponseCode() + " "
                                + connection.getResponseMessage() + ": " + responsePayload(connection)
                                + " from " + readUrl + " to query: " + sql);
            }

            Object result = new JSONParser().parse(new InputStreamReader(connection.getInputStream(), "UTF-8"));
            return (List<Map<String, Object>>) result;
        } catch (IOException e) {
            synchronized (readUrlCache) {
                readUrlCache.remove(segmentId);
            }
            throw e;
        } catch (ParseException e) {
            synchronized (readUrlCache) {
                readUrlCache.remove(segmentId);
            }
            throw new TroughException("problem parsing json response from " + readUrl, e);
        }
    }

    protected String readUrl(String segmentId) throws TroughException {
        if (readUrlCache.get(segmentId) == null) {
            synchronized (readUrlCache) {
                if (readUrlCache.get(segmentId) == null) {
                    String url = readUrlNoCache(segmentId);
                    readUrlCache.put(segmentId, url);
                }
            }
            logger.info("segment " + segmentId + " read url is " + readUrlCache.get(segmentId));
        }
        return readUrlCache.get(segmentId);
    }

    @SuppressWarnings("unchecked")
    protected String readUrlNoCache(String segmentId) throws TroughException {
        ReqlExpr reql = r.table("services").getAll(segmentId).optArg("index", "segment")
                .filter(r.hashMap("role", "trough-read"))
                .filter(svc -> r.now().sub(svc.g("last_heartbeat")).lt(svc.g("ttl")))
                .orderBy("load");
        List<Map<String, Object>> result;
        result = (List<Map<String,Object>>) rethinkQuery(reql, null);
        if (result != null && result.size() > 0) {
            return (String) result.get(0).get("url");
        } else {
            throw new TroughNoReadUrlException("failed to obtain read url for trough segment " + segmentId + " (maybe the segment hasn't been created yet)");
        }
    }

    public void registerSchema(String schemaId, String schemaSql) throws IOException {
        String url = segmentManagerUrl("schema/" + schemaId + "/sql");
        logger.info("registering schema " + schemaId + " at " + url);
        HttpURLConnection connection = httpRequest("PUT", url, SQL_MIMETYPE, schemaSql, TEN_MINUTES_MS);
        if (connection.getResponseCode() != 201 && connection.getResponseCode() != 204) {
            throw new TroughException("received " + connection.getResponseCode() + ": " + responsePayload(connection)
            + " in response to PUT " + url + " with data " + schemaSql);
        }
    }

    public void write(String segmentId, String sqlTmpl, Object[] values) throws IOException {
        write(segmentId, sqlTmpl, values, "default");
    }

    public void write(String segmentId, String sqlTmpl, Object[] values, String schemaId) throws IOException {
        String url = writeUrl(segmentId, schemaId);

        String[] sqlValues = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            sqlValues[i] = sqlValue(values[i]);
        }
        String sql = String.format(sqlTmpl, (Object[]) sqlValues);

        try {
            HttpURLConnection connection = httpRequest("POST", url, "application/sql", sql, TEN_MINUTES_MS);
            if (connection.getResponseCode() != 200) {
                throw new TroughException("unexpected response " + connection.getResponseCode() + " "
                        + connection.getResponseMessage() + ": " + responsePayload(connection)
                        + " from " + url + " to query: " + sql);
            }            
            if (!dirtySegments.contains(segmentId)) {
                synchronized (dirtySegments) {
                    dirtySegments.add(segmentId);
                }
            }
        } catch (IOException e) {
            synchronized (writeUrlCache) {
                writeUrlCache.remove(segmentId);
            }
            throw e;
        }
    }

    protected String writeUrl(String segmentId, String schemaId) throws IOException {
        if (writeUrlCache.get(segmentId) == null) {
            synchronized (writeUrlCache) {
                if (writeUrlCache.get(segmentId) == null) {
                    String url = writeUrlNoCache(segmentId, schemaId);
                    writeUrlCache.put(segmentId, url);
                }
            }
            logger.info("segment " + segmentId + " write url is " + writeUrlCache.get(segmentId));
        }
        return writeUrlCache.get(segmentId);
    }

    @SuppressWarnings("unchecked")
    protected String writeUrlNoCache(String segmentId, String schemaId) throws IOException {
        String provisionUrl = segmentManagerUrl("provision");

        JSONObject payload = new JSONObject();
        payload.put("segment", segmentId);
        payload.put("schema", schemaId);

        HttpURLConnection connection = httpRequest("POST", provisionUrl, JSON_MIMETYPE, payload.toJSONString(), TEN_MINUTES_MS);
        if (connection.getResponseCode() != 200) {
            throw new TroughException("received " + connection.getResponseCode() + ": " + responsePayload(connection)
            + " in response to POST " + provisionUrl + " with data " + payload);
        }

        JSONObject result;
        try {
            result = (JSONObject) new JSONParser().parse(new InputStreamReader(connection.getInputStream(), "UTF-8"));
        } catch (ParseException e) {
            throw new TroughException("unable to parse response from POST " + provisionUrl + " as json", e);
        }
        String writeUrl = (String) result.get("write_url");
        if (writeUrl == null) {
            throw new TroughException("write_url missing from response to " + provisionUrl + " - " + result);
        }
        return writeUrl;
    }

    public void promoteDirtySegments() {
        String[] promoteThese;
        synchronized (dirtySegments) {
            promoteThese = dirtySegments.toArray(new String[0]);
            dirtySegments.clear();
        }

        for (String segmentId: promoteThese) {
            try {
                promote(segmentId);
                if (Thread.currentThread().isInterrupted()) {
                    return;
                }
            } catch (Exception e) {
                logger.log(Level.WARNING, "problem promoting segment " + segmentId, e);
            }
        }
    }
}
