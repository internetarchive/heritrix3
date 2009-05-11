package org.archive.modules.fetcher;

public class FetchErrors {


    /**
     * Fetch truncation codes present in ProcessorURI annotations.
     * All truncation annotations have a <code>TRUNC_SUFFIX</code> 
     * suffix (TODO: Make for-sure unique or redo truncation so 
     * definitive flag marked against {@link CrawlURI}).
     */
    public static final String TRUNC_SUFFIX = "Trunc";
    // headerTrunc
    public static final String HEADER_TRUNC = "header" + TRUNC_SUFFIX; 
    // timeTrunc
    public static final String TIMER_TRUNC = "time" + TRUNC_SUFFIX;
    // lenTrunc
    public static final String LENGTH_TRUNC = "len" + TRUNC_SUFFIX;

}
