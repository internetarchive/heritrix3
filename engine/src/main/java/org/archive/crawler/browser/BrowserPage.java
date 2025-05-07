package org.archive.crawler.browser;

import org.archive.modules.CrawlURI;
import org.archive.modules.behaviors.Page;
import org.archive.net.webdriver.WebDriverBiDi;
import org.archive.net.webdriver.BrowsingContext;
import org.archive.net.webdriver.Script;
import org.archive.util.IdleBarrier;

import java.util.List;
import java.util.stream.Stream;

/**
 * A CrawlURI that's currently loaded as a page in a browser.
 */
public record BrowserPage(CrawlURI curi,
                          IdleBarrier networkActivity,
                          WebDriverBiDi webdriver,
                          BrowsingContext.Context context) implements Page {

    /**
     * Evaluates JavaScript and returns the result as simple Java objects (numbers, strings, maps, lists).
     */
    public <T> T eval(String script, Object... args) {
        return callFunction(script, false, args);
    }

    @SuppressWarnings("unchecked")
    private <T> T callFunction(String script, boolean awaitPromise, Object... args) {
        List<Script.LocalValue> argsLocal = null;
        if (args != null && args.length > 0) {
            argsLocal = Stream.of(args).map(Script.LocalValue::from).toList();
        }
        var result = webdriver().script().callFunction(script, scriptTarget(), awaitPromise, argsLocal);
        if (result instanceof Script.EvaluateResultSuccess success) {
            return (T) success.result().javaValue();
        } else if (result instanceof Script.EvaluateResultException failure) {
            throw new RuntimeException(failure.exceptionDetails().text());
        } else {
            throw new RuntimeException("Unexpected result from script evaluation: " + result);
        }
    }

    private Script.ContextTarget scriptTarget() {
        return new Script.ContextTarget(context(), "heritrix");
    }

    @Override
    public <T> T evalPromise(String script, Object... args) {
        return callFunction(script, true, args);
    }
}
