package org.archive.modules.deciderules;

import org.archive.modules.CrawlURI;


/**
 * Provides a rule that returns *false* for any URIs whose status falls within
 * the provided inclusive range. 
 * @author cmiles74
 */
public class NotMatchesStatusCodeDecideRule extends MatchesStatusCodeDecideRule {

    /**
     * @param CrawlURI
     *            The URI to be evaluated
     * @return false if {@link CrawlURI#getFetchStatus()} is within the specified
     *         inclusive range, true if it is outside the range
     */
    @Override
    protected boolean evaluate(CrawlURI uri) {
        return !super.evaluate(uri);
    }
}