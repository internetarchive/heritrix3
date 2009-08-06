/* BenchmarkUriUniqFilters
 *
 * $Id$
 *
 * Created on Jun 22, 2005.
 *
 * Copyright (C) 2005 Internet Archive.
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
package org.archive.crawler.util;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

import org.archive.crawler.datamodel.UriUniqFilter;
import org.archive.modules.CrawlURI;
import org.archive.util.fingerprint.MemLongFPSet;


/**
 * BenchmarkUriUniqFilters
 * 
 * @author gojomo
 */
public class BenchmarkUriUniqFilters implements UriUniqFilter.CrawlUriReceiver {
//    private Logger LOGGER =
//        Logger.getLogger(BenchmarkUriUniqFilters.class.getName());
    
    private BufferedWriter out; // optional to dump uniq items
    String current; // current line/URI being checked
    
    /**
     * Test the UriUniqFilter implementation (MemUriUniqFilter,
     * BloomUriUniqFilter, or BdbUriUniqFilter) named in first
     * argument against the file of one-per-line URIs named
     * in the second argument. 
     * 
     * @param args from cmd-line
     * @throws IOException
     */
    public static void main(String[] args) throws IOException {
        (new BenchmarkUriUniqFilters()).instanceMain(args);
    }
    
    public void instanceMain(String[] args) throws IOException {
        String testClass = args[0];
        String inputFilename = args[1];
        long start = System.currentTimeMillis();
        UriUniqFilter uniq = createUriUniqFilter(testClass);
        long created = System.currentTimeMillis();
        BufferedReader br = new BufferedReader(new FileReader(inputFilename));
        if(args.length>2) {
            String outputFilename = args[2];
            out = new BufferedWriter(new FileWriter(outputFilename));
        }
        int added = 0;
        while((current=br.readLine())!=null) {
            added++;
            uniq.add(current,null);
        }
        uniq.close();
        long finished = System.currentTimeMillis();
        if(out!=null) {
            out.close();
        }
        System.out.println(added+" adds");
        System.out.println(uniq.count()+" retained");
        System.out.println((created-start)+"ms to setup UUF");
        System.out.println((finished-created)+"ms to perform all adds");
    }
    
    private UriUniqFilter createUriUniqFilter(String testClass) throws IOException {
        UriUniqFilter uniq = null;
        if(BdbUriUniqFilter.class.getName().endsWith(testClass)) {;
            // BDB setup
            File tmpDir = File.createTempFile("uuf","benchmark");
            tmpDir.delete();
            tmpDir.mkdir();
            uniq = new BdbUriUniqFilter(tmpDir, 50);
        } else if(BloomUriUniqFilter.class.getName().endsWith(testClass)) {
            // bloom setup
            uniq = new BloomUriUniqFilter();
        } else if(MemUriUniqFilter.class.getName().endsWith(testClass)) {
            // mem hashset
            uniq = new MemUriUniqFilter();
        } else if (FPUriUniqFilter.class.getName().endsWith(testClass)) {
            // mem fp set (open-addressing) setup
            uniq = new FPUriUniqFilter(new MemLongFPSet(21,0.75f));
        }
        uniq.setDestination(this);
        return uniq;
    }

    /* (non-Javadoc)
     * @see org.archive.crawler.datamodel.UriUniqFilter.HasUriReceiver#receive(org.archive.crawler.datamodel.CrawlURI)
     */
    public void receive(CrawlURI item) {
        if(out!=null) {
            try {
                // we assume all tested filters are immediate passthrough so
                // we can use 'current'; a buffering filter would change this
                // assumption
                out.write(current);
                out.write("\n");
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
    }
}