package org.archive.spring;

import java.io.Writer;

public interface WriteTarget {
    Writer getWriter(); 
    Writer getWriter(boolean append); 
}
