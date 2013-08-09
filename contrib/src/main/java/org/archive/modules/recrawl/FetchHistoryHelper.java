/*
 *  This file is part of the Heritrix web crawler (crawler.archive.org).
 *
 *  Licensed to the Internet Archive (IA) by one or more individual 
 *  contributors. 
 *
 *  The IA licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.archive.modules.recrawl;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.archive.modules.CrawlURI;

/**
 * collection of utility methods useful for loading and storing crawl history.
 * <p>note that these methods can also be useful for non-HBase crawl history storage.</p>
 * @contributor kenji
 *
 */
public class FetchHistoryHelper {
  private static final Log logger = LogFactory.getLog(FetchHistoryHelper.class);
  /**
   * key for storing timestamp in crawl history map.
   */
  public static final String A_TIMESTAMP = ".ts";

  public static String getHeaderValue(HttpMethod method, String name) {
      org.apache.commons.httpclient.Header header = method.getResponseHeader(name);
      return header != null ? header.getValue() : null;
  }

  /**
   * returns a Map to store recrawl data, positioned properly in CrawlURI's
   * fetch history array, according to {@code timestamp}. this makes it possible
   * to import crawl history data from multiple sources.
   * @param uri target {@link CrawlURI}
   * @param timestamp timestamp (in ms) of crawl history to be added.
   * @return Map object to store recrawl data, or null if {@code timestamp} is older
   * than existing crawl history entry and there's no room for it.
   * @see #setHistoryLength(int)
   */
  @SuppressWarnings("unchecked")
  public static Map<String, Object> getFetchHistory(CrawlURI uri, long timestamp, int historyLength) {
    Map<String, Object> data = uri.getData();
    Map<String, Object>[] history = (Map[])data.get(RecrawlAttributeConstants.A_FETCH_HISTORY);
    if (history == null) {
      // there's no history records at all.
      // FetchHistoryProcessor assumes history is HashMap[], not Map[].
      history = new HashMap[historyLength];
      data.put(RecrawlAttributeConstants.A_FETCH_HISTORY, history);
    }
    for (int i = 0; i < history.length; i++) {
      if (history[i] == null) {
        history[i] = new HashMap<String, Object>();
        history[i].put(A_TIMESTAMP, timestamp);
        return history[i];
      }
      Object ts = history[i].get(A_TIMESTAMP);
      // no timestamp value is regarded as older than anything.
      if (!(ts instanceof Long) || timestamp > (Long)ts) {
        if (i < history.length - 2) {
          System.arraycopy(history, i, history, i + 1, history.length - i - 1);
        } else if (i == history.length - 2) {
          history[i + 1] = history[i];
        }
        history[i] = new HashMap<String, Object>();
        history[i].put(A_TIMESTAMP, timestamp);
        return history[i];
      }
    }
    return null;
  }

  protected static final DateFormat HTTP_DATE_FORMAT = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz");

  protected FetchHistoryHelper() {
  }

  /**
   * converts time in HTTP Date format {@code dateStr} to seconds
   * since epoch. 
   * @param dateStr time in HTTP Date format.
   * @return seconds since epoch
   */
  public static long parseHttpDate(String dateStr) {
      synchronized (HTTP_DATE_FORMAT) {
          try {
              Date d = HTTP_DATE_FORMAT.parse(dateStr);
              return d.getTime() / 1000;
          } catch (ParseException ex) {
            if (logger.isDebugEnabled())
              logger.debug("bad HTTP DATE: " + dateStr);
              return 0;
          }
      }
  }

  public static String formatHttpDate(long time) {
      synchronized (HTTP_DATE_FORMAT) {
          // format(Date) is not thread safe either
          return HTTP_DATE_FORMAT.format(new Date(time * 1000));
      }
  }

}