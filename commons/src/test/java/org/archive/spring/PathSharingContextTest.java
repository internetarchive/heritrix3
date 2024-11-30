package org.archive.spring;

import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.Assert.*;

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
            assertTrue("should be no validation errors", context.getAllErrors().isEmpty());
            assertEquals("primaryConfiguationPath should be correct", configPath, context.getPrimaryConfigurationPath());
            Bean1 bean1 = context.getBean("bean1", Bean1.class);
            Bean2 bean2 = context.getBean("bean2", Bean2.class);
            assertNotNull("bean1 should not be null", bean1);
            assertNotNull("bean2 should not be null", bean2);
            assertEquals("bean1.name should be set", name, bean1.name);
            assertEquals("bean1 should be autowired into bean2", bean1, bean2.bean1);
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