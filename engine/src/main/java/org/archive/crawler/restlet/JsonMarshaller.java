package org.archive.crawler.restlet;

import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.io.IOException;
import java.io.Writer;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Map;
import java.util.logging.Logger;

import org.apache.commons.lang3.StringUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.restlet.data.Reference;

import jakarta.xml.bind.annotation.XmlTransient;
import jakarta.xml.bind.annotation.XmlType;

import static org.archive.crawler.restlet.XmlMarshaller.marshalAsElement;
import static org.archive.crawler.restlet.XmlMarshaller.orderProperties;

/**
 * JsonMarshaller can be used to write data structures as simple json. See
 * {@link #marshalDocument(Writer, String, Object)} for more information.
 * 
 * @author ekoerner
 * @see {@link XmlMarshaller}
 */
public class JsonMarshaller {

    private static final Logger logger = Logger.getLogger(JsonMarshaller.class.getName());

    /**
     * Writes {@code content} as json to {@code writer}. Recursively descends
     * into Maps, using keys as object keys. Iterates over items in arrays and
     * Iterables, generating lists. Marshals simple object values
     * with {@link #toString()}. The enclosing Object with {@code rootKey}
     * will not be generated if {@code rootKey} is empty.
     * The result looks something like this:
     * 
     * <pre>
     * { "rootKey": { // root object is a Map
     *     "key1": simpleObjectValue1,
     *     "key2": { // /rootKey/key2 is another Map
     *       "subkey1": subvalue1,
     *       "subkey2": [ // an array or Iterable
     *          item1Value,
     *          item2Value
     *       ],
     *       "subkey3": subvalue3
     *     }
     *   }
     * }
     * </pre>
     * 
     * @param writer  output writer
     * @param rootKey (optional) wrapper root key name
     * @param content data structure to marshal
     * @throws IOException
     * @see {@link XmlMarshaller#marshalDocument(Writer, String, Object)}
     */
    public static void marshalDocument(Writer writer, String rootKey, Object content) throws IOException {
        // rethrow IOException
        // rethrow JSONException (is already a RuntimeException)

        Object wrapped = marshal(content);

        if (!StringUtils.isEmpty(rootKey)) {
            wrapped = new JSONObject().put(rootKey, wrapped);
        }

        serialize(writer, wrapped, 2);
    }

    /**
     * Serialize json {@code value} to {@code writer}.
     * 
     * @param writer       output writer
     * @param value        value to be serialized
     * @param indentFactor number of spaces for json indentation
     * @throws IOException
     * @see {@link JSONObject#writeValue(Writer, Object, int, int)}
     */
    protected static void serialize(Writer writer, Object value, int indentFactor) throws IOException {
        // JSONObject.writeValue(writer, wrapped, 2, 0);
        if (value instanceof JSONObject) {
            ((JSONObject) value).write(writer, indentFactor, 0);
        } else if (value instanceof JSONArray) {
            ((JSONArray) value).write(writer, indentFactor, 0);
        } else if (value == null) {
            writer.write("null");
        } else {
            // we should usually not arrive here, I think
            // we could alternatively just go with `value.toString()` (but this is not
            // necessarily valid JSON)
            throw new RuntimeException("Unexpected JSON marshalling result?");
        }
    }

    /**
     * Convert/marshall {@code value} to JSON objects if possible.
     * 
     * @param value object to convert
     * @return JSON object/array or {@code value} if not necessary because basic
     *         type
     * @see {@link XmlMarshaller#marshal(org.restlet.ext.xml.XmlWriter, String, Object)}
     * @see {@link JSONObject#wrap(Object)}
     */
    protected static Object marshal(Object value) {
        // see JSONObject.wrap()
        // xmlWriter.dataElement(key, stripInvalidXMLChars(value.toString()));
        if (value == null || value instanceof Byte || value instanceof Character
                || value instanceof Short || value instanceof Integer
                || value instanceof Long || value instanceof Boolean
                || value instanceof Float || value instanceof Double
                || value instanceof String || value instanceof BigInteger
                || value instanceof BigDecimal || value instanceof Enum) {
            return value;
        }

        // handle known annoying circular references
        if (value instanceof Reference) {
            return value.toString();
        }

        if (value instanceof Map<?, ?>) {
            Map<?, ?> map = (Map<?, ?>) value;
            JSONObject obj = new JSONObject();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                obj.put(entry.getKey().toString(), marshal(entry.getValue()));
            }
            return obj;
        }

        if (value instanceof Iterable<?>) {
            Iterable<?> iterable = (Iterable<?>) value;
            JSONArray array = new JSONArray();
            for (Object item : iterable) {
                array.put(marshal(item));
            }
            return array;
        }

        if (marshalAsElement(value)) {
            return marshalBean(value);
        }

        // so, here is the critical part where we want to avoid circular references as
        // this is the catch-all!
        try {
            // let's just attempt to JSONify it
            return new JSONObject(value);
        } catch (StackOverflowError e) {
            logger.fine("Infinite recursion for class=" + value.getClass());
        }

        return value.toString();
    }

    /**
     * generate nested JSON structure for a bean {@code obj}.
     * each readable JavaBeans property is mapped to an nested element named after
     * its name. Those properties annotated with {@link XmlTransient} are ignored.
     * 
     * @param obj bean
     * @see {@link XmlMarshaller#marshalBean(org.restlet.ext.xml.XmlWriter, String, Object)}
     */
    protected static JSONObject marshalBean(Object obj) {
        // we will always return this object even if conversion fails partways?
        JSONObject object = new JSONObject();

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
                        object.put(prop.getName(), marshal(propValue));
                    }
                } catch (Exception ex) {
                    // generate empty element, for now. generate comment?
                    // TODO: generate null or ""? (Or skip completely?)
                    object.put(prop.getName(), "");
                }
            }
        } catch (IntrospectionException ex) {
            // ignored, for now.
        }

        return object;
    }
}
