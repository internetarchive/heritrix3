package org.archive.modules.behaviors;

import org.apache.commons.httpclient.URIException;
import org.archive.modules.CrawlURI;
import org.archive.modules.extractor.Hop;
import org.archive.modules.extractor.LinkContext;
import org.archive.modules.extractor.UriErrorLoggerModule;
import org.archive.net.UURI;
import org.archive.net.UURIFactory;

import java.util.List;

public class ExtractLinksBehavior implements Behavior {
    private final UriErrorLoggerModule loggerModule;

    public ExtractLinksBehavior(UriErrorLoggerModule loggerModule) {
        this.loggerModule = loggerModule;
    }

    public void run(Page page) {
        List<String> urls = page.eval(/* language=JavaScript */ """
                const links = new Set();
                for (const el of document.querySelectorAll('a[href]')) {
                    let href = el.href;
                    if (href instanceof SVGAnimatedString) {
                        href = new URL(href.baseVal, el.ownerDocument.location.href).toString();
                    }
                    if (href.startsWith('http://') || href.startsWith('https://')) {
                        links.add(href.replace(/#.*$/, ''));
                    }
                }
                return Array.from(links);
                """);
        for (var url : urls) {
            try {
                UURI dest = UURIFactory.getInstance(page.curi().getUURI(), url);
                CrawlURI link = page.curi().createCrawlURI(dest, LinkContext.NAVLINK_MISC, Hop.NAVLINK);
                page.curi().getOutLinks().add(link);
            } catch (URIException e) {
                loggerModule.logUriError(e, page.curi().getUURI(), url);
            }
        }
    }
}
