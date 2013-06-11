package org.archive.modules.recrawl.hbase;

import static org.archive.modules.recrawl.RecrawlAttributeConstants.A_CONTENT_DIGEST_COUNT;
import static org.archive.modules.recrawl.RecrawlAttributeConstants.A_ORIGINAL_DATE;
import static org.archive.modules.recrawl.RecrawlAttributeConstants.A_ORIGINAL_URL;
import static org.archive.modules.recrawl.RecrawlAttributeConstants.A_WARC_FILENAME;
import static org.archive.modules.recrawl.RecrawlAttributeConstants.A_WARC_FILE_OFFSET;
import static org.archive.modules.recrawl.RecrawlAttributeConstants.A_WARC_RECORD_ID;

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.hadoop.hbase.HColumnDescriptor;
import org.apache.hadoop.hbase.HTableDescriptor;
import org.apache.hadoop.hbase.client.Get;
import org.apache.hadoop.hbase.client.HBaseAdmin;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.RetriesExhaustedWithDetailsException;
import org.apache.hadoop.hbase.regionserver.NoSuchColumnFamilyException;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.hadoop.thirdparty.guava.common.collect.BiMap;
import org.apache.hadoop.thirdparty.guava.common.collect.HashBiMap;
import org.archive.modules.CrawlURI;
import org.archive.modules.recrawl.AbstractContentDigestHistory;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.context.Lifecycle;

/**
 * HBase content digest history store. Must be a toplevel bean in
 * crawler-beans.cxml in order to receive {@link Lifecycle} events.
 * 
 * @see AbstractContentDigestHistory
 * @contributor nlevitt
 */
public class HBaseContentDigestHistory extends AbstractContentDigestHistory implements Lifecycle {

    private static final Logger logger = 
            Logger.getLogger(HBaseContentDigestHistory.class.getName());

    protected static final byte[] COLUMN_FAMILY = Bytes.toBytes("f");
    protected static final byte[] COLUMN = Bytes.toBytes("c");

    protected static final BiMap<String,String> JSON_KEYS_MAP = HashBiMap.create();
    static {
        JSON_KEYS_MAP.put(A_CONTENT_DIGEST_COUNT, "c");
        JSON_KEYS_MAP.put(A_ORIGINAL_URL, "u");
        JSON_KEYS_MAP.put(A_WARC_RECORD_ID, "i");
        JSON_KEYS_MAP.put(A_WARC_FILENAME, "f");
        JSON_KEYS_MAP.put(A_WARC_FILE_OFFSET, "o");
        JSON_KEYS_MAP.put(A_ORIGINAL_DATE, "d");
    }

    protected HBaseTable table;
    public void setTable(HBaseTable table) {
        this.table = table;
    }

    protected boolean addColumnFamily = false;
    public boolean getAddColumnFamily() {
        return addColumnFamily;
    }
    /**
     * Add the expected column family
     * {@link HBasePersistProcessor#COLUMN_FAMILY} to the HBase table if the
     * table doesn't already have it.
     */
    public void setAddColumnFamily(boolean addColumnFamily) {
        this.addColumnFamily = addColumnFamily;
    }

    protected int retryIntervalMs = 10*1000;
    public int getRetryIntervalMs() {
        return retryIntervalMs;
    }
    public void setRetryIntervalMs(int retryIntervalMs) {
        this.retryIntervalMs = retryIntervalMs;
    }

    protected int maxTries = 1;
    public int getMaxTries() {
        return maxTries;
    }
    public void setMaxTries(int maxTries) {
        this.maxTries = maxTries;
    }

    protected synchronized void addColumnFamily() {
        try {
            HTableDescriptor oldDesc = table.getHtableDescriptor();
            if (oldDesc.getFamily(COLUMN_FAMILY) == null) {
                HTableDescriptor newDesc = new HTableDescriptor(oldDesc);
                newDesc.addFamily(new HColumnDescriptor(COLUMN_FAMILY));
                logger.info("table does not yet have expected column family, modifying descriptor to " + newDesc);
                HBaseAdmin hbaseAdmin = table.getHbase().admin();
                hbaseAdmin.disableTable(table.getName());
                hbaseAdmin.modifyTable(Bytes.toBytes(table.getName()), newDesc);
                hbaseAdmin.enableTable(table.getName());
            }
        } catch (IOException e) {
            logger.warning("problem adding column family: " + e);
        }
    }

    private boolean isRunning;
    @Override
    public void start() {
        // add column family here to avoid disabling table while another
        // ToeThread is trying to use it
        if (getAddColumnFamily()) {
            addColumnFamily();
        }
        this.isRunning = true;
    }
    @Override
    public void stop() {
        this.isRunning = false;
    }
    @Override
    public boolean isRunning() {
        return isRunning;
    }

    @Override
    public void load(CrawlURI curi) {
        // make this call in all cases so that the value is initialized and
        // WARCWriterProcessor knows it should put the info in there
        HashMap<String, Object> contentDigestHistory = curi.getContentDigestHistory();

        byte[] key = Bytes.toBytes(persistKeyFor(curi));
        Result hbaseResult = tryHbaseGet(curi, new Get(key));

        if (hbaseResult != null) {
            Map<String, Object> loadedHistory = parseHbaseResult(curi, hbaseResult);

            if (loadedHistory != null) {
                if (logger.isLoggable(Level.FINER)) {
                    logger.finer("loaded history by digest " + persistKeyFor(curi)
                            + " for uri " + curi + " - " + loadedHistory);
                }
                contentDigestHistory.putAll(loadedHistory);
            }
        }
    }

    protected Result tryHbaseGet(CrawlURI curi, Get hbaseGet) {
        try {
            return table.get(hbaseGet);
        } catch (IOException e) {
            logger.warning("problem retrieving persist data from hbase, proceeding without, for digest " + persistKeyFor(curi) + " uri " +  curi + " - " + e);
            return null;
        }
    }

    protected Map<String, Object> parseHbaseResult(CrawlURI curi, Result hbaseResult) {
        HashMap<String, Object> loadedHistory = null;
        // no data for uri is indicated by empty Result
        if (!hbaseResult.isEmpty()) {
            byte[] jsonBytes = hbaseResult.getValue(COLUMN_FAMILY, COLUMN);
            if (jsonBytes != null) {
                JSONObject json = null;
                try {
                    json = new JSONObject(Bytes.toString(jsonBytes));
                    loadedHistory = new HashMap<String,Object>();
                    @SuppressWarnings("unchecked")
                    Iterator<String> keyIter = json.keys();
                    while (keyIter.hasNext()) {
                        String jsonKey = keyIter.next();
                        Object jsonValue = json.get(jsonKey);
                        String historyMapKey = JSON_KEYS_MAP.inverse().get(jsonKey);
                        if (historyMapKey == null) {
                            logger.warning("unknown key \"" + jsonKey + "\" found in hbase json for digest " + persistKeyFor(curi));
                            historyMapKey = jsonKey;
                        }
                        loadedHistory.put(historyMapKey, jsonValue);
                    }
                } catch (JSONException e) {
                    logger.warning("problem parsing json for digest " + persistKeyFor(curi) + " uri " +  curi + " - " + e);
                }
            } else {
                // shouldn't happen? result.isEmpty() is normal case
                logger.fine("[jsonBytes==null] no persist data for digest " + persistKeyFor(curi) + " uri " +  curi);
            }
        } else {
            logger.finest("[result.isEmpty()] no persist data for digest " + persistKeyFor(curi) + " uri " +  curi);
        }

        return loadedHistory;
    }

    @Override
    public void store(CrawlURI curi) {
        if (!curi.hasContentDigestHistory()
                || curi.getContentDigestHistory().isEmpty()) {
            logger.warning("not saving empty content digest history (do you "
                    + " have a ContentDigestHistoryLoader in your disposition"
                    + " chain?) - " + curi);
            return;
        }
        if (logger.isLoggable(Level.FINER)) {
            logger.finer("storing history by digest " + persistKeyFor(curi)
                    + " for uri " + curi + " - "
                    + curi.getContentDigestHistory());
        }

        Put hbasePut = createHbasePut(curi);
        tryHbasePut(curi, hbasePut);
    }

    protected void tryHbasePut(CrawlURI curi, Put p) {
        int tryCount = 0;
        do {
            tryCount++;
            try {
                table.put(p);
                return;
            } catch (RetriesExhaustedWithDetailsException e) {
                if (e.getCause(0) instanceof NoSuchColumnFamilyException && getAddColumnFamily()) {
                    addColumnFamily();
                    tryCount--;
                } else {
                    logger.warning("put failed " + "(try " + tryCount + " of "
                            + getMaxTries() + ")" + " for " + curi + " - " + e);
                }
            } catch (IOException e) {
                logger.warning("put failed " + "(try " + tryCount + " of "
                        + getMaxTries() + ")" + " for " + curi + " - " + e);
            } catch (NullPointerException e) {
                // HTable.put() throws NullPointerException while connection is lost.
                logger.warning("put failed " + "(try " + tryCount + " of "
                        + getMaxTries() + ")" + " for " + curi + " - " + e);
            }

            if (tryCount > 0 && tryCount < getMaxTries() && isRunning()) {
                try {
                    Thread.sleep(getRetryIntervalMs());
                } catch (InterruptedException ex) {
                    logger.warning("thread interrupted. aborting retry for " + curi);
                    return;
                }
            }
        } while (tryCount < getMaxTries() && isRunning());

        if (isRunning()) {
            logger.warning("giving up after " + tryCount + " tries on put for " + curi);
        }
    }

    protected Put createHbasePut(CrawlURI curi) {
        byte[] key = Bytes.toBytes(persistKeyFor(curi));
        Put hbasePut = new Put(key);
        try {
            JSONObject json = new JSONObject();
            for (Entry<String, Object> entry: curi.getContentDigestHistory().entrySet()) {
                String jsonKey = JSON_KEYS_MAP.get(entry.getKey());
                if (jsonKey == null) {
                    logger.warning("unknown key \"" + entry.getKey() + "\" found in content digest history map for " + curi);
                    jsonKey = entry.getKey();
                }
                json.put(jsonKey, entry.getValue());
            }
            hbasePut.add(COLUMN_FAMILY, COLUMN, Bytes.toBytes(json.toString()));
        } catch (JSONException e) {
            // should not happen - all values are either primitive or String.
            logger.log(Level.SEVERE, "problem creating json object for digest " + persistKeyFor(curi) + " uri " +  curi, e);
        }
        return hbasePut;
    }

}
