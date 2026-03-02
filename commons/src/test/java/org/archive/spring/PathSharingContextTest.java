package org.archive.spring;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.BeanDefinitionStoreException;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class PathSharingContextTest {
    @Test
    public void testGroovyConfig() {
        testConfig("groovy", "classpath:org/archive/spring/PathSharingContextTestBeans.groovy");
    }

    @Test
    public void testXmlConfig() {
        testConfig("xml", "classpath:org/archive/spring/PathSharingContextTestBeans.cxml");
    }

    private static void testConfig(String name, String configPath) {
        try (var context = new PathSharingContext(configPath)) {
            context.validate();
            assertTrue(context.getAllErrors().isEmpty(), "should be no validation errors");
            assertEquals(configPath, context.getPrimaryConfigurationPath(), "primaryConfiguationPath should be correct");
            Bean1 bean1 = context.getBean("bean1", Bean1.class);
            Bean2 bean2 = context.getBean("bean2", Bean2.class);
            assertNotNull(bean1, "bean1 should not be null");
            assertNotNull(bean2, "bean2 should not be null");
            assertEquals(name, bean1.name, "bean1.name should be set");
            assertEquals(bean1, bean2.bean1, "bean1 should be autowired into bean2");
        }
    }

    /**
     * Test that XXE attacks with parameter entity declarations are blocked
     */
    @Test
    public void testXxeProtectionBlocksParameterEntity(@TempDir Path tempDir) throws IOException {
        String maliciousXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE beans [
                  <!ENTITY % xxe SYSTEM "file:///dev/null">
                  %xxe;
                ]>
                <beans xmlns="http://www.springframework.org/schema/beans"
                       xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                       xsi:schemaLocation="http://www.springframework.org/schema/beans http://www.springframework.org/schema/beans/spring-beans-3.0.xsd">
                    <bean id="bean1" class="org.archive.spring.PathSharingContextTest$Bean1">
                        <property name="name" value="test"/>
                    </bean>
                </beans>
                """;

        File maliciousFile = tempDir.resolve("malicious_param.cxml").toFile();
        Files.writeString(maliciousFile.toPath(), maliciousXml);

        try {
            new PathSharingContext("file:" + maliciousFile.getAbsolutePath()).close();
            fail("XXE parameter entity attack should be blocked");
        } catch (BeanDefinitionStoreException e) {
            if (!e.getCause().getMessage().contains("DOCTYPE is disallowed")) {
                fail("XXE parameter entity attack should be blocked");
            }
        }
    }

    public static class Bean1 {
        private String name;

        public void setName(String name) {
            this.name = name;
        }
    }

    public static class Bean2 {
        private Bean1 bean1;

        @Autowired
        public void setBean1(Bean1 bean1) {
            this.bean1 = bean1;
        }
    }
}