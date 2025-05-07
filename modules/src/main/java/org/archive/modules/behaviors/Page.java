package org.archive.modules.behaviors;

import org.archive.modules.CrawlURI;

/**
 * Represents a {@link CrawlURI} loaded as web page that a @{@link Behavior} can
 * interact with.
 */
public interface Page {
    CrawlURI curi();

    <T> T eval(String script, Object... args);

    <T> T evalPromise(String script, Object... args);
}
