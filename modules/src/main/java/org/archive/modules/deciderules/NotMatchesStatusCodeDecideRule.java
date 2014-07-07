package org.archive.modules.deciderules;

import org.archive.modules.CrawlURI;


/**
 * Provides a rule that returns "true" for any CrawlURIs which has a fetch
 * status code that does not fall within the provided inclusive range. For
 * instance, to reject any URIs with a "client error" status code you must
 * provide the range 400 to 499.
 * 
 * @author cmiles74
 */
public class NotMatchesStatusCodeDecideRule extends MatchesStatusCodeDecideRule {

    /**
     * Sets the upper bound on the range of acceptable status codes.
     *
     * @param Integer Status code
     */
    public void setUpperBound(Integer statusCode) {

        kp.put("upperBound", statusCode);
    }

    /**
     * Returns the upper bound on the range of acceptable status codes.
     *
     * @returns Integer Status code
     */
    public Integer getUpperBound() {

        Object value = kp.get("upperBound");

        if(value != null) {

            return((Integer) value);
        }

        return(null);
    }

    /**
     * Returns "true" if the provided CrawlURI has a fetch status that does not
     * fall within this instance's specified range.
     * 
     * @param CrawlURI
     *            The URI to be evaluated
     * @return true If the CrawlURI has a fetch status outside the specified
     *         range
     */
    @Override
    protected boolean evaluate(CrawlURI uri) {

        // by default, we'll return false
        boolean value = false;

        int statusCode = uri.getFetchStatus();

        if (statusCode <= getLowerBound().intValue()
                || statusCode >= getUpperBound().intValue()) {

            value = true;
        }

        return (value);
    }
}
