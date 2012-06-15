package org.archive.modules.deciderules;

import org.archive.modules.CrawlURI;

/**
 * Provides a rule that returns "true" for any URIs whose status falls within
 * the provided inclusive range. For instance, to select only URIs with a
 * "success" status code you must provide the range 200 to 299.
 * 
 * @author cmiles74
 */
public class MatchesStatusCodeDecideRule extends PredicatedDecideRule {

    /** Default lower bound */
    public final static Integer DEFAULT_LOWER_BOUND = new Integer(0);

    /** Default upper bound */
    public final static Integer DEFAULT_UPPER_BOUND = new Integer(600);

    /**
     * Creates a new MatchStatusCodeDecideRule instance. Note that
     * this will return a rule that will return "true" for all valid
     * status codes (and some invalid ones, too).
     */
    public MatchesStatusCodeDecideRule() {

        // set our default bounds
        kp.put("lowerBound", DEFAULT_LOWER_BOUND);
        kp.put("upperBound", DEFAULT_UPPER_BOUND);
    }

    /**
     * Sets the lower bound on the range of acceptable status codes.
     *
     * @param Integer Status code
     */
    public void setLowerBound(Integer statusCode) {

        kp.put("lowerBound", statusCode);
    }

    /**
     * Returns the lower bound on the range of acceptable status codes.
     *
     * @returns Integer Status code
     */
    public Integer getLowerBound() {

        Object value = kp.get("lowerBound");

        if(value != null) {

            return((Integer) value);
        }

        return(null);
    }

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
     * @param CrawlURI
     *            The URI to be evaluated
     * @return true if {@link CrawlURI#getFetchStatus()} is within the specified
     *         inclusive range
     */
    @Override
    protected boolean evaluate(CrawlURI uri) {
        return uri.getFetchStatus() >= getLowerBound().intValue()
                && uri.getFetchStatus() <= getUpperBound().intValue();
    }
}