package org.archive.util;

import java.io.Reader;
import java.util.Iterator;

import org.apache.commons.io.LineIterator;

/**
 * A LineIterator that also implements Iterable, so that it can be used with
 * the java enhanced for-each loop syntax.
 * 
 * @contributor nlevitt
 */
public class IterableLineIterator extends LineIterator 
    implements Iterable<String> {

    public IterableLineIterator(final Reader reader)
            throws IllegalArgumentException {
        super(reader);
    }

    @SuppressWarnings("unchecked")
    public Iterator<String> iterator() {
        return this;
    }
}
