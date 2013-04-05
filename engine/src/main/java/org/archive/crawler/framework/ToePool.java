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

package org.archive.crawler.framework;

import java.io.PrintWriter;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.lang.StringUtils;
import org.archive.crawler.reporting.AlertThreadGroup;
import org.archive.util.ArchiveUtils;
import org.archive.util.Histotable;
import org.archive.util.Reporter;

/**
 * A collection of ToeThreads. The class manages the ToeThreads currently
 * running. It offers methods for increasing and decreasing their 
 * number, keeping track of their state and (not necessarily safely)
 * killing hung threads.
 *
 * @author Gordon Mohr
 * @author Kristinn Sigurdsson
 *
 * @see org.archive.crawler.framework.ToeThread
 */
public class ToePool extends ThreadGroup implements Reporter {
    /** run worker thread slightly lower than usual */
    public static int DEFAULT_TOE_PRIORITY = Thread.NORM_PRIORITY - 1;
    
    protected CrawlController controller;
    protected int nextSerialNumber = 1;
    protected int targetSize = 0; 

    /**
     * Constructor. Creates a pool of ToeThreads. 
     *
     * @param c A reference to the CrawlController for the current crawl.
     */
    public ToePool(AlertThreadGroup atg, CrawlController c) {
        super(atg, "ToeThreads");        
        this.controller = c;
        setDaemon(true);
    }
    
    public void cleanup() {
    	// force all Toes waiting on queues, etc to proceed
        Thread[] toes = getToes();
        for(Thread toe : toes) {
            if(toe!=null) {
                toe.interrupt();
            }
        }
        
        // see HER-2036
        this.controller = null;
    }

    /**
     * @return The number of ToeThreads that are not available (Approximation).
     */
    public int getActiveToeCount() {
        Thread[] toes = getToes();
        int count = 0;
        for (int i = 0; i < toes.length; i++) {
            if((toes[i] instanceof ToeThread) &&
                    ((ToeThread)toes[i]).isActive()) {
                count++;
            }
        }
        return count; 
    }

    /**
     * @return The number of ToeThreads. This may include killed ToeThreads
     *         that were not replaced.
     */
    public int getToeCount() {
        Thread[] toes = getToes();
        int count = 0;
        for (int i = 0; i<toes.length; i++) {
            if((toes[i] instanceof ToeThread)) {
                count++;
            }
        }
        return count; 
    }
    
    private Thread[] getToes() {
        Thread[] toes = new Thread[activeCount()+10];
        this.enumerate(toes);
        return toes;
    }

    /**
     * Change the number of ToeThreads.
     *
     * @param newsize The new number of ToeThreads.
     */
    public void setSize(int newsize)
    {
        targetSize = newsize;
        int difference = newsize - getToeCount(); 
        if (difference > 0) {
            // must create threads
            for(int i = 1; i <= difference; i++) {
                startNewThread();
            }
        } else {
            // must retire extra threads
            int retainedToes = targetSize; 
            Thread[] toes = this.getToes();
            for (int i = 0; i < toes.length ; i++) {
                if(!(toes[i] instanceof ToeThread)) {
                    continue;
                }
                retainedToes--;
                if (retainedToes>=0) {
                    continue; // this toe is spared
                }
                // otherwise:
                ToeThread tt = (ToeThread)toes[i];
                tt.retire();
            }
        }
    }

    /**
     * Kills specified thread. Killed thread can be optionally replaced with a
     * new thread.
     *
     * <p><b>WARNING:</b> This operation should be used with great care. It may
     * destabilize the crawler.
     *
     * @param threadNumber Thread to kill
     * @param replace If true then a new thread will be created to take the
     *           killed threads place. Otherwise the total number of threads
     *           will decrease by one.
     */
    public void killThread(int threadNumber, boolean replace){

        Thread[] toes = getToes();
        for (int i = 0; i< toes.length; i++) {
            if(! (toes[i] instanceof ToeThread)) {
                continue;
            }
            ToeThread toe = (ToeThread) toes[i];
            if(toe.getSerialNumber()==threadNumber) {
                toe.kill();
            }
        }

        if(replace){
            // Create a new toe thread to take its place. Replace toe
            startNewThread();
        }
    }

    private synchronized void startNewThread() {
        ToeThread newThread = new ToeThread(this, nextSerialNumber++);
        newThread.setPriority(DEFAULT_TOE_PRIORITY);
        newThread.start();
    }

    /**
     * @return Instance of CrawlController.
     */
    public CrawlController getController() {
        return controller;
    }
    
    //
    // Reporter implementation
    //
    
    @Override
    public void reportTo(PrintWriter writer) {
        writer.print("Toe threads report - "
                + ArchiveUtils.get12DigitDate() + "\n");
        writer.print(" Job being crawled: "
                + this.controller.getMetadata().getJobName() + "\n");
        writer.print(" Number of toe threads in pool: " + getToeCount() + " ("
                + getActiveToeCount() + " active)\n\n");
        
        Thread[] toes = this.getToes();
        synchronized (toes) {
            for (int i = 0; i < toes.length; i++) {
                if (!(toes[i] instanceof ToeThread)) {
                    continue;
                }
                ToeThread tt = (ToeThread) toes[i];
                if (tt != null) {
                    tt.reportTo(writer);
                }
            }
        }
    }      
    
    public void compactReportTo(PrintWriter writer) {
        writer.print(getToeCount() + " threads (" + getActiveToeCount()
                + " active)\n");

        Thread[] toes = this.getToes();
        boolean legendWritten = false; 
        // TODO: sort by activity: those with curi the longest at front
        synchronized (toes) {
            for (int i = 0; i < toes.length; i++) {
                if (!(toes[i] instanceof ToeThread)) {
                    continue;
                }
                ToeThread tt = (ToeThread) toes[i];
                if (tt != null) {
                    if(!legendWritten) {
                        writer.println(tt.shortReportLegend());
                        legendWritten = true;
                    }
                    tt.shortReportLineTo(writer);
                }
            }
        }
    }

    @Override
    public Map<String, Object> shortReportMap() {
        Histotable<Object> steps = new Histotable<Object>();
        Histotable<Object> processors = new Histotable<Object>();
        Thread[] toes = getToes();
        for (int i = 0; i < toes.length; i++) {
            if(!(toes[i] instanceof ToeThread)) {
                continue;
            }
            ToeThread tt = (ToeThread)toes[i];
            if(tt!=null) {
                steps.tally(tt.getStep().toString());
                String currentProcessorName = tt.getCurrentProcessorName();
                if (StringUtils.isEmpty(currentProcessorName)) {
                    currentProcessorName = "noActiveProcessor";
                }
                processors.tally(currentProcessorName);
            }
        }

        Map<String,Object> data = new LinkedHashMap<String, Object>();

        data.put("toeCount", getToeCount());
        
        LinkedList<String> unwound = new LinkedList<String>(); 
        for (Entry<?, Long> step: steps.getSortedByCounts()) {
            unwound.add(step.getValue() + " " + step.getKey());
        }
        data.put("steps", unwound);

        unwound = new LinkedList<String>(); 
        for (Entry<?, Long> proc: processors.getSortedByCounts()) {
            unwound.add(proc.getValue() + " " + proc.getKey());
        }
        data.put("processors", unwound);
        
        return data;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void shortReportLineTo(PrintWriter w) {
        Map<String, Object> map = shortReportMap();
        w.print(map.get("toeCount"));
        w.print(" threads: ");
        
        LinkedList<String> sortedSteps = (LinkedList<String>)map.get("steps");
        {
        	Iterator<String> iter = sortedSteps.iterator();
        	if (!iter.hasNext()) {
        		return;
        	}
        	w.print(iter.next());
        	if (iter.hasNext()) {
        		w.print(", ");
        		w.print(iter.next());
        		if (iter.hasNext()) {
        			w.print(", etc...");
        		}
        	}
        	w.print("; ");
        }
        LinkedList<String> sortedProcesses = (LinkedList<String>)map.get("processors");
        {
        	Iterator<String> iter = sortedProcesses.iterator();
        	if (iter.hasNext()) {
        		w.print(iter.next());
        		while (iter.hasNext()) {
        			w.print(", ");
        			w.print(iter.next());
        		}
        	}
        }

    }

    /* (non-Javadoc)
     * @see org.archive.util.Reporter#singleLineLegend()
     */
    @Override
    public String shortReportLegend() {
        return "total: mostCommonStateTotal secondMostCommonStateTotal";
    }


    public void waitForAll() {
        while (true) try {
            if (isAllAlive(getToes())) {
                return;
            }
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            throw new IllegalStateException(e);
        }
    }
    
    
    private static boolean isAllAlive(Thread[] threads) {
        for (Thread t: threads) {
            if ((t != null) && (!t.isAlive())) {
                return false;
            }
        }
        return true;
    }
}
