package org.archive.crawler.monitor;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.archive.crawler.event.StatSnapshotEvent;
import org.archive.crawler.framework.CrawlController;
import org.archive.spring.ConfigPath;
import org.archive.spring.ConfigPathConfigurer;
import org.archive.util.ArchiveUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;

/**
 * Monitors the available space on the paths configured. If the available space
 * drops below a specified threshold a crawl pause is requested.
 * <p>
 * Monitoring is done via the <code>java.io.File.getUsableSpace()</code> method.
 * This method will sometimes fail on network attached storage, returning 0
 * bytes available even if that is not actually the case. 
 * <p>
 * Paths that do not resolve to actual filesystem folders or files will not be
 * evaluated (i.e. if <code>java.io.File.exists()</code> returns <code>false</code>
 * no further processing is carried out on that File). 
 * <p>
 * Paths are checked available space whenever a {@link StatSnapshotEvent} occurs. 
 * 
 * @contributor Kristinn Sigur&eth;sson
 */
public class DiskSpaceMonitor implements ApplicationListener<ApplicationEvent> {
    private static final Logger logger = Logger.getLogger(DiskSpaceMonitor.class.getName());

    protected List<String> monitorPaths = new ArrayList<String>();
    protected long pauseThresholdMiB = 500;
    protected CrawlController controller;
    protected ConfigPathConfigurer configPathConfigurer;
    protected boolean monitorConfigPaths = true;

    /**
     * @param monitorPaths List of filesystem paths that should be monitored for available space.
     */
    public void setMonitorPaths(List<String> monitorPaths) {
        this.monitorPaths = monitorPaths;
    }
    public List<String> getMonitorPaths() {
        return this.monitorPaths;
    }

    /**
     * Set the minimum amount of space that must be available on all monitored paths.
     * If the amount falls below this pause threshold on any path the crawl will be paused.
     *  
     * @param pauseThresholdMiB The desired pause threshold value. 
     *                          Specified in megabytes (MiB).
     */
    public void setPauseThresholdMiB(long pauseThresholdMiB) {
        this.pauseThresholdMiB = pauseThresholdMiB;
    }
    public long getPauseThresholdMiB() {
        return this.pauseThresholdMiB;
    }
    
    /**
     * If enabled, all the paths returned by {@link ConfigPathConfigurer#getAllConfigPaths()}
     * will be monitored in addition to any paths explicitly specified via
     * {@link #setMonitorPaths(List)}.
     * <p>
     * <code>true</code> by default.
     * <p>
     * <em>Note:</em> This is not guaranteed to contain all paths that Heritrix writes to.
     * It is the responsibility of modules that write to disk to register their activity
     * with the {@link ConfigPathConfigurer} and some may not do so.
     * 
     * @param monitorConfigPaths If config paths should be monitored for usable space.
     */
    public void setMonitorConfigPaths(boolean monitorConfigPaths){
        this.monitorConfigPaths = monitorConfigPaths;
    }
    public boolean getMonitorConfigPaths(){
        return this.monitorConfigPaths;
    }

    /** Autowire access to CrawlController **/
    @Autowired
    public void setCrawlController(CrawlController controller) {
        this.controller = controller;
    }
    public CrawlController getCrawlController() {
        return this.controller;
    }
    
    /** Autowire access to ConfigPathConfigurer **/
    @Autowired
    public void setConfigPathConfigurer(ConfigPathConfigurer configPathConfigurer) {
        this.configPathConfigurer = configPathConfigurer;
    }
    public ConfigPathConfigurer getConfigPathConfigurer() {
        return this.configPathConfigurer;
    }

    /**
     * Checks available space on {@link StatSnapshotEvent}s.
     */
    @Override
    public void onApplicationEvent(ApplicationEvent event) {
        if (event instanceof StatSnapshotEvent) {
            // Check available space every time the statistics tracker
            // updates its sample, by default every 20 sec.
            for (String path : getMonitorPaths()) {
                checkAvailableSpace(new File(path));
            }
            if (monitorConfigPaths) {
                for(ConfigPath path : configPathConfigurer.getAllConfigPaths().values()) {
                    checkAvailableSpace(path.getFile());
                }
            }
        }
    }

    /**
     * Probe via File.getUsableSpace to see if monitored paths have fallen below
     * the pause threshold. If so, request a crawl pause.
     * 
     * @path The filesystem path to check for usable space
     */
    protected void checkAvailableSpace(File path) {
        if (!path.exists()) {
            // Paths that can not be resolved will not report accurate
            // available space. Log and ignore.
            logger.fine("Ignoring non-existent path " + path.getAbsolutePath());
            return;
        }
        long availBytes = path.getUsableSpace();
        long thresholdBytes = getPauseThresholdMiB() * 1024 * 1024;

        if (availBytes < thresholdBytes && controller.isActive()) {
            // Enact pause
            controller.requestCrawlPause();
            
            // Log issue
            String errorMsg = "Low Disk Pause - %d bytes (%s) available on %s, "
                    + "this is below the minimum threshold of %d bytes (%s)";
            logger.log(Level.SEVERE, String.format(errorMsg, availBytes,
                    ArchiveUtils.formatBytesForDisplay(availBytes),
                    path.getAbsolutePath(), thresholdBytes, 
                    ArchiveUtils.formatBytesForDisplay(thresholdBytes)));
        }
    }

}
