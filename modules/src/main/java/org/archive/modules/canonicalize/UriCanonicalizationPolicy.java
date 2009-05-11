package org.archive.modules.canonicalize;

/**
 * URI Canonicalizatioon Policy
 * 
 * @contributor stack
 * @contributor gojomo
 */
public abstract class UriCanonicalizationPolicy {
    public abstract String canonicalize(String uri);
}
