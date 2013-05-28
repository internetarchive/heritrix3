/*
 *  This file is part of the Heritrix web crawler (crawler.archive.org).
 *
 *  Licensed to the Internet Archive (IA) by one or more individual 
 *  contributors. 
 *
 *  The IA licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.archive.crawler.restlet;

import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.io.IOException;
import java.io.Writer;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;
import javax.xml.bind.annotation.XmlType;

import org.apache.commons.lang.StringUtils;
import org.restlet.util.XmlWriter;
import org.xml.sax.SAXException;

/**
 * XmlMarshaller can be used to write data structures as simple xml. See
 * {@link #marshalDocument(Writer, String, Object)} for more information.
 * 
 * @contributor nlevitt
 */
public class XmlMarshaller {
    
    protected static XmlWriter getXmlWriter(Writer writer) {
        XmlWriter xmlWriter = new XmlWriter(writer);

        // https://webarchive.jira.com/browse/HER-1603?focusedCommentId=22558#action_22558
        xmlWriter.setDataFormat(true);
        xmlWriter.setIndentStep(2);

        return xmlWriter;
    }

    /**
     * Writes {@code content} as xml to {@code writer}. Recursively descends
     * into Maps, using keys as tag names. Iterates over items in arrays and
     * Iterables, using "value" as the tag name. Marshals simple object values
     * with {@link #toString()}. The result looks something like this:
     * 
     * <pre>
     * {@literal
     * <rootTag> <!-- root object is a Map -->
     *   <key1>simpleObjectValue1</key1>
     *   <key2>  <!-- /rootTag/key2 is another Map -->
     *     <subkey1>subvalue1</subkey1>
     *     <subkey2> <!-- an array or Iterable-->
     *       <value>item1Value</value>
     *       <value>item2Value</value>
     *     </subkey2>
     *     <subkey3>subvalue3</subkey3>
     *   </key2>
     * </rootTag>
     * }
     * </pre>
     * 
     * @param writer
     *            output writer
     * @param rootTag
     *            xml document root tag name
     * @param content
     *            data structure to marshal
     * @throws IOException 
     */
    public static void marshalDocument(Writer writer, String rootTag,
            Object content) throws IOException {
        XmlWriter xmlWriter = getXmlWriter(writer); 
        try {
            xmlWriter.startDocument();
            marshal(xmlWriter, rootTag, content);
            xmlWriter.endDocument(); // calls flush()
        } catch (SAXException e) {
            if (e.getException() instanceof IOException) {
                // e.g. broken tcp connection
                throw (IOException) e.getException();  
            } else {
                throw new RuntimeException(e);
            }
        }
    }
    /**
     * sort PropertyDescriptors according to propOrder.
     * properties listed in propOrder come first, in the order they are listed, and
     * then come remaining unlisted properties, in arbitrary order (likely alphabetical).
     * this semantics is rather relaxed compared to original JAXB semantics, where props
     * and propOrder must be the same set (except for those marked XmlTransient).
     * @param props PropertyDescriptor array to be sorted in-place.
     * @param propOrder list of property names in order of desired appearance. 
     */
    protected static void orderProperties(PropertyDescriptor[] props, final String[] propOrder) {
        if (propOrder == null || propOrder.length == 0) return;
        final Map<String, Integer> order = new HashMap<String, Integer>();
        for (int i = 0; i < propOrder.length; i++) {
            order.put(propOrder[i], i);
        }
        final Integer LAST = Integer.valueOf(propOrder.length);
        Arrays.sort(props, new Comparator<PropertyDescriptor>() {
            @Override
            public int compare(PropertyDescriptor o1, PropertyDescriptor o2) {
                Integer c1 = order.get(o1.getName());
                Integer c2 = order.get(o2.getName());
                return (c1 != null ? c1 : LAST).compareTo(c2 != null ? c2 : LAST);
            } 
        });
    }
    /**
     * test if {@code obj} has {@link XmlRootElement} annotation.
     * to avoid unexpected side effects, objects are mapped to nested XML structure
     * only when its class has {@link XmlRootElement} annotation.
     * note semantics is slightly different from JAXB - just borrowing XmlRootElement
     * as substitute of XmlElement, because Map entry cannot be annotated.
     * @param obj object to test
     * @return true if obj's class has XmlRootElement annotation.
     */
    protected static boolean marshalAsElement(Object obj) {
    	XmlRootElement ann = obj.getClass().getAnnotation(XmlRootElement.class);
    	return ann != null;
    }
    /**
     * generate nested XML structure for a bean {@code obj}. enclosing element
     * will not be generated if {@code key} is empty. each readable JavaBeans property
     * is mapped to an nested element named after its name. Those properties
     * annotated with {@link XmlTransient} are ignored.
     * @param xmlWriter XmlWriter
     * @param key name of enclosing element
     * @param obj bean
     * @throws SAXException
     */
    protected static void marshalBean(XmlWriter xmlWriter, String key, Object obj) throws SAXException {
        if (!StringUtils.isEmpty(key))
            xmlWriter.startElement(key);
        try {
            BeanInfo beanInfo = Introspector.getBeanInfo(obj.getClass(), Object.class);
            PropertyDescriptor[] props = beanInfo.getPropertyDescriptors();
            XmlType xmlType = obj.getClass().getAnnotation(XmlType.class);
            if (xmlType != null) {
                String[] propOrder = xmlType.propOrder();
                if (propOrder != null) {
                    // TODO: should cache this sorted version?
                    orderProperties(props, propOrder);
                }
            }
            for (PropertyDescriptor prop : props) {
                Method m = prop.getReadMethod();
                if (m == null || m.getAnnotation(XmlTransient.class) != null)
                    continue;
                try {
                    Object propValue = m.invoke(obj);
                    if (propValue != null && !"".equals(propValue)) {
                        marshal(xmlWriter, prop.getName(), propValue);
                    }
                } catch (Exception ex) {
                    // generate empty element, for now. generate comment?
                    xmlWriter.emptyElement(prop.getName());
                }
            }
        } catch (IntrospectionException ex) {
            // ignored, for now.
        }
        if (!StringUtils.isEmpty(key))
            xmlWriter.endElement(key);
    }

    protected static void marshal(XmlWriter xmlWriter, String key, Object value) throws SAXException {
        if (value == null) {
            xmlWriter.emptyElement(key);
        } else if (value instanceof Map<?,?>) {
            marshal(xmlWriter, key, (Map<?,?>) value);
        } else if (value instanceof Iterable<?>) {
            marshal(xmlWriter, key, (Iterable<?>) value);
        } else if (marshalAsElement(value)) {
            marshalBean(xmlWriter, key, value);
        } else {
            xmlWriter.dataElement(key, value.toString());
        }
    }

    protected static void marshal(XmlWriter xmlWriter, String key, Map<?,?> map) throws SAXException {
        xmlWriter.startElement(key);
        for (Map.Entry<?,?> entry: map.entrySet()) {
            marshal(xmlWriter, entry.getKey().toString(), entry.getValue());
        }
        xmlWriter.endElement(key);
    }

    protected static void marshal(XmlWriter xmlWriter, String key, Iterable<?> iterable) throws SAXException {
        xmlWriter.startElement(key);
        for (Object item: iterable) {
            marshal(xmlWriter, item);
        }
        xmlWriter.endElement(key);
    }

    // something we have no name for goes in a <value/> tag
    protected static void marshal(XmlWriter xmlWriter, Object item) throws SAXException {
        if (item instanceof Map.Entry<?, ?>) {
            // if it happens to have a name, use it
            Map.Entry<?, ?> entry = (Map.Entry<?, ?>) item;
            marshal(xmlWriter, entry.getKey().toString(), entry.getValue());
        } else {
            marshal(xmlWriter, "value", item);
        }
    }
}
