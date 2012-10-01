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

import java.io.IOException;
import java.net.URL;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.lang.StringUtils;
import org.archive.modules.CrawlURI;
import org.archive.spring.ConfigFile;
import org.archive.spring.ConfigPath;

import com.sleepycat.je.DatabaseException;

/**
 * Loads CrawlURI attributes from previous fetch from persistent storage for
 * consultation by a later recrawl. 
 * 
 * @author gojomo
 * @version $Date: 2006-09-25 20:19:54 +0000 (Mon, 25 Sep 2006) $, $Revision: 4654 $
 */
public class PersistLoadProcessor extends PersistOnlineProcessor {
    @SuppressWarnings("unused")
    private static final long serialVersionUID = -1917169316015093131L;
    private static final Logger logger =
        Logger.getLogger(PersistLoadProcessor.class.getName());

    // class description: "PersistLoadProcessor. Loads CrawlURI attributes " +
    // "from a previous crawl for current consultation."

    /**
     * A source (either log file or BDB directory) from which to copy history
     * information into the current store at startup. (Whenever possible, it
     * would be better to ensure the original history DB is in its own
     * independent BDB environment, and then copy and reuse that environment in
     * the followup crawl(s).) Only one of {@preloadSource} and
     * {@preloadSourceUrl} may be specified.
     */
    protected ConfigPath preloadSource = 
        new ConfigFile("preload source","");
    public ConfigPath getPreloadSource() {
        return preloadSource;
    }
    public void setPreloadSource(ConfigPath preloadSource) {
        this.preloadSource = preloadSource;
    }
    public PersistLoadProcessor() {
    }

    /**
     * A log file source url from which to copy history information into the
     * current store at startup. (Whenever possible, it would be better to
     * ensure the original history DB is in its own independent BDB environment,
     * and then copy and reuse that environment in the followup crawl(s).)
     * Only one of {@preloadSource} and {@preloadSourceUrl} may be specified.
     */
    protected String preloadSourceUrl = "";
    public String getPreloadSourceUrl() {
        return preloadSourceUrl;
    }
    public void setPreloadSourceUrl(String preloadSourceUrl) {
        this.preloadSourceUrl = preloadSourceUrl;
    }
    
    @Override
    protected void innerProcess(CrawlURI curi) throws InterruptedException {
        String pkey = persistKeyFor(curi);
        @SuppressWarnings("unchecked")
        Map<String, Object> prior = 
        	(Map<String,Object>) store.get(pkey);
        if(prior!=null) {
            // merge in keys
            prior.keySet().removeAll(curi.getData().keySet());
            curi.getData().putAll(prior); 
        }
    }

    @Override
    protected boolean shouldProcess(CrawlURI uri) {
        return shouldLoad(uri);
    }

    @Override
    public void start() {
        if (isRunning()) {
            return;
        }
        super.start();
        if (StringUtils.isNotBlank(getPreloadSourceUrl()) && StringUtils.isNotBlank(getPreloadSource().getPath())) {
            logger.log(Level.SEVERE, "Both preloadSource and preloadSourceUrl are set - using preloadSource " + getPreloadSource().getFile());
        }
        String source = null;
        Integer count = null;
        try {
            if (StringUtils.isNotBlank(getPreloadSource().getPath())) {
                source = preloadSource.getPath();
                count = PersistProcessor.copyPersistSourceToHistoryMap(preloadSource.getFile(), store);
            } else if (StringUtils.isNotBlank(getPreloadSourceUrl())) {
                source = getPreloadSourceUrl();
                count = PersistProcessor.copyPersistSourceToHistoryMap(new URL(source), store);
            }

            if (count != null) {
                logger.info("Loaded deduplication information for " + count + " previously fetched urls from " + source);
            }
        } catch (IOException ioe) {
            logger.log(Level.SEVERE, "Problem loading " + source + ", proceeding without deduplication. " + ioe);
        } catch(DatabaseException de) {
            logger.log(Level.SEVERE, "Problem loading " + source + ", proceeding without deduplication. " + de);
        } catch(IllegalArgumentException iae) {
            logger.log(Level.SEVERE, "Problem loading " + source + ", proceeding without deduplication. " + iae);
        }
    }
} //EOC