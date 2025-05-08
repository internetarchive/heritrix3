package org.archive.spring;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

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