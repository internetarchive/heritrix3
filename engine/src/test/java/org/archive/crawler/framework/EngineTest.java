package org.archive.crawler.framework;

import java.io.File;
import java.io.IOException;

import org.apache.commons.io.FileUtils;
import org.archive.util.TmpDirTestCase;

public class EngineTest extends TmpDirTestCase {
    
    public void testGetProfileCxmlResource() throws IOException {
        File dummyJobsDir = new File(getTmpDir(),"dummyJobsDir"); 
        assertTrue(dummyJobsDir.mkdirs());
        assertNotNull(new Engine(dummyJobsDir).getProfileCxmlResource());
        FileUtils.deleteDirectory(dummyJobsDir); 
    }

}
