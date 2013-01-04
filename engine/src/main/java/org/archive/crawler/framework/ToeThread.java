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

import static org.archive.modules.CoreAttributeConstants.A_RUNTIME_EXCEPTION;
import static org.archive.modules.fetcher.FetchStatusCodes.S_PROCESSING_THREAD_KILLED;
import static org.archive.modules.fetcher.FetchStatusCodes.S_RUNTIME_EXCEPTION;
import static org.archive.modules.fetcher.FetchStatusCodes.S_SERIOUS_ERROR;

import java.io.PrintWriter;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.net.InetAddress;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.archive.io.SinkHandlerLogThread;
import org.archive.modules.CrawlURI;
import org.archive.modules.Processor;
import org.archive.modules.ProcessorChain.ChainStatusReceiver;
import org.archive.modules.fetcher.HostResolver;
import org.archive.spring.KeyedProperties;
import org.archive.util.ArchiveUtils;
import org.archive.util.DevUtils;
import org.archive.util.ProgressStatisticsReporter;
import org.archive.util.Recorder;
import org.archive.util.ReportUtils;
import org.archive.util.Reporter;

import com.sleepycat.util.RuntimeExceptionWrapper;

/**
 * One "worker thread"; asks for CrawlURIs, processes them,
 * repeats unless told otherwise.
 *
 * @author Gordon Mohr
 */
public class ToeThread extends Thread
implements Reporter, ProgressStatisticsReporter, 
           HostResolver, SinkHandlerLogThread, ChainStatusReceiver {

    public enum Step {
        NASCENT, ABOUT_TO_GET_URI, FINISHED, 
        ABOUT_TO_BEGIN_PROCESSOR, HANDLING_RUNTIME_EXCEPTION, 
        ABOUT_TO_RETURN_URI, FINISHING_PROCESS
    }

    private static Logger logger =
        Logger.getLogger("org.archive.crawler.framework.ToeThread");

    private CrawlController controller;
    private int serialNumber;
    
    /**
     * Each ToeThead has an instance of HttpRecord that gets used
     * over and over by each request.
     * 
     * @see org.archive.util.RecorderMarker
     */
    private Recorder httpRecorder = null;

    // activity monitoring, debugging, and problem detection
    private Step step = Step.NASCENT;
    private long atStepSince;
    private String currentProcessorName = "";
    
    private String coreName;
    private CrawlURI currentCuri;
    private long lastStartTime;
    private long lastFinishTime;

    
    // default priority; may not be meaningful in recent JVMs
    private static final int DEFAULT_PRIORITY = Thread.NORM_PRIORITY-2;
    
    // indicator that a thread is now surplus based on current desired
    // count; it should wrap up cleanly
    private volatile boolean shouldRetire = false;
    
    /**
     * Create a ToeThread
     * 
     * @param g ToeThreadGroup
     * @param sn serial number
     */
    public ToeThread(ToePool g, int sn) {
        // TODO: add crawl name?
        super(g,"ToeThread #" + sn);
        coreName="ToeThread #" + sn + ": ";
        controller = g.getController();
        serialNumber = sn;
        setPriority(DEFAULT_PRIORITY);
        int outBufferSize = controller.getRecorderOutBufferBytes();
        int inBufferSize = controller.getRecorderInBufferBytes();
        httpRecorder = new Recorder(controller.getScratchDir().getFile(),
            "tt" + sn + "http", outBufferSize, inBufferSize);
        lastFinishTime = System.currentTimeMillis();
    }

    /** (non-Javadoc)
     * @see java.lang.Thread#run()
     */
    public void run() {
        String name = controller.getMetadata().getJobName();
        logger.fine(getName()+" started for order '"+name+"'");
        Recorder.setHttpRecorder(httpRecorder); 
        
        try {
            while ( true ) {
                ArchiveUtils.continueCheck();
                
                setStep(Step.ABOUT_TO_GET_URI, null);

                CrawlURI curi = controller.getFrontier().next();
                
                
                synchronized(this) {
                    ArchiveUtils.continueCheck();
                    setCurrentCuri(curi);
                    currentCuri.setThreadNumber(this.serialNumber);
                    lastStartTime = System.currentTimeMillis();
                    currentCuri.setRecorder(httpRecorder);
                }
                
                try {
                    KeyedProperties.loadOverridesFrom(curi);
                    
                    controller.getFetchChain().process(curi,this);
                    
                    controller.getFrontier().beginDisposition(curi);
                    
                    controller.getDispositionChain().process(curi,this);
  
                } catch (RuntimeExceptionWrapper e) {
                    // Workaround to get cause from BDB
                    if(e.getCause() == null) {
                        e.initCause(e.getCause());
                    }
                    recoverableProblem(e);
                } catch (AssertionError ae) {
                    // This risks leaving crawl in fatally inconsistent state, 
                    // but is often reasonable for per-Processor assertion problems 
                    recoverableProblem(ae);
                } catch (RuntimeException e) {
                    recoverableProblem(e);
                } catch (InterruptedException e) {
                    if(currentCuri!=null) {
                        recoverableProblem(e);
                        Thread.interrupted(); // clear interrupt status
                    } else {
                        throw e;
                    }
                } catch (StackOverflowError err) {
                    recoverableProblem(err);
                } catch (Error err) {
                    // OutOfMemory and any others
                    seriousError(err); 
                } finally {
                    httpRecorder.endReplays();
                    KeyedProperties.clearOverridesFrom(curi); 
                }
                
                setStep(Step.ABOUT_TO_RETURN_URI, null);
                ArchiveUtils.continueCheck();

                synchronized(this) {
                    controller.getFrontier().finished(currentCuri);
                    controller.getFrontier().endDisposition();
                    setCurrentCuri(null);
                }
                curi = null;
                
                setStep(Step.FINISHING_PROCESS, null);
                lastFinishTime = System.currentTimeMillis();
                if(shouldRetire) {
                    break; // from while(true)
                }
            }
        } catch (InterruptedException e) {
            if(currentCuri!=null){
                logger.log(Level.SEVERE,"Interrupt leaving unfinished CrawlURI "+getName()+" - job may hang",e);
            }
            // thread interrupted, ok to end
            logger.log(Level.FINE,this.getName()+ " ended with Interruption");
        } catch (Exception e) {
            // everything else (including interruption)
            logger.log(Level.SEVERE,"Fatal exception in "+getName(),e);
        } catch (OutOfMemoryError err) {
            seriousError(err);
        } finally {
            controller.getFrontier().endDisposition();

        }

        setCurrentCuri(null);
        // Do cleanup so that objects can be GC.
        this.httpRecorder.closeRecorders();
        this.httpRecorder = null;

        logger.fine(getName()+" finished for order '"+name+"'");
        setStep(Step.FINISHED, null);
        controller = null;
    }

    /**
     * Set currentCuri, updating thread name as appropriate
     * @param curi
     */
    private void setCurrentCuri(CrawlURI curi) {
        if(curi==null) {
            setName(coreName);
        } else {
            setName(coreName+curi);
        }
        currentCuri = curi;
    }

    /**
     * @param s
     */
    public void setStep(Step s, String procName) {
        step=s;
        atStepSince = System.currentTimeMillis();
        currentProcessorName = procName != null ? procName : "";
    }
    
    public void atProcessor(Processor proc) {
        setStep(Step.ABOUT_TO_BEGIN_PROCESSOR, proc.getBeanName());
    }

    private void seriousError(Error err) {
        // try to prevent timeslicing until we have a chance to deal with OOM
        // Note that modern-day JVM priority indifference with native threads
        // may make this priority-jumbling pointless
        setPriority(DEFAULT_PRIORITY+1);  
        if (controller!=null) {
            // hold all ToeThreads from proceeding to next processor
            controller.freeReserveMemory();
            controller.requestCrawlPause();
            if (controller.getFrontier().getFrontierJournal() != null) {
                controller.getFrontier().getFrontierJournal().seriousError(
                    getName() + err.getMessage());
            }
        }
        
        // OutOfMemory etc.
        String extraInfo = DevUtils.extraInfo();
        System.err.println("<<<");
        System.err.println(ArchiveUtils.getLog17Date());
        System.err.println(err);
        System.err.println(extraInfo);
        err.printStackTrace(System.err);
        
        if (controller!=null) {
            PrintWriter pw = new PrintWriter(System.err);
            controller.getToePool().compactReportTo(pw);
            pw.flush();
        }
        System.err.println(">>>");
//        DevUtils.sigquitSelf();
        
        String context = "unknown";
        if(currentCuri!=null) {
            // update fetch-status, saving original as annotation
            currentCuri.getAnnotations().add("err="+err.getClass().getName());
            currentCuri.getAnnotations().add("os"+currentCuri.getFetchStatus());
                        currentCuri.setFetchStatus(S_SERIOUS_ERROR);
            context = currentCuri.shortReportLine() + " in " + currentProcessorName;
         }
        String message = "Serious error occured trying " +
            "to process '" + context + "'\n" + extraInfo;
        logger.log(Level.SEVERE, message.toString(), err);
        setPriority(DEFAULT_PRIORITY);
    }

    /**
     * Handling for exceptions and errors that are possibly recoverable.
     * 
     * @param e
     */
    private void recoverableProblem(Throwable e) {
        Object previousStep = step;
        setStep(Step.HANDLING_RUNTIME_EXCEPTION, null);
        //e.printStackTrace(System.err);
        currentCuri.setFetchStatus(S_RUNTIME_EXCEPTION);
        // store exception temporarily for logging
        currentCuri.getAnnotations().add("err="+e.getClass().getName());
        currentCuri.getData().put(A_RUNTIME_EXCEPTION, e);
        String message = "Problem " + e + 
                " occured when trying to process '"
                + currentCuri.toString()
                + "' at step " + previousStep 
                + " in " + currentProcessorName +"\n";
        logger.log(Level.SEVERE, message.toString(), e);
    }


    /**
     * @return Return toe thread serial number.
     */
    public int getSerialNumber() {
        return this.serialNumber;
    }
    
    /** Get the CrawlController acossiated with this thread.
     *
     * @return Returns the CrawlController.
     */
    public CrawlController getController() {
        return controller;
    }

    /**
     * Terminates a thread.
     *
     * <p> Calling this method will ensure that the current thread will stop
     * processing as soon as possible (note: this may be never). Meant to
     * 'short circuit' hung threads.
     *
     * <p> Current crawl uri will have its fetch status set accordingly and
     * will be immediately returned to the frontier.
     *
     * <p> As noted before, this does not ensure that the thread will stop
     * running (ever). But once evoked it will not try and communicate with
     * other parts of crawler and will terminate as soon as control is
     * established.
     */
    protected void kill(){
        this.interrupt();
        synchronized(this) {
            if (currentCuri!=null) {
                currentCuri.setFetchStatus(S_PROCESSING_THREAD_KILLED);
                controller.getFrontier().finished(currentCuri);
             }
        }
    }

        /**
         * @return Current step (For debugging/reporting, give abstract step
     * where this thread is).
         */
        public Object getStep() {
                return step;
        }

    /**
     * Is this thread validly processing a URI, not paused, waiting for 
     * a URI, or interrupted?
     * @return whether thread is actively processing a URI
     */
    public boolean isActive() {
        // if alive and not waiting in/for frontier.next(), we're 'active'
        return this.isAlive() && (currentCuri != null) && !isInterrupted();
    }
    
    /**
     * Request that this thread retire (exit cleanly) at the earliest
     * opportunity.
     */
    public void retire() {
        shouldRetire = true;
    }

    /**
     * Whether this thread should cleanly retire at the earliest 
     * opportunity. 
     * 
     * @return True if should retire.
     */
    public boolean shouldRetire() {
        return shouldRetire;
    }

    //
    // Reporter implementation
    // 
    
    /**
     * Compiles and returns a report on its status.
     * @param pw Where to print.
     */
    @Override
    public void reportTo(PrintWriter pw) {
        // name is ignored for now: only one kind of report
        
        pw.print("[");
        pw.println(getName());

        // Make a local copy of the currentCuri reference in case it gets
        // nulled while we're using it.  We're doing this because
        // alternative is synchronizing and we don't want to do this --
        // it causes hang ups as controller waits on a lock for this thread,
        // something it gets easily enough on old threading model but something
        // it can wait interminably for on NPTL threading model.
        // See [ 994946 ] Pause/Terminate ignored on 2.6 kernel 1.5 JVM.
        CrawlURI c = currentCuri;
        if(c != null) {
            pw.print(" ");
            c.shortReportLineTo(pw);
            pw.print("    ");
            pw.print(c.getFetchAttempts());
            pw.print(" attempts");
            pw.println();
            pw.print("    ");
            pw.print("in processor: ");
            pw.print(currentProcessorName);
        } else {
            pw.print(" -no CrawlURI- ");
        }
        pw.println();

        long now = System.currentTimeMillis();
        long time = 0;

        pw.print("    ");
        if(lastFinishTime > lastStartTime) {
            // That means we finished something after we last started something
            // or in other words we are not working on anything.
            pw.print("WAITING for ");
            time = now - lastFinishTime;
        } else if(lastStartTime > 0) {
            // We are working on something
            pw.print("ACTIVE for ");
            time = now-lastStartTime;
        }
        pw.print(ArchiveUtils.formatMillisecondsToConventional(time));
        pw.println();

        pw.print("    ");
        pw.print("step: ");
        pw.print(step);
        pw.print(" for ");
        pw.print(ArchiveUtils.formatMillisecondsToConventional(System.currentTimeMillis()-atStepSince));
        pw.println();

        reportThread(this, pw);
        pw.print("]");
        pw.println();
        
        pw.flush();
    }

    /**
     * @param t Thread
     * @param pw PrintWriter
     */
    static public void reportThread(Thread t, PrintWriter pw) {
        ThreadMXBean tmxb = ManagementFactory.getThreadMXBean();
        ThreadInfo info = tmxb.getThreadInfo(t.getId());
        pw.print("Java Thread State: ");
        pw.println(info.getThreadState());
        pw.print("Blocked/Waiting On: ");
        if (info.getLockOwnerId() >= 0) {
            pw.print(info.getLockName());
            pw.print(" which is owned by ");
            pw.print(info.getLockOwnerName());
            pw.print("(");
            pw.print(info.getLockOwnerId());
            pw.println(")");
        } else {
            pw.println("NONE");
        }
        
        StackTraceElement[] ste = t.getStackTrace();
        for(int i=0;i<ste.length;i++) {
            pw.print("    ");
            pw.print(ste[i].toString());
            pw.println();
        }
    }

    @Override
    public Map<String, Object> shortReportMap() {
        Map<String,Object> data = new LinkedHashMap<String, Object>();
        data.put("serialNumber", serialNumber);
        CrawlURI c = currentCuri;
        if (c != null) {
            data.put("currentURI", c.toString());
            data.put("currentProcessor", currentProcessorName);
            data.put("fetchAttempts", c.getFetchAttempts());
        } else {
            data.put("currentURI", null);
        }

        long now = System.currentTimeMillis();
        long time = 0;
        if (lastFinishTime > lastStartTime) {
            data.put("status", "WAITING");
            time = now - lastFinishTime;
        } else if (lastStartTime > 0) {
            data.put("status", "ACTIVE");
            time = now - lastStartTime;
        }
        data.put("currentStatusElapsedMilliseconds", time);
        data.put("currentStatusElapsedPretty", ArchiveUtils.formatMillisecondsToConventional(time));
        data.put("step", step);
        return data;
    }

    /**
     * @param w PrintWriter to write to.
     */
    @Override
    public void shortReportLineTo(PrintWriter w)
    {
        w.print("#");
        w.print(this.serialNumber);

        // Make a local copy of the currentCuri reference in case it gets
        // nulled while we're using it.  We're doing this because
        // alternative is synchronizing and we don't want to do this --
        // it causes hang ups as controller waits on a lock for this thread,
        // something it gets easily enough on old threading model but something
        // it can wait interminably for on NPTL threading model.
        // See [ 994946 ] Pause/Terminate ignored on 2.6 kernel 1.5 JVM.
        CrawlURI c = currentCuri;
        if(c != null) {
            w.print(" ");
            w.print(currentProcessorName);
            w.print(" ");
            w.print(c.toString());
            w.print(" (");
            w.print(c.getFetchAttempts());
            w.print(") ");
        } else {
            w.print(" [no CrawlURI] ");
        }
        
        long now = System.currentTimeMillis();
        long time = 0;

        if(lastFinishTime > lastStartTime) {
            // That means we finished something after we last started something
            // or in other words we are not working on anything.
            w.print("WAITING for ");
            time = now - lastFinishTime;
        } else if(lastStartTime > 0) {
            // We are working on something
            w.print("ACTIVE for ");
            time = now-lastStartTime;
        }
        w.print(ArchiveUtils.formatMillisecondsToConventional(time));
        w.print(" at ");
        w.print(step);
        w.print(" for ");
        w.print(ArchiveUtils.formatMillisecondsToConventional(now-atStepSince));
        w.print("\n");
        w.flush();
    }

    @Override
    public String shortReportLegend() {
        return "#serialNumber processorName currentUri (fetchAttempts) threadState threadStep";
    }

    public String shortReportLine() {
        return ReportUtils.shortReportLine(this);
    }

    public void progressStatisticsLine(PrintWriter writer) {
        writer.print(getController().getStatisticsTracker()
            .getSnapshot().getProgressStatisticsLine());
        writer.print("\n");
    }

    public void progressStatisticsLegend(PrintWriter writer) {
        writer.print(getController().getStatisticsTracker()
            .progressStatisticsLegend());
        writer.print("\n");
    }
    
    public String getCurrentProcessorName() {
        return currentProcessorName;
    }
    
    
    public InetAddress resolve(String host) {
        return controller.getServerCache().getHostFor(host).getIP();
    }
}
