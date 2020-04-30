package org.archive.modules.deciderules;

import junit.framework.TestCase;
import org.apache.commons.httpclient.URIException;
import org.archive.modules.CrawlURI;
import org.archive.net.UURIFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class MatchesListRegexDecideRuleTest extends TestCase {

    /**
     * Not easy to test this code in older versions of junit. Basically with the timeout set to "0", this method
     * will never return.
     * @throws URIException
     */
    public void testEvaluate() throws URIException {
        final String regex = "http://www\\.netarkivet\\.dk/((x+x+)+)y";
        String seed = "http://www.netarkivet.dk/xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx";
        MatchesListRegexDecideRule rule = new MatchesListRegexDecideRule();
        List<Pattern> patternList = new ArrayList<>();
        patternList.add(Pattern.compile(regex));
        rule.setRegexList(patternList);
        rule.setEnabled(true);
        rule.setListLogicalOr(true);
        rule.setDecision(DecideResult.REJECT);
        rule.setTimeoutPerRegexSeconds(2);
        final CrawlURI curi = new CrawlURI(UURIFactory.getInstance(seed));
        final DecideResult decideResult = rule.decisionFor(curi);
        assertEquals("Expected NONE not " + decideResult , DecideResult.NONE, decideResult);
    }

    public void testEvaluateInTime() throws URIException {
        final String regex = "http://www\\.netarkivet\\.dk/x+";
        String seed = "http://www.netarkivet.dk/xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx";
        MatchesListRegexDecideRule rule = new MatchesListRegexDecideRule();
        List<Pattern> patternList = new ArrayList<>();
        patternList.add(Pattern.compile(regex));
        rule.setRegexList(patternList);
        rule.setEnabled(true);
        rule.setListLogicalOr(true);
        rule.setDecision(DecideResult.REJECT);
        rule.setTimeoutPerRegexSeconds(2);
        final CrawlURI curi = new CrawlURI(UURIFactory.getInstance(seed));
        final DecideResult decideResult = rule.decisionFor(curi);
        assertEquals("Expected REJECT not " + decideResult , DecideResult.REJECT, decideResult);
    }

}