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

import java.io.Writer;
import java.util.Map;

import org.restlet.util.XmlWriter;
import org.xml.sax.SAXException;

/**
 * @contributor nlevitt
 */
public class XmlMarshaller {
    protected XmlWriter xmlWriter;
    
    public XmlMarshaller(Writer writer) throws SAXException {
        xmlWriter = new XmlWriter(writer);
        // this looks ok, but makes the response much bigger - pipe to 
        // "xmllint --format -" instead
        // xmlWriter.setDataFormat(true);
        // xmlWriter.setIndentStep(2);
    }

    public void marshalDocument(String rootTag, Object content) throws SAXException {
        xmlWriter.startDocument();
        marshal(rootTag, content);
        xmlWriter.endDocument(); // calls flush()
    }

    protected void marshal(String key, Object value) throws SAXException {
        if (value == null) {
            xmlWriter.emptyElement(key);
        } else if (value instanceof Map<?,?>) {
            marshal(key, (Map<?,?>) value);
        } else if (value instanceof Iterable<?>) {
            marshal(key, (Iterable<?>) value);
        } else {
            xmlWriter.dataElement(key, value.toString());
        }
    }

    protected void marshal(String key, Map<?,?> map) throws SAXException {
        xmlWriter.startElement(key);
        for (Map.Entry<?,?> entry: map.entrySet()) {
            marshal(entry.getKey().toString(), entry.getValue());
        }
        xmlWriter.endElement(key);
    }

    protected void marshal(String key, Iterable<?> iterable) throws SAXException {
        xmlWriter.startElement(key);
        for (Object item: iterable) {
            marshal(item);
        }
        xmlWriter.endElement(key);
    }

    // something we have no name for goes in a <value/> tag
    protected void marshal(Object item) throws SAXException {
        if (item instanceof Map.Entry<?, ?>) {
            // if it happens to have a name, use it
            Map.Entry<?, ?> entry = (Map.Entry<?, ?>) item;
            marshal(entry.getKey().toString(), entry.getValue());
        } else {
            marshal("value", item);
        }
    }
}
