package org.archive.crawler.framework;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class EngineTest {
    @TempDir
    Path tempDir;

    @Test
    public void testGetProfileCxmlResource() throws IOException {
        File dummyJobsDir = new File(tempDir.toFile(),"dummyJobsDir");
        assertTrue(dummyJobsDir.mkdirs());
        assertNotNull(new Engine(dummyJobsDir).getProfileCxmlResource());
        FileUtils.deleteDirectory(dummyJobsDir); 
    }

}
