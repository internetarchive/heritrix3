package org.archive.modules.recrawl.hbase;

import java.util.Map;
import java.util.logging.Logger;

import org.apache.hadoop.hbase.util.Bytes;
import org.archive.modules.CrawlURI;
import org.archive.modules.canonicalize.CanonicalizationRule;
import org.archive.modules.recrawl.FetchHistoryHelper;
import org.archive.modules.recrawl.FetchHistoryProcessor;
import org.archive.modules.recrawl.PersistProcessor;

/**
 * implements common utility methods for implementing {@link RecrawlDataSchema}.
 * <ul>
 * <li>configuring single column family name</li>
 * <li>formatting/parsing HTTP date text</li>
 * <li>constructing row key</li>
 * <li>preparing fetch-history array</li>
 * </ul>
 * @contributor kenji
 */
abstract public class RecrawlDataSchemaBase implements RecrawlDataSchema {
    private static final Logger logger = Logger.getLogger(RecrawlDataSchemaBase.class.getName());

    /**
     * default value for {@link #columnFamily}.
     */
    public static final byte[] DEFAULT_COLUMN_FAMILY = Bytes.toBytes("f");
    protected byte[] columnFamily = DEFAULT_COLUMN_FAMILY;

    public static final byte[] COLUMN_NOCRAWL = Bytes.toBytes("z");
    
    /**
     * default value for {@link #useCanonicalString}.
     */
    public static boolean DEFAULT_USE_CANONICAL_STRING = true;

    private boolean useCanonicalString = DEFAULT_USE_CANONICAL_STRING;
    private CanonicalizationRule keyRule = null;

    protected int historyLength = 2;

    public RecrawlDataSchemaBase() {
        super();
    }

    public void setColumnFamily(String colf) {
        columnFamily = Bytes.toBytes(colf);
    }

    public boolean isUseCanonicalString() {
        return useCanonicalString;
    }
    /**
     * if set to true, canonicalized string will be used as row key, rather than URI
     * @param useCanonicalString
     */
    public void setUseCanonicalString(boolean useCanonicalString) {
        this.useCanonicalString = useCanonicalString;
    }

    public String getColumnFamily() {
        return Bytes.toString(columnFamily);
    }


    public CanonicalizationRule getKeyRule() {
        return keyRule;
    }
    /**
     * alternative canonicalization rule for generating row key from URI.
     * TODO: currently unused.
     * @param keyRule
     */
    public void setKeyRule(CanonicalizationRule keyRule) {
        this.keyRule = keyRule;
    }

    public int getHistoryLength() {
        return historyLength;
    }

    /**
     * maximum number of crawl history entries to retain in {@link CrawlURI}.
     * when more than this number of crawl history entry is being added by
     * {@link #getFetchHistory(CrawlURI, long)}, oldest entry will be discarded.
     * {@code historyLength} should be the same number as
     * {@link FetchHistoryProcessor#setHistoryLength(int)}, or FetchHistoryProcessor will
     * reallocate the crawl history array.
     * @param historyLength
     * @see FetchHistoryProcessor#setHistoryLength(int)
     */
    public void setHistoryLength(int historyLength) {
        this.historyLength = historyLength;
    }

    /**
     * calls {@link FetchHistoryHelper#getFetchHistory(CrawlURI, long, int)} with {@link #historyLength}.
     * @param uri CrawlURI from which fetch history is obtained.
     * @return Map object for storing re-crawl data (never null).
     * @see FetchHistoryHelper#getFetchHistory(CrawlURI, long, int)
     * @see FetchHistoryProcessor
     */
    protected Map<String, Object> getFetchHistory(CrawlURI uri, long timestamp) {
        return FetchHistoryHelper.getFetchHistory(uri, timestamp, historyLength);
    }

    /**
     * return row key for {@code curi}.
     * TODO: move this to HBasePersistProcessor by redesigning {@link RecrawlDataSchema}.
     * @param curi {@link CrawlURI} for which a row is being fetched.
     * @return row key
     */
    public byte[] rowKeyForURI(CrawlURI curi) {
        if (useCanonicalString) {
            // TODO: use keyRule if specified.
            return Bytes.toBytes(PersistProcessor.persistKeyFor(curi));
        } else {
            return Bytes.toBytes(curi.toString());
        }
    }
}
