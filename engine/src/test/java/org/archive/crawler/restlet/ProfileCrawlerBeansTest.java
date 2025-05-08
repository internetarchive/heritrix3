package org.archive.crawler.restlet;

import org.archive.spring.PathSharingContext;
import org.junit.jupiter.api.Test;
import org.springframework.validation.Errors;

import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ProfileCrawlerBeansTest {
    @Test
    public void testXmlProfile() {
        testProfile("classpath:/org/archive/crawler/restlet/profile-crawler-beans.cxml");
    }

    @Test
    public void testGroovyProfile() {
        testProfile("classpath:/org/archive/crawler/restlet/profile-crawler-beans.groovy");
    }

    private static void testProfile(String location) {
        String profile = location.substring(location.lastIndexOf('/') + 1);
        try (var context = new PathSharingContext(location)) {
            context.validate();
            HashMap<String, Errors> allErrors = context.getAllErrors();
            assertEquals(1, allErrors.size(), profile + " should have one bean with errors");
            Errors metadataErrors = allErrors.get("metadata");
            assertEquals(1, metadataErrors.getErrorCount(), profile + " Metadata bean should have one error");
            assertTrue(metadataErrors.getAllErrors().get(0).toString().contains("ENTER_AN_URL_WITH_YOUR_CONTACT_INFO"),
                    profile + " should have the operator contact info error");
        }
    }
}
