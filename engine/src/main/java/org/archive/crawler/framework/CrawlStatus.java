package org.archive.crawler.framework;

public enum CrawlStatus {

    /** Inital value. May not be ready to run/incomplete. */    
    CREATED("Created"),
    
    /** Job has been successfully submitted to a CrawlJobHandler. */
    PENDING("Pending"),
    
    /** Job is being crawled. */
    RUNNING("Running"),
    
    /** Job was deleted by user, will not be displayed in UI. */
    DELETED("Deleted"),
    
    /** Job was terminted by user input while crawling. */
    ABORTED("Finished - Ended by operator"),
    
    /** Something went very wrong. */
    FINISHED_ABNORMAL("Finished - Abnormal exit from crawling"),

    /** Job finished normally having completed its crawl. */    
    FINISHED("Finished"),
    
    /** Job finished normally when the specified timelimit was hit. */
    FINISHED_TIME_LIMIT("Finished - Timelimit hit"),

    
    /**
     * Job finished normally when the specifed amount of data (MB) had been
     * downloaded
     */
    FINISHED_DATA_LIMIT("Finished - Maximum amount of data limit hit"),

    
    /**
     * Job finished normally when the specified number of documents had been
     * fetched.
     */
    FINISHED_DOCUMENT_LIMIT("Finished - Maximum number of documents limit hit"),

    FINISHED_WRITE_LIMIT("Finished - Maximum bytes written"),
    
    /**
     * Job is going to be temporarly stopped after active threads are finished.
     */
    WAITING_FOR_PAUSE("Pausing - Waiting for threads to finish"),
    

    /** Job was temporarly stopped. State is kept so it can be resumed */
    PAUSED("Paused"),


    /**
     * Job is being checkpointed.  When finished checkpointing, job is set
     * back to STATUS_PAUSED (Job must be first paused before checkpointing
     * will run).
     */
    CHECKPOINTING("Checkpointing"),
    
    
    /** Job could not be launced due to an InitializationException */
    MISCONFIGURED("Could not launch job - Fatal InitializationException"),

    
    /** Job is actually a profile */
    PROFILE("Profile"),
    

    /** Prepared. */
    PREPARING("Preparing");

    
    
    final public String desc;
    
    CrawlStatus(String desc) {
        this.desc = desc;
    }
    
    
    public String getDescription() {
        return desc;
    }
    
}
