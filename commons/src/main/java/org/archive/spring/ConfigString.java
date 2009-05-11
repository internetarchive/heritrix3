package org.archive.spring;

import java.io.Reader;
import java.io.StringReader;

import org.archive.io.ReadSource;

public class ConfigString implements ReadSource {
    String value;
    public String getValue() {
        return value;
    }
    public void setValue(String value) {
        this.value = value;
    }
    public Reader getReader() {
        return new StringReader(value);
    }
}
