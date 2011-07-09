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

package org.archive.modules.seeds;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.Iterator;
import java.util.concurrent.CountDownLatch;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.httpclient.URIException;
import org.apache.commons.io.IOUtils;
import org.archive.io.ReadSource;
import org.archive.modules.CrawlURI;
import org.archive.modules.SchedulingConstants;
import org.archive.net.UURI;
import org.archive.net.UURIFactory;
import org.archive.spring.WriteTarget;
import org.archive.util.ArchiveUtils;
import org.archive.util.DevUtils;
import org.archive.util.iterator.LineReadingIterator;
import org.archive.util.iterator.RegexLineIterator;
import org.springframework.beans.factory.annotation.Required;

/**
 * Module that announces a list of seeds from a text source (such
 * as a ConfigFile or ConfigString), and provides a mechanism for
 * adding seeds after a crawl has begun.
 *
 * @contributor gojomo
 */
public class TextSeedModule extends SeedModule 
implements ReadSource {
    private static final long serialVersionUID = 3L;

    private static final Logger logger =
        Logger.getLogger(TextSeedModule.class.getName());

    /**
     * Text from which to extract seeds
     */
    protected ReadSource textSource = null;
    public ReadSource getTextSource() {
        return textSource;
    }
    @Required
    public void setTextSource(ReadSource seedsSource) {
        this.textSource = seedsSource;
    }
    
    /**
     * Number of lines of seeds-source to read on initial load before proceeding
     * with crawl. Default is -1, meaning all. Any other value will cause that
     * number of lines to be loaded before fetching begins, while all extra
     * lines continue to be processed in the background. Generally, this should
     * only be changed when working with very large seed lists, and scopes that
     * do *not* depend on reading all seeds. 
     */
    protected int blockAwaitingSeedLines = -1;
    public int getBlockAwaitingSeedLines() {
        return blockAwaitingSeedLines;
    }
    public void setBlockAwaitingSeedLines(int blockAwaitingSeedLines) {
        this.blockAwaitingSeedLines = blockAwaitingSeedLines;
    }

    public TextSeedModule() {
    }

    /**
     * Announce all seeds from configured source to SeedListeners 
     * (including nonseed lines mixed in). 
     * @see org.archive.modules.seeds.SeedModule#announceSeeds()
     */
    public void announceSeeds() {
        if(getBlockAwaitingSeedLines()>-1) {
            final CountDownLatch latch = new CountDownLatch(getBlockAwaitingSeedLines());
            new Thread(){
                @Override
                public void run() {
                    announceSeeds(latch); 
                    while(latch.getCount()>0) {
                        latch.countDown();
                    }
                }
            }.start();
            try {
                latch.await();
            } catch (InterruptedException e) {
                // do nothing
            } 
        } else {
            announceSeeds(null); 
        }
    }
    
    protected void announceSeeds(CountDownLatch latchOrNull) {
        BufferedReader reader = new BufferedReader(textSource.obtainReader());       
        try {
            announceSeedsFromReader(reader,latchOrNull);    
        } finally {
            IOUtils.closeQuietly(reader);
        }
    }
            
    /**
     * Announce all seeds (and nonseed possible-directive lines) from
     * the given Reader
     * @param reader source of seed/directive lines
     * @param latchOrNull if non-null, sent countDown after each line, allowing 
     * another thread to proceed after a configurable number of lines processed
     */
    protected void announceSeedsFromReader(BufferedReader reader, CountDownLatch latchOrNull) {
        String s;
        Iterator<String> iter = 
            new RegexLineIterator(
                    new LineReadingIterator(reader),
                    RegexLineIterator.COMMENT_LINE,
                    RegexLineIterator.NONWHITESPACE_ENTRY_TRAILING_COMMENT,
                    RegexLineIterator.ENTRY);

        int count = 0; 
        while (iter.hasNext()) {
            s = (String) iter.next();
            if(Character.isLetterOrDigit(s.charAt(0))) {
                // consider a likely URI
                seedLine(s);
                count++;
                if(count%20000==0) {
                    System.runFinalization();
                }
            } else {
                // report just in case it's a useful directive
                nonseedLine(s);
            }
            if(latchOrNull!=null) {
                latchOrNull.countDown(); 
            }
        }
        publishConcludedSeedBatch(); 
    }
    
    /**
     * Handle a read line that is probably a seed.
     * 
     * @param uri String seed-containing line
     */
    protected void seedLine(String uri) {
        if (!uri.matches("[a-zA-Z][\\w+\\-]+:.*")) { // Rfc2396 s3.1 scheme,
                                                     // minus '.'
            // Does not begin with scheme, so try http://
            uri = "http://" + uri;
        }
        try {
            UURI uuri = UURIFactory.getInstance(uri);
            CrawlURI curi = new CrawlURI(uuri);
            curi.setSeed(true);
            curi.setSchedulingDirective(SchedulingConstants.MEDIUM);
            if (getSourceTagSeeds()) {
                curi.setSourceTag(curi.toString());
            }
            publishAddedSeed(curi);
        } catch (URIException e) {
            // try as nonseed line as fallback
            nonseedLine(uri);
        }
    }
    
    /**
     * Handle a read line that is not a seed, but may still have
     * meaning to seed-consumers (such as scoping beans). 
     * 
     * @param uri String seed-containing line
     */
    protected void nonseedLine(String line) {
        publishNonSeedLine(line);
    }
    
    /**
     * Treat the given file as a source of additional seeds,
     * announcing to SeedListeners.
     * 
     * @see org.archive.modules.seeds.SeedModule#actOn(java.io.File)
     */
    public void actOn(File f) {
        BufferedReader reader = null;
        try {
            reader = ArchiveUtils.getBufferedReader(f);
            announceSeedsFromReader(reader, null);    
        } catch (IOException ioe) {
            logger.log(Level.SEVERE,"problem reading seed file "+f,ioe);
        } finally {
            IOUtils.closeQuietly(reader);
        }
    }

    /**
     * Add a new seed to scope. By default, simply appends
     * to seeds file, though subclasses may handle differently.
     *
     * <p>This method is *not* sufficient to get the new seed 
     * scheduled in the Frontier for crawling -- it only 
     * affects the Scope's seed record (and decisions which
     * flow from seeds). 
     *
     * @param curi CandidateUri to add
     * @return true if successful, false if add failed for any reason
     */
    @Override
    public synchronized void addSeed(final CrawlURI curi) {
        if(!(textSource instanceof WriteTarget)) {
            // TODO: do something else to log seed update
            logger.warning("nowhere to log added seed: "+curi);
        } else {
            // TODO: determine if this modification to seeds file means
            // TextSeedModule should (again) be Checkpointable
            try {
                Writer fw = ((WriteTarget)textSource).obtainWriter(true);
                // Write to new (last) line the URL.
                fw.write("\n");
                fw.write("# Heritrix added seed " +
                    ((curi.getVia() != null) ? "redirect from " + curi.getVia():
                        "(JMX)") + ".\n");
                fw.write(curi.toString());
                fw.flush();
                fw.close();
            } catch (IOException e) {
                DevUtils.warnHandle(e, "problem writing new seed");
            }
        }
        publishAddedSeed(curi); 
    }

    public Reader obtainReader() {
        return textSource.obtainReader();
    }
}
