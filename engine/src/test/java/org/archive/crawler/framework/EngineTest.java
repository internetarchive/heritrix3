package org.archive.crawler.framework;

import org.archive.util.TmpDirTestCase;

public class EngineTest extends TmpDirTestCase {
    
    public void testGetProfileCxmlResource() {
        assertNotNull(new Engine(getTmpDir()).getProfileCxmlResource());
    }

}
