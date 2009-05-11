package org.archive.crawler.selftest;

import java.io.File;

import org.archive.crawler.frontier.precedence.PrecedenceLoader;


/**
 * Tests that precedence values for URIs can be imported from an offline 
 * analysis.  This test crawls the same directory structure as 
 * {@link PrecedenceSelfTest1} and expects the URIs to be crawled in the same
 * order.  However, the result is achieved using a 
 * {@link org.archive.crawler.frontier.precedence.PreloadedUriPrecedencePolicy}
 * to load per-URI precedence information from an external file.
 * 
 * <p>Such a file could be generated from PageRank analysis of a previously
 * completed crawl; see {@link http://webteam.archive.org/confluence/display/Heritrix/Offline+PageRank+Analysis+Notes}.
 * (For this minimal functional test, the PreloadedUriPrecedencePolicy input
 * file was simply hand-generated.)
 * 
 * @author pjack
 */
public class Precedence2SelfTest extends Precedence1SelfTest {


    @Override
    protected String changeGlobalConfig(String config) {
        // add an autowired uriPrecedencePolicy with preloaded values
        String uriPrecedencePolicy = 
            " <bean id='uriPrecedencePolicy' class='org.archive.crawler.frontier.precedence.PreloadedUriPrecedencePolicy'>\n" +
            "  <property name='basePrecedence' value='5'/>\n" +
            " </bean>";
        config = config.replace("<!--@@BEANS_MOREBEANS@@-->", uriPrecedencePolicy);
        // suppress superclass insertion of inner bean policy
        config = config.replace("<!--@@FRONTIER_PROPERTIES@@-->", "");
        return super.changeGlobalConfig(config);
    }

    @Override
    protected void configureHeritrix() throws Exception {
        File src = new File(getJobDir(), "rank.txt");
        File dest = new File(getJobDir(), "state");
        String[] args = new String[] { 
                src.getAbsolutePath(), 
                dest.getAbsolutePath() 
        };

        PrecedenceLoader.main(args);
    }

}
