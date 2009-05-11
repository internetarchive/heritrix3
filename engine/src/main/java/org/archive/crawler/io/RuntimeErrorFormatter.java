/* RuntimeErrorFormatter
 * 
 * Created on Jul 7, 2003
 * 
 * $Id$
 * 
 * Copyright (C) 2003 Internet Archive.
 *
 * This file is part of the Heritrix web crawler (crawler.archive.org).
 *
 * Heritrix is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser Public License as published by
 * the Free Software Foundation; either version 2.1 of the License, or
 * any later version.
 *
 * Heritrix is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser Public License for more details.
 *
 * You should have received a copy of the GNU Lesser Public License
 * along with Heritrix; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */
package org.archive.crawler.io;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.logging.LogRecord;

import org.archive.crawler.datamodel.CoreAttributeConstants;
import org.archive.crawler.datamodel.CrawlURI;

/**
 * Runtime exception log formatter.
 *
 * Used to format unexpected runtime exceptions such as 
 * OOMEs.
 * 
 * @author gojomo
 */
public class RuntimeErrorFormatter extends UriProcessingFormatter
implements CoreAttributeConstants {
    public String format(LogRecord lr) {
        Object [] parameters = lr.getParameters();
        String stackTrace = "None retrieved";
        if (parameters != null) {
            // CrawlURI is always first parameter.
            CrawlURI curi = (CrawlURI)parameters[0];
            if (curi != null) {
                Throwable t = (Throwable)curi.getData().get(A_RUNTIME_EXCEPTION);
                assert t != null : "Null throwable";
                StringWriter sw = new StringWriter();
                if (t == null) {
                    sw.write("No exception to report.");
                } else {
                    t.printStackTrace(new PrintWriter(sw));
                }
                stackTrace = sw.toString();
            }
        }
        return super.format(lr) + " " + stackTrace;
    }
}
