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

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.filefilter.FileFilterUtils;
import org.archive.util.ArchiveUtils;

/**
 * Implementation for Engine.  Jobs and profiles are stored in a 
 * directory called the jobsDir.  The jobs are contained as subdirectories of
 * jobDir.  
 * 
 * @contributor pjack
 * @contributor gojomo
 */
public class Engine {
    @SuppressWarnings("unused")
    private static final long serialVersionUID = 4L;

    final public static String LOGS_DIR_NAME = "logs subdirectory";
    final public static String REPORTS_DIR_NAME = "reports subdirectory";

    final private static Logger LOGGER = 
        Logger.getLogger(Engine.class.getName()); 
        
    /** directory where job directories are expected */
    protected File jobsDir;
    /** map of job short names -> CrawlJob instances */ 
    protected HashMap<String,CrawlJob> jobConfigs = new HashMap<String,CrawlJob>();

    protected String profileCxmlPath = 
        "/org/archive/crawler/restlet/profile-crawler-beans.cxml";
    
    public Engine(File jobsDir) {
        this.jobsDir = jobsDir;
        
        try {
            org.archive.util.FileUtils.ensureWriteableDirectory(jobsDir);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        
        findJobConfigs();
        // TODO: cleanup any cruft from improperly ended jobs 
    }
    
    /**
     * Find all job configurations in the usual place -- subdirectories
     * of the jobs directory with files ending '.cxml', and from jobPathFiles
     * (previously added by user) found in the jobs directory
     */
    public void findJobConfigs() {
        // TODO: allow other places/paths to be scanned/added as well?
        
        // remove crawljobs whose directories have disappeared
        // TODO: try a more delicate cleanup; eg: if appCtx exists?
        for(String jobName: jobConfigs.keySet().toArray(new String[0])) {
            CrawlJob cj = jobConfigs.get(jobName);
            if(!cj.getJobDir().exists()) {
                jobConfigs.remove(jobName); 
            }
        }
        
        // just in case...
        if (! jobsDir.exists()) {
            LOGGER.log(Level.SEVERE,"jobsDir has disappeared: "+jobsDir.toString());
            return;
        }

        // discover any new job directories
        for (File candidateFile: jobsDir.listFiles()) {
            File jobFile = candidateFile; 
            if (candidateFile.getName().endsWith(".jobpath")) {
                // convert .jobpaths to the referenced external directory
                jobFile = getJobDirectoryFrom(candidateFile);
            }
            if (jobConfigs.containsKey(jobFile.getName())) {
                continue;
            }
            if(!addJobDirectory(jobFile)) {
                LOGGER.log(Level.WARNING,"invalid job directory: " + jobFile 
                        + " where job expected from: " + candidateFile);
            }
        }
    }

    /**
     * Return the job directory File read from the supplied ".jobpath" file,
     * or null on any error. 
     */
    protected File getJobDirectoryFrom(File jobPathFile) {
        try {
            return new File(FileUtils.readFileToString(jobPathFile).trim());
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE,"bad .jobpath: "+jobPathFile, e);
            return null; 
        }
	}

    /**
     * Adds a job directory to the Engine known jobConfigs if not extant.
     * 
     * @param dir directory to be added
     * @return true if directory successfully added, false for any failure
     */
    public boolean addJobDirectory(File dir) {
        if(dir==null) {
            return false; 
        }
        File[] candidateConfigs = dir.listFiles(new FilenameFilter() {
            public boolean accept(File dir, String name) {
                return name.endsWith(".cxml");
            }});
        if(candidateConfigs==null || candidateConfigs.length == 0) {
            // no CXML file found!
            return false; 
        }
        if(jobConfigs.containsKey(dir.getName())) {
            // same-name job already exists
            return false; 
        }
        for (File cxml : candidateConfigs) {
            try {
                CrawlJob cj = new CrawlJob(cxml);            
                if(!cj.getJobDir().getParentFile().equals(getJobsDir())) {
                    writeJobPathFile(cj);
                }
                jobConfigs.put(cj.getShortName(),cj);
                LOGGER.log(Level.INFO,"added crawl job: " + cj.getShortName());
                return true;
            } catch (IOException iae) {
                LOGGER.log(Level.SEVERE,"unable to add job directory"+dir,iae);
            } catch (IllegalArgumentException iae) {
                LOGGER.log(Level.SEVERE,"bad cxml: "+cxml,iae);
            }
        }
        // path rejected for some reason
        return false; 
    }
    
    public Map<String,CrawlJob> getJobConfigs() {
        return jobConfigs;
    }
   
    
    
    /**
     * Copy a job to a new location, possibly making a job
     * a profile or a profile a runnable job. 
     * 
     * @param orig CrawlJob representing source
     * @param destDir File location destination
     * @param asProfile true if destination should become a profile
     * @throws IOException 
     */
    public synchronized void copy(CrawlJob orig, File destDir, boolean asProfile) 
    throws IOException {
        org.archive.util.FileUtils.ensureWriteableDirectory(destDir);
        if(destDir.list().length>0) {
            throw new IOException("destination dir not empty");
        }
        File srcDir = orig.getPrimaryConfig().getParentFile();

        // FIXME: Add option for only copying history DB
        // FIXME: Don't hardcode these names
        // FIXME: (?) copy any referenced file (ConfigFile/ConfigPath),
        // even outside the job directory? 
       
        // copy all simple files except the 'job.log' and its '.lck' (if any)
        FileUtils.copyDirectory(srcDir, destDir, 
                FileFilterUtils.andFileFilter(
                        FileFilterUtils.fileFileFilter(),
                        FileFilterUtils.notFileFilter(
                                FileFilterUtils.prefixFileFilter("job.log"))));
        
        // ...and all contents of 'resources' subdir...
        File srcResources = new File(srcDir, "resources");
        if (srcResources.isDirectory()) {
            FileUtils.copyDirectory(srcResources, new File(destDir, "resources"));
        }
        
        File newPrimaryConfig = new File(destDir, orig.getPrimaryConfig().getName());
        if(asProfile) {
            if(!orig.isProfile()) {
                // rename cxml to have 'profile-' prefix
                FileUtils.moveFile(
                        newPrimaryConfig, 
                        new File(destDir, "profile-"+newPrimaryConfig.getName()));
            }
        } else {
            if(orig.isProfile()) {
                // rename cxml to remove 'profile-' prefix
                FileUtils.moveFile(
                        newPrimaryConfig, 
                        new File(destDir, newPrimaryConfig.getName().substring(8)));
            }
        }
        findJobConfigs();
    }
    
    /**
     * Copy a job to a new location, possibly making a job
     * a profile or a profile a runnable job. 
     * 
     * @param cj CrawlJob representing source
     * @param copyTo String location destination; interpreted relative to jobsDir
     * @param asProfile true if destination should become a profile
     * @throws IOException 
     */
    public void copy(CrawlJob cj, String copyTo, boolean asProfile) throws IOException {
        File dest = new File(copyTo);
        if(!dest.isAbsolute()) {
            dest = new File(jobsDir,copyTo);
        }
        copy(cj,dest,asProfile);
    }
    
    public String getHeritrixVersion(){
        return ArchiveUtils.VERSION;
    }
    
    public synchronized void deleteJob(CrawlJob job) throws IOException {
        FileUtils.deleteDirectory(job.getJobDir());
    }

    public void requestLaunch(String shortName) {
        jobConfigs.get(shortName).launch();
    }

    public CrawlJob getJob(String shortName) {
        if(!jobConfigs.containsKey(shortName)) {
            // try a rescan if not already present
            findJobConfigs();
        }
        return jobConfigs.get(shortName); 
    }

    public File getJobsDir() {
        return jobsDir;
    }
    
    public Map<String,Object> heapReportData() {
        Map<String,Object> map = new LinkedHashMap<String, Object>();
        map.put("usedBytes", Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory());
        map.put("totalBytes", Runtime.getRuntime().totalMemory());
        map.put("maxBytes", Runtime.getRuntime().maxMemory());
        return map;
    }

    public String heapReport() {
        long totalMemory = Runtime.getRuntime().totalMemory();
        long freeMemory = Runtime.getRuntime().freeMemory();
        long maxMemory = Runtime.getRuntime().maxMemory();
        StringBuilder sb = new StringBuilder(64); 
        sb
         .append((totalMemory-freeMemory)/1024)
         .append(" KiB used; ")
         .append(totalMemory/1024)
         .append(" KiB current heap; ")
         .append(maxMemory/1024)
         .append(" KiB max heap");
         return sb.toString(); 
    }

    public void shutdown() {
        // TODO stop everything
        for(CrawlJob job : jobConfigs.values()) {
            if(job.isRunning()) {
                job.terminate();
            }
        }
        waitForNoRunningJobs(0);
    }

    /**
     * Wait for all jobs to be in non-running state, or until timeout
     * (given in ms) elapses. Use '0' for no timeout (wait as long as
     * necessary.
     * 
     * @param timeout
     * @return true if timeout occurred and a job is (possibly) still running
     */
    public boolean waitForNoRunningJobs(long timeout) {
        long startTime = System.currentTimeMillis();     
        // wait for all jobs to not be running
        outer: while(true) {
            if(timeout>0 && (startTime+timeout)>System.currentTimeMillis()) {
                return true; 
            }
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                break;
            }
            for(CrawlJob job : jobConfigs.values()) {
                if(job.isRunning()) {
                    continue outer;
                }
            }
            break;
        }
        try {
            // wait an extra second for good measure
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            // ignore
        }
        return false; 
    }

    /**
     * @return InputStream resource from defined profile CXML path
     */
    protected InputStream getProfileCxmlResource() {
        return getClass().getResourceAsStream(profileCxmlPath);
    }
    
    /**
     * create a new job dir and copy profile CXML into as non-profile CXML
     * @param newJobDir new job directory
     * @throws IOException 
     */
	public boolean createNewJobWithDefaults(File newJobDir) {
        try {
            // get crawler-beans template into string
            InputStream inStream = getProfileCxmlResource();
            String defaultCxmlStr;
            defaultCxmlStr = IOUtils.toString(inStream);
            inStream.close();

            // write default crawler-beans string to new job dir
            org.archive.util.FileUtils.ensureWriteableDirectory(newJobDir);
            File newJobCxml = new File(newJobDir,"crawler-beans.cxml");
            FileUtils.writeStringToFile(newJobCxml, defaultCxmlStr);

            return true;

        } catch (IOException e) {
            LOGGER.log(Level.SEVERE,"failed to create new job: "
                    + newJobDir.getAbsolutePath());
            return false;
        }
    }

    /**
     * Writes a .jobpath file for the new CrawlJob, whose directory is
     * outside the main Engine jobs directory. 
     * 
     * @param job CrawlJob whose main directory the .jobpath should point to
     * @throws IOException for any IO error
     */
    public void writeJobPathFile(CrawlJob job) throws IOException {
        String jobpathFileName = job.getShortName()+".jobpath";
        File jobpathFile = new File(jobsDir,jobpathFileName);
        FileUtils.writeStringToFile(jobpathFile, job.getJobDir().getAbsolutePath()+"\n");
        LOGGER.log(Level.INFO, "wrote jobpath file: " + jobpathFileName);
    }
}