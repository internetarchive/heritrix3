package org.archive.crawler.restlet;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;

import junit.framework.TestCase;

import org.archive.crawler.framework.CrawlJob;
import org.archive.spring.PathSharingContext;

public class ScriptingConsoleTest extends TestCase {
    // barebone CrawlJob object.
    public static class TestCrawlJob extends CrawlJob {
        public TestCrawlJob() {
            super(null);
            this.ac = new PathSharingContext(new String[0]);
        }
        @Override
        protected void scanJobLog() {
        }
    }
    CrawlJob cj;
    ScriptingConsole sc;
    
    protected void setUp() throws Exception {
        super.setUp();
        cj = new TestCrawlJob();
        sc = new ScriptingConsole(cj);
    }

    public void testInitialState() {
        assertEquals("script is empty", "", sc.getScript());
        assertNull("exception is null", sc.getException());
    }
    
    public void testExecute() {
        final String script = "rawOut.println 'elk'";
        final ScriptEngineManager manager = new ScriptEngineManager();
        final ScriptEngine eng = manager.getEngineByName("groovy");
        sc.execute(eng, script);
        
        assertNull("exception is null", sc.getException());
        assertEquals("has the same script", sc.getScript(), script);
        assertEquals("linesExecuted", 1, sc.getLinesExecuted());
        assertEquals("rawOut", "elk\n", sc.getRawOutput());
        
    }
    
    public void testExecuteError() {
        final String script = "rawOut.println undef";
        final ScriptEngineManager manager = new ScriptEngineManager();
        final ScriptEngine eng = manager.getEngineByName("groovy");
        sc.execute(eng, script);
        
        assertNotNull("exception is non-null", sc.getException());
        assertEquals("rawOut", "", sc.getRawOutput());
        assertEquals("linesExecuted", 0, sc.getLinesExecuted());
        
        // extra test - it is okay to fail this test is okay because
        // ScriptingConsole is single-use now.
        sc.execute(eng, "rawOut.println 1");
        assertNull("exception is cleared", sc.getException());
    }
}
