package org.archive.crawler.restlet;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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

    /**
     * List with invalid character simulating bad input from job.log or crawl.log
     */
    @Test
    public void testInvalidCharacters() throws Exception {
        List<String> list = new ArrayList<String>();
        StringBuilder sb = new StringBuilder();
        sb.append("TEST<>&");
        sb.append('\u0007'); // BEL - should be excluded
        sb.append('\uD7FF'); // last code point before surrogates - should be accepted
        sb.append('\uD800'); // high surrogates - should be excluded
        sb.append('\uE000'); // first after surrogates - should be accepted
        sb.append('\uFFFD'); // replacement char - should be accepted

        sb.append(Character.toChars(0x10000)); // lowest supplementary char - should be accepted
        sb.append(Character.toChars(0x10FFFF)); // highest supplementary char - should be accepted

        list.add(sb.toString());
        Map<String, Object> map = new LinkedHashMap<String, Object>();
        map.put("crawlLogTail", list);
        StringWriter w = new StringWriter();
        XmlMarshaller.marshalDocument(w, "doc", list);

        String expected = "(?s:)" +
                "^<\\?xml version=\"1\\.0\" standalone='yes'\\?>\\s*" +
                "<doc>\\s*<value>TEST&lt;&gt;&amp;&#55295;&#57344;&#65533;&#55296;&#56320;&#56319;&#57343;</value>\\s*" +
                "</doc>\\s*$";
        String xml = w.toString();
        assertTrue(xml.matches(expected), "xml matches expected RE");
    }
}
