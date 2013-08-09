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
package org.archive.modules.recrawl.hbase;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.hadoop.hbase.client.Get;
import org.apache.hadoop.hbase.client.Result;
import org.archive.modules.CrawlURI;
import org.archive.modules.ProcessResult;
import org.archive.modules.Processor;
import org.archive.modules.recrawl.FetchHistoryProcessor;

/**
 * A {@link Processor} for retrieving recrawl info from HBase table.
 * See {@link HBasePersistProcessor} for table schema.
 * As with other fetch history processors, this needs to be combined with {@link FetchHistoryProcessor}
 * (set up after FetchHTTP, before WarcWriter) to work.
 * @see HBasePersistStoreProcessor
 * @contributor kenji
 */
public class HBasePersistLoadProcessor extends HBasePersistProcessor {
    private static final Logger logger =
            Logger.getLogger(HBasePersistLoadProcessor.class.getName());

    @Override
    protected ProcessResult innerProcessResult(CrawlURI uri) throws InterruptedException {
        byte[] key = rowKeyForURI(uri);
        Get g = new Get(key);
        try {
            Result r = table.get(g);
            // no data for uri is indicated by empty Result
            if (r.isEmpty()) {
                if (logger.isLoggable(Level.FINE)) {
                    logger.fine(uri + ": <no crawlinfo>");
                }
                return ProcessResult.PROCEED;
            }
            schema.load(r, uri);
            if (uri.getFetchStatus() < 0) {
                return ProcessResult.FINISH;
            }
        } catch (IOException e) {
            logger.warning("problem retrieving persist data from hbase, proceeding without, for " + uri + " - " + e);
        } catch (Exception ex) {
            // get() throws RuntimeException upon ZooKeeper connection failures.
            // no crawl history load failure should make fetch of URL fail.
            logger.log(Level.WARNING, "Get failed for " + uri + ": ", ex);
        }
        return ProcessResult.PROCEED;
    }

    /**
     * unused.
     */
    @Override
    protected void innerProcess(CrawlURI uri) throws InterruptedException {
    }

    @Override
    protected boolean shouldProcess(CrawlURI uri) {
        // TODO: we want deduplicate robots.txt, too.
        //if (uri.isPrerequisite()) return false;
        String scheme = uri.getUURI().getScheme();
        if (!(scheme.equals("http") || scheme.equals("https") || scheme.equals("ftp")))  {
            return false;
        }
        return true;
    }
}
