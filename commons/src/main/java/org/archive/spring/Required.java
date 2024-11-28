package org.archive.spring;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Replacement for the removed Spring @Required annotation. Do not use this in new beans,
 * use constructor injection instead. This only exists to avoid breaking existing crawl profiles.
 */
@Deprecated
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Required {
}
