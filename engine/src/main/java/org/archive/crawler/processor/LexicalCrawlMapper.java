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
package org.archive.crawler.processor;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.net.URLConnection;
import java.util.Iterator;
import java.util.SortedMap;
import java.util.TreeMap;

import org.archive.crawler.framework.Frontier;
import org.archive.modules.CrawlURI;
import org.archive.spring.ConfigPath;
import org.archive.util.iterator.LineReadingIterator;
import org.archive.util.iterator.RegexLineIterator;
import org.springframework.beans.factory.annotation.Autowired;


/**
 * A simple crawl splitter/mapper, dividing up CrawlURIs/CrawlURIs
 * between crawlers by diverting some range of URIs to local log files
 * (which can then be imported to other crawlers). 
 * 
 * May operate on a CrawlURI (typically early in the processing chain) or
 * its CrawlURI outlinks (late in the processing chain, after 
 * LinksScoper), or both (if inserted and configured in both places). 
 * 
 * <p>Uses lexical comparisons of classKeys to map URIs to crawlers. The
 * 'map' is specified via either a local or HTTP-fetchable file. Each
 * line of this file should contain two space-separated tokens, the
 * first a key and the second a crawler node name (which should be
 * legal as part of a filename). All URIs will be mapped to the crawler
 * node name associated with the nearest mapping key equal or subsequent 
 * to the URI's own classKey. If there are no mapping keys equal or 
 * after the classKey, the mapping 'wraps around' to the first mapping key.
 * 
 * <p>One crawler name is distinguished as the 'local name'; URIs mapped to
 * this name are not diverted, but continue to be processed normally.
 * 
 * <p>For example, assume a SurtAuthorityQueueAssignmentPolicy and
 * a simple mapping file:
 * 
 * <pre>
 *  d crawlerA
 *  ~ crawlerB
 * </pre>
 * <p>All URIs with "com," classKeys will find the 'd' key as the nearest
 * subsequent mapping key, and thus be mapped to 'crawlerA'. If that's
 * the 'local name', the URIs will be processed normally; otherwise, the
 * URI will be written to a diversion log aimed for 'crawlerA'. 
 * 
 * <p>If using the JMX importUris operation importing URLs dropped by
 * a {@link LexicalCrawlMapper} instance, use <code>recoveryLog</code> style.
 * 
 * @author gojomo
 * @version $Date$, $Revision$
 */
public class LexicalCrawlMapper extends CrawlMapper {
    @SuppressWarnings("unused")
    private static final long serialVersionUID = 2L;

    /**
     * Path to map specification file. Each line should include 2
     * whitespace-separated tokens: the first a key indicating the end of a
     * range, the second the crawler node to which URIs in the key range should
     * be mapped.  This setting is ignored if MAP_URI is specified.
     */
    protected ConfigPath mapPath = new ConfigPath("map specification file","lexicalcrawlmapper.config");
    public ConfigPath getMapPath() {
        return this.mapPath;
    }
    public void setMapPath(ConfigPath path) {
        this.mapPath = path; 
    }


    /**
     * URI to map specification file. Each line should include 2
     * whitespace-separated tokens: the first a key indicating the end of a
     * range, the second the crawler node to which URIs in the key range should
     * be mapped.  This setting takes precedence over MAP_PATH; if both are
     * specified, then MAP_PATH is ignored.
     */
    protected String mapUri = "";
    public String getMapUri() {
        return this.mapUri;
    }
    public void setMapUri(String uri) {
        this.mapUri = uri; 
    }

    protected Frontier frontier;
    public Frontier getFrontier() {
        return this.frontier;
    }
    @Autowired
    public void setFrontier(Frontier frontier) {
        this.frontier = frontier;
    }

    /**
     * Mapping of classKey ranges (as represented by their start) to 
     * crawlers (by abstract name/filename)
     */
    protected TreeMap<String, String> map = new TreeMap<String, String>();
    
    /**
     * Constructor.
     */
    public LexicalCrawlMapper() {
        super();
    }


    /**
     * Look up the crawler node name to which the given CrawlURI 
     * should be mapped. 
     * 
     * @param cauri CrawlURI to consider
     * @return String node name which should handle URI
     */
    protected String map(CrawlURI cauri) {
        // get classKey, via frontier to generate if necessary
        String classKey = frontier.getClassKey(cauri);
        SortedMap<String,String> tail = map.tailMap(classKey);
        if(tail.isEmpty()) {
            // wraparound
            tail = map;
        }
        // target node is value of nearest subsequent key
        return (String) tail.get(tail.firstKey());
    }

    public void start() {
        super.start();
        try {
            loadMap();
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    /**
     * Retrieve and parse the mapping specification from a local path or
     * HTTP URL. 
     * 
     * @throws IOException
     */
    protected void loadMap() throws IOException {
        map.clear();
        String uri = getMapUri();
        Reader reader = null;
        if (uri.trim().length() == 0) {
            File source = getMapPath().getFile();
            reader = new FileReader(source);
        } else {
            URLConnection conn = (new URL(uri)).openConnection();
            reader = new InputStreamReader(conn.getInputStream());
        }
        reader = new BufferedReader(reader);
        Iterator<String> iter = 
            new RegexLineIterator(
                    new LineReadingIterator((BufferedReader) reader),
                    RegexLineIterator.COMMENT_LINE,
                    RegexLineIterator.TRIMMED_ENTRY_TRAILING_COMMENT,
                    RegexLineIterator.ENTRY);
        while (iter.hasNext()) {
            String[] entry = ((String) iter.next()).split("\\s+");
            map.put(entry[0],entry[1]);
        }
        reader.close();
    }
}