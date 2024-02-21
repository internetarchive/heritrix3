package org.archive.modules.extractor;

import org.apache.commons.io.IOUtils;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;

public class PDFParserTest {
    @Test
    public void test() throws IOException {
        byte[] data = IOUtils.resourceToByteArray("/org/archive/crawler/modules/extractor/PDFParserTest.pdf");
        PDFParser parser = new PDFParser(data);
        ArrayList<String> uris = parser.extractURIs();
        Assert.assertEquals(Collections.singletonList("https://example.com/link-annotation"), uris);
    }
}