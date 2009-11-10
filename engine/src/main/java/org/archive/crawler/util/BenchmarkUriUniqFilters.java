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