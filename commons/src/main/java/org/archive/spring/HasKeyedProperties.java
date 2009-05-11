package org.archive.spring;

/**
 * Interface indicating an object has an internal map of properties,
 * and thus at least partially amenable to sheet-based contextual 
 * overriding of properties. 
 * 
 */
public interface HasKeyedProperties {
    public KeyedProperties getKeyedProperties();
}
