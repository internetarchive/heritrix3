package org.archive.modules.deciderules;

import org.archive.url.URIException;
import org.archive.modules.CrawlURI;
import org.archive.net.UURIFactory;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class MatchesListRegexDecideRuleTest {

    /**
     * Not easy to test this code in older versions of junit. Basically with the timeout set to "0", this method
     * will never return.
     */
    @Test
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
        assertEquals(DecideResult.NONE, decideResult, "Expected NONE not " + decideResult);
    }

    @Test
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
        assertEquals(DecideResult.REJECT, decideResult, "Expected REJECT not " + decideResult);
    }

}