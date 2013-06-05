package org.archive.modules.recrawl.hbase;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.hadoop.hbase.util.Bytes;
import org.archive.modules.CrawlURI;
import org.archive.modules.recrawl.FetchHistoryProcessor;
import org.archive.modules.recrawl.RecrawlAttributeConstants;

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

	public static final byte[] DEFAULT_COLUMN_FAMILY = Bytes.toBytes("f");
	protected byte[] columnFamily = DEFAULT_COLUMN_FAMILY;

	public static final byte[] COLUMN_NOCRAWL = Bytes.toBytes("z");

	protected static String getHeaderValue(org.apache.commons.httpclient.HttpMethod method, String name) {
		org.apache.commons.httpclient.Header header = method.getResponseHeader(name);
		return header != null ? header.getValue() : null;
	}

	public void setColumnFamily(String colf) {
		columnFamily = Bytes.toBytes(colf);
	}

	@Override
	public String getColumnFamily() {
		return Bytes.toString(columnFamily);
	}

	/**
	 * returns a Map to store recrawl data, which is properly stored in CrawlURI's
	 * fetch history array property ({@link RecrawlAttributeConstants#A_FETCH_HISTORY} member of {@link CrawlURI#getData()}.)
	 * if {@code uri} has no fetch history yet, it is first initialized with an array of length two so that
	 * {@linkplain FetchHistoryProcessor} do not need to reallocate it (this only works for historyLength == 2, though).
	 * @param uri CrawlURI from which fetch history is obtained.
	 * @return Map object for storing re-crawl data (never null).
	 * @see FetchHistoryProcessor
	 */
	@SuppressWarnings("unchecked")
	protected static Map<String, Object> getFetchHistory(CrawlURI uri) {
		Map<String, Object> data = uri.getData();
		Map<String, Object>[] history = (Map[])data.get(RecrawlAttributeConstants.A_FETCH_HISTORY);
		if (history == null) {
			// only the first element is used by FetchHTTP, WarcWriterProcessor etc.
			// FetchHistoryProcessor casts history to HashMap[]. So it must be new HashMap[2], not Map[2]
			history = new HashMap[2];
			history[0] = new HashMap<String, Object>();
			// no need to set history[1]. it would simply be discarded by FetchHistoryProcessor.
			data.put(RecrawlAttributeConstants.A_FETCH_HISTORY, history);
		}
		return history[0];
	}

	public RecrawlDataSchemaBase() {
		super();
	}

	protected static final DateFormat HTTP_DATE_FORMAT = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz");

	/**
	 * converts time in HTTP Date format {@code dateStr} to seconds
	 * since epoch. 
	 * @param dateStr time in HTTP Date format.
	 * @return seconds since epoch
	 */
	protected long parseHttpDate(String dateStr) {
		synchronized (HTTP_DATE_FORMAT) {
			try {
				Date d = HTTP_DATE_FORMAT.parse(dateStr);
				return d.getTime() / 1000;
			} catch (ParseException ex) {
				if (logger.isLoggable(Level.FINER))
					logger.fine("bad HTTP DATE: " + dateStr);
				return 0;
			}
		}
	}

	protected String formatHttpDate(long time) {
		synchronized (HTTP_DATE_FORMAT) {
			// format is not thread safe either
			return HTTP_DATE_FORMAT.format(new Date(time * 1000));
		}
	}

}
