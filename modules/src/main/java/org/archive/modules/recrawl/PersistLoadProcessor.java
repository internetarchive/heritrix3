/* PersistLoadProcessor.java
 * 
 * Created on Feb 13, 2005
 *
 * Copyright (C) 2007 Internet Archive.
 * 
 * This file is part of the Heritrix web crawler (crawler.archive.org).
 * 
 * Heritrix is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser Public License as published by
 * the Free Software Foundation; either version 2.1 of the License, or
 * any later version.
 * 
 * Heritrix is distributed in the hope that it will be useful, 
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser Public License
 * along with Heritrix; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */
package org.archive.modules.recrawl;

import java.io.IOException;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.lang.StringUtils;
import org.archive.modules.recrawl.PersistLoadProcessor;
import org.archive.modules.recrawl.PersistProcessor;
import org.archive.modules.ProcessorURI;
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
    private static final long serialVersionUID = -1917169316015093131L;
    private static final Logger logger =
        Logger.getLogger(PersistLoadProcessor.class.getName());

    // class description: "PersistLoadProcessor. Loads CrawlURI attributes " +
    // "from a previous crawl for current consultation."
    
    
    /**
     * A source (either log file or BDB directory) from which to copy
     * history information into the current store at startup. (Whenever
     * possible, it would be better to ensure the original history DB 
     * is in its own independent BDB environment, and then copy and 
     * reuse that environment in the followup crawl(s).)
     */
    ConfigPath preloadSource = 
        new ConfigFile("preload source","");
    public ConfigPath getPreloadSource() {
        return preloadSource;
    }
    public void setPreloadSource(ConfigPath preloadSource) {
        this.preloadSource = preloadSource;
    }
    public PersistLoadProcessor() {
    }

    @SuppressWarnings("unchecked")
    @Override
    protected void innerProcess(ProcessorURI curi) throws InterruptedException {
        Map<String, Object> prior = 
        	(Map<String,Object>) store.get(persistKeyFor(curi));
        if(prior!=null) {
            // merge in keys
            curi.getData().putAll(prior); 
        }
    }

    @Override
    protected boolean shouldProcess(ProcessorURI uri) {
        return shouldLoad(uri);
    }

    @Override
    public void start() {
        super.start();
        String psource = getPreloadSource().getPath();
        if (StringUtils.isNotBlank(psource)) {
            try {
                int count = PersistProcessor.copyPersistSourceToHistoryMap(
                        null, preloadSource.getFile().getAbsolutePath(),store);
                logger.info("Loaded deduplication information for " + count + " previously fetched urls from " + preloadSource);
            } catch (IOException ioe) {
                logger.log(Level.WARNING, "Problem loading " + preloadSource + ", proceeding without deduplication! " + ioe);
            } catch(DatabaseException de) {
                logger.log(Level.WARNING, "Problem loading " + preloadSource + ", proceeding without deduplication! " + de);
            }
        }
    }
} //EOC