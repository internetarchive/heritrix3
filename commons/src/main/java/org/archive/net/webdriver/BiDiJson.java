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

package org.archive.net.webdriver;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONPropertyName;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Utility methods for mapping Webdriver BiDi JSON to Java records.
 */
class BiDiJson {
    /**
     * Maps Java records to a BiDi JSON tree.
     */
    static Object toJson(Object value) {
        if (value == null) return null;
        if (value instanceof String) return value;
        if (value instanceof Number) return value;
        if (value instanceof Boolean) return value;
        if (value instanceof JSONObject) return value;
        if (value instanceof Identifier identifier) return identifier.id();
        if (value instanceof Enum<?> enumValue) return enumValue.name();
        if (value instanceof Map<?, ?>) {
            var map = new JSONObject();
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                map.put(entry.getKey().toString(), toJson(entry.getValue()));
            }
            return map;
        }
        if (value instanceof Collection) {
            var list = new JSONArray();
            for (Object item : (Collection<?>) value) {
                list.put(toJson(item));
            }
            return list;
        }
        if (value.getClass().isRecord()) {
            var object = new JSONObject();
            TypeName typeName = value.getClass().getAnnotation(TypeName.class);
            if (typeName != null) object.put("type", typeName.value());
            for (RecordComponent component : value.getClass().getRecordComponents()) {
                try {
                    Object item = toJson(component.getAccessor().invoke(value));
                    if (item != null) {
                        String name = component.getName();
                        var annotation = component.getDeclaredAnnotation(PropertyName.class);
                        if (annotation != null) name = annotation.value();
                        object.put(name, item);
                    }
                } catch (IllegalAccessException | InvocationTargetException e) {
                    throw new RuntimeException(e);
                }
            }
            return object;
        }
        if (value instanceof byte[] bytes) {
            var object = new JSONObject();
            try {
                object.put("type", "string");
                object.put("value", StandardCharsets.UTF_8.newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT)
                        .decode(ByteBuffer.wrap(bytes)).toString());
            } catch (CharacterCodingException e) {
                object.put("type", "base64");
                object.put("value", Base64.getEncoder().encodeToString(bytes));
            }
            return object;
        }
        throw new UnsupportedOperationException("Unsupported type: " + value.getClass());
    }

    /**
     * Maps BiDi JSON tree to Java records.
     */
    @SuppressWarnings("unchecked")
    static <T> T fromJson(Object value, Class<T> type) {
        try {
            if (value == JSONObject.NULL) {
                return null;
            }
            if (value == null || type.isPrimitive() || type.isAssignableFrom(value.getClass())) {
                return (T) value;
            }
            if (type == Long.class && value instanceof Number number) {
                return (T) (Long) number.longValue();
            }
            if (type == byte[].class && value instanceof JSONObject jsonObject) {
                switch (jsonObject.getString("type")) {
                    case "base64" -> {
                        return (T) Base64.getDecoder().decode(jsonObject.getString("value"));
                    }
                    case "string" -> {
                        return (T) jsonObject.getString("value").getBytes(StandardCharsets.UTF_8);
                    }
                }
            }
            if (Identifier.class.isAssignableFrom(type) && value instanceof String string) {
                return type.getDeclaredConstructor(String.class).newInstance(string);
            }
            if (type.isAssignableFrom(List.class) && value instanceof JSONArray array) {
                return (T) array.toList();
            }
            if (type.isEnum() && value instanceof String string) {
                return (T) Enum.valueOf((Class<Enum>) type, string);
            }
            if (type.isRecord()) {
                if (value instanceof JSONObject jsonObject) {
                    RecordComponent[] components = type.getRecordComponents();
                    Class<?>[] componentTypes = new Class<?>[components.length];
                    Object[] values = new Object[components.length];
                    for (int i = 0; i < components.length; i++) {
                        componentTypes[i] = components[i].getType();
                        var componentValue = jsonObject.opt(components[i].getName());
                        if (componentValue != null && componentValue != JSONObject.NULL &&
                                components[i].getGenericType() instanceof ParameterizedType parameterizedType) {
                            if (parameterizedType.getRawType() == List.class && componentValue instanceof JSONArray array) {
                                Class<?> listType = (Class<?>) parameterizedType.getActualTypeArguments()[0];
                                var list = new ArrayList<>();
                                for (Object item : array) {
                                    list.add(fromJson(item, listType));
                                }
                                values[i] = list;
                            } else {
                                throw new UnsupportedOperationException("Unsupported type: " + parameterizedType + " for " + components[i] + " in " + type + " (value '" + jsonObject + "')");
                            }
                        } else {
                            values[i] = fromJson(componentValue, components[i].getType());
                        }
                    }
                    return (T) type.getDeclaredConstructor(componentTypes).newInstance(values);
                } else if (value instanceof String string) {
                    return type.getConstructor(String.class).newInstance(string);
                } else {
                    throw new UnsupportedOperationException("Unsupported type: " + type);
                }
            }
            if (type.isInterface() && type.isSealed() && value instanceof JSONObject jsonObject) {
                String typeName = jsonObject.getString("type");
                if (typeName == null) throw new UnsupportedOperationException("Missing 'type' field in " + jsonObject);
                return (T) fromJson(jsonObject, getSubclassByTypeName(type, typeName));
            }
            throw new UnsupportedOperationException("Unsupported type: " + type + " (value '" + value + "')");
        } catch (NoSuchMethodException | InstantiationException | IllegalAccessException |
                 InvocationTargetException | IllegalArgumentException e) {
            throw new RuntimeException("Couldn't map to " + type + ": " + e.getMessage(), e);
        }
    }

    private static Class<?> getSubclassByTypeName(Class<?> type, String typeName) {
        for (Class<?> subclass : type.getPermittedSubclasses()) {
            TypeName annotation = subclass.getDeclaredAnnotation(TypeName.class);
            if (annotation != null && annotation.value().equals(typeName)) return subclass;
        }
        throw new UnsupportedOperationException("Unsupported 'type' for " + type + ": " + typeName);
    }

    /**
     * Marker for identifier records which are represented as a bare JSON String.
     */
    interface Identifier {
        String id();
    }

    /**
     * The value of the 'type' field for a BiDi object that can have multiple types.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    @interface TypeName {
        String value();
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.RECORD_COMPONENT)
    @interface PropertyName {
        String value();
    }
}
