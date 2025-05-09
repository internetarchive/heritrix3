package org.archive.crawler.restlet;

import java.io.StringWriter;
import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlType;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author kenji
 *
 */
public class XmlMarshallerTest {

    /**
     * Map with nested Map
     */
    @Test
    public void testMarshalMap() throws Exception {
        Map<String, Object> map = new LinkedHashMap<String, Object>();
        map.put("c", 1);
        map.put("a", "a-value");
        Map<String, Object> nestedMap = new LinkedHashMap<String, Object>();
        nestedMap.put("x", 10);
        nestedMap.put("y", "y-value");
        map.put("m", nestedMap);
        
        StringWriter w = new StringWriter();
        XmlMarshaller.marshalDocument(w, "doc", map);
        
        String expected = "(?s:)" +
                "^<\\?xml version=\"1\\.0\" standalone='yes'\\?>\\s*" +
                "<doc>\\s*<c>1</c>\\s*<a>a-value</a>\\s*" +
                "<m>\\s*<x>10</x>\\s*<y>y-value</y>\\s*</m>\\s*" +
                "</doc>\\s*$";
        String xml = w.toString();
        System.out.println(xml);
        assertTrue(xml.matches(expected), "xml matches expected RE");
    }
    
    @XmlRootElement
    @XmlType(propOrder={"c", "a", "m"})
    public static class Model {
        public int getC() { return 1; }
        public String getA() { return "a-value"; }
        public Object getM() {
            return new NestedModel();
        }
    }
    @XmlRootElement
    @XmlType(propOrder={"x", "y"})
    public static class NestedModel {
        public int getX() { return 10; }
        public String getY() { return "y-value"; }
    }
    
    /**
     * Bean with nested Bean
     */
    @Test
    public void testMashalBean() throws Exception {
        Object bean = new Model();
        StringWriter w = new StringWriter();
        XmlMarshaller.marshalDocument(w, "doc", bean);
        
        String expected = "(?s:)" +
                "^<\\?xml version=\"1\\.0\" standalone='yes'\\?>\\s*" +
                "<doc>\\s*<c>1</c>\\s*<a>a-value</a>\\s*" +
                "<m>\\s*<x>10</x>\\s*<y>y-value</y>\\s*</m>\\s*" +
                "</doc>\\s*$";
        String xml = w.toString();
        System.out.println(xml);
        assertTrue(xml.matches(expected), "xml matches expected RE");
    }

    /**
     * Map with nested Bean
     */
    @Test
    public void testMarshalBeanInMap() throws Exception {
        Map<String, Object> map = new LinkedHashMap<String, Object>();
        map.put("c", 1);
        map.put("a", "a-value");
        Object m = new NestedModel();
        map.put("m", m);
        
        StringWriter w = new StringWriter();
        XmlMarshaller.marshalDocument(w, "doc", map);
        
        String expected = "(?s:)" +
                "^<\\?xml version=\"1\\.0\" standalone='yes'\\?>\\s*" +
                "<doc>\\s*<c>1</c>\\s*<a>a-value</a>\\s*" +
                "<m>\\s*<x>10</x>\\s*<y>y-value</y>\\s*</m>\\s*" +
                "</doc>\\s*$";
        String xml = w.toString();
        System.out.println(xml);
        assertTrue(xml.matches(expected), "xml matches expected RE");
    }
}
