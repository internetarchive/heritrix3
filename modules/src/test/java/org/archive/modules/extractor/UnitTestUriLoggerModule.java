/**
 * 
 */
package org.archive.modules.extractor;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.httpclient.URIException;
import org.archive.net.UURI;

/**
 * @author pjack
 *
 */
public class UnitTestUriLoggerModule implements UriErrorLoggerModule {
    @SuppressWarnings("unused")
    private static final long serialVersionUID = 1L;
    
    final private static Logger LOGGER = 
        Logger.getLogger(UnitTestUriLoggerModule.class.getName());

    public void logUriError(URIException e, UURI u, CharSequence l) {
        LOGGER.log(Level.INFO, u.toString(), e);
    }

    public Logger getLogger(String name) {
        return LOGGER;
    }

    
    
}
