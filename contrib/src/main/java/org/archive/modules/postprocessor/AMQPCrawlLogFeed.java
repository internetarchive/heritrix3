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
package org.archive.modules.postprocessor;

import java.io.UnsupportedEncodingException;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import org.archive.crawler.framework.Frontier;
import org.archive.crawler.frontier.AbstractFrontier;
import org.archive.crawler.io.UriProcessingFormatter;
import org.archive.modules.AMQPProducerProcessor;
import org.archive.modules.CoreAttributeConstants;
import org.archive.modules.CrawlURI;
import org.archive.modules.net.CrawlHost;
import org.archive.modules.net.ServerCache;
import org.archive.util.ArchiveUtils;
import org.archive.util.MimetypeUtils;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * @see UriProcessingFormatter
 * @contributor nlevitt
 */
public class AMQPCrawlLogFeed extends AMQPProducerProcessor {

    private final static int GUESS_AT_LOG_LENGTH = 1200;

    protected Frontier frontier;
    public Frontier getFrontier() {
        return this.frontier;
    }
    /** Autowired frontier, needed to determine when a url is finished. */
    @Autowired
    public void setFrontier(Frontier frontier) {
        this.frontier = frontier;
    }

    protected ServerCache serverCache;
    public ServerCache getServerCache() {
        return this.serverCache;
    }
    @Autowired
    public void setServerCache(ServerCache serverCache) {
        this.serverCache = serverCache;
    }

    protected List<String> prependValues;
    public List<String> getPrependValues() {
        return prependValues;
    }
    public void setPrependValues(List<String> prependValues) {
        this.prependValues = prependValues;
    }

    public AMQPCrawlLogFeed() {
        // set default values
        exchange = "heritrix.realTimeFeed";
        routingKey = "crawlLog";
    }

    /**
     * Reusable assembly buffer.
     */
    protected final ThreadLocal<StringBuilder> bufLocal = new ThreadLocal<StringBuilder>() {
        @Override
        protected StringBuilder initialValue() {
            return new StringBuilder(GUESS_AT_LOG_LENGTH);
        }
    };

    private final static String NA = "-";
    private final static String DELIM = "\t";

    /**
     * @param str
     *            String to check.
     * @return Return passed string or <code>NA</code> if null.
     */
    protected String checkForNull(String str) {
        return (str == null || str.length() <= 0) ? NA : str;
    }

    @Override
    protected byte[] buildMessage(CrawlURI curi) {
        String length = NA;

        if (curi.isHttpTransaction()) {
            if (curi.getContentLength() >= 0) {
                length = Long.toString(curi.getContentLength());
            } else if (curi.getContentSize() > 0) {
                length = Long.toString(curi.getContentSize());
            }
        } else {
            if (curi.getContentSize() > 0) {
                length = Long.toString(curi.getContentSize());
            }
        }

        StringBuilder buffer = bufLocal.get();
        buffer.setLength(0);

        if (getPrependValues() != null) {
            for (String v: getPrependValues()) {
                buffer.append(v).append(DELIM);
            }
        }

        buffer.append(ArchiveUtils.getLog17Date(System.currentTimeMillis()));
        buffer.append(DELIM).append(curi.getFetchStatus());
        buffer.append(DELIM).append(length);
        buffer.append(DELIM).append(curi.getUURI().toString());
        buffer.append(DELIM).append(checkForNull(curi.getPathFromSeed()));
        buffer.append(DELIM).append(checkForNull(curi.flattenVia()));
        buffer.append(DELIM).append(MimetypeUtils.truncate(curi.getContentType()));
        buffer.append(DELIM).append("#").append(curi.getThreadNumber());

        // arcTimeAndDuration
        buffer.append(DELIM);
        if (curi.containsDataKey(CoreAttributeConstants.A_FETCH_COMPLETED_TIME)) {
            long completedTime = curi.getFetchCompletedTime();
            long beganTime = curi.getFetchBeginTime();
            buffer.append(ArchiveUtils.get17DigitDate(beganTime)).append("+").append(Long.toString(completedTime - beganTime));
        } else {
            buffer.append(NA);
        }

        buffer.append(DELIM).append(checkForNull(curi.getContentDigestSchemeString()));
        buffer.append(DELIM).append(checkForNull(curi.getSourceTag()));

        buffer.append(DELIM);
        CrawlHost host = getServerCache().getHostFor(curi.getUURI());
        if (host != null) {
            buffer.append(host.fixUpName());
        } else {
            buffer.append(NA);
        }

        buffer.append(DELIM);
        Collection<String> anno = curi.getAnnotations();
        if (anno != null && anno.size() > 0) {
            Iterator<String> iter = anno.iterator();
            buffer.append(iter.next());
            while (iter.hasNext()) {
                buffer.append(',');
                buffer.append(iter.next());
            }
        } else {
            buffer.append(NA);
        }

        buffer.append(DELIM).append(curi.getExtraInfo());

        String str = buffer.toString();
        try {
            return str.getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected boolean shouldProcess(CrawlURI curi) {
        if (frontier instanceof AbstractFrontier) {
            return !((AbstractFrontier) frontier).needsReenqueuing(curi);
        } else {
            return false;
        }
    }

}
