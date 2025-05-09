package org.archive.crawler.restlet;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;

import org.archive.crawler.framework.CrawlJob;
import org.archive.spring.PathSharingContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ScriptingConsoleTest {
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

    @BeforeEach
    protected void setUp() throws Exception {
        cj = new TestCrawlJob();
        sc = new ScriptingConsole(cj);
    }

    @Test
    public void testInitialState() {
        assertEquals("", sc.getScript());
        assertNull(sc.getException());
    }

    @Test
    public void testExecute() {
        final String script = "rawOut.println 'elk'";
        final ScriptEngineManager manager = new ScriptEngineManager();
        final ScriptEngine eng = manager.getEngineByName("groovy");
        sc.execute(eng, script);
        
        assertNull(sc.getException());
        assertEquals(script, sc.getScript());
        assertEquals(1, sc.getLinesExecuted());
        assertEquals("elk\n", sc.getRawOutput());
        
    }

    @Test
    public void testExecuteError() {
        final String script = "rawOut.println undef";
        final ScriptEngineManager manager = new ScriptEngineManager();
        final ScriptEngine eng = manager.getEngineByName("groovy");
        sc.execute(eng, script);
        
        assertNotNull(sc.getException());
        assertEquals("", sc.getRawOutput());
        assertEquals( 0, sc.getLinesExecuted());
        
        // extra test - it is okay to fail this test is okay because
        // ScriptingConsole is single-use now.
        sc.execute(eng, "rawOut.println 1");
        assertNull(sc.getException(), "exception is cleared");
    }
}
