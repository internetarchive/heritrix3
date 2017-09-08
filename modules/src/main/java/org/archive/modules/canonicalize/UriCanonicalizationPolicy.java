package org.archive.modules.canonicalize;

/**
 * URI Canonicalizatioon Policy
 * 
 * @author stack
 * @author gojomo
 */
public abstract class UriCanonicalizationPolicy {
    public abstract String canonicalize(String uri);
}
