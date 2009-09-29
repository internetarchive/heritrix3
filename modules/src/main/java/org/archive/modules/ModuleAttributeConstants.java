/**
 * 
 */
package org.archive.modules;


/**
 * @author pjack
 *
 */
public class ModuleAttributeConstants {

    
    final public static String A_DNS_SERVER_IP_LABEL = "dns-server-ip";
    
    /**
     * Fetch truncation codes present in {@link CrawlURI} annotations.
     * All truncation annotations have a <code>TRUNC_SUFFIX</code> suffix (TODO:
     * Make for-sure unique or redo truncation so definitive flag marked
     * against {@link CrawlURI}).
     */
    public static final String TRUNC_SUFFIX = "Trunc";
    // headerTrunc
    public static final String HEADER_TRUNC = "header" + TRUNC_SUFFIX; 
    // timeTrunc
    public static final String TIMER_TRUNC = "time" + TRUNC_SUFFIX;
    // lenTrunc
    public static final String LENGTH_TRUNC = "len" + TRUNC_SUFFIX;
    
    /** a 'source' (usu. URI) that's inherited by discovered URIs */
    public static final String A_SOURCE_TAG = "source";

    public static final String A_FETCH_BEGAN_TIME= "fetch-began-time";

    private ModuleAttributeConstants() {
    }

}
