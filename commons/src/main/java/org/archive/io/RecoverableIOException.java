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
package org.archive.io;

import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;

/**
 * A decorator on IOException for IOEs that are likely not fatal or at least
 * merit retry.
 * @author stack
 * @version $Date$, $Revision$
 */
public class RecoverableIOException extends IOException {
    private static final long serialVersionUID = 6194776587381865451L;
    private final IOException decoratedIOException;

    public RecoverableIOException(final String message) {
        this(new IOException(message));
    }

    public RecoverableIOException(final IOException ioe) {
        super();
        this.decoratedIOException = ioe;
    }

    public Throwable getCause() {
        return this.decoratedIOException.getCause();
    }

    public String getLocalizedMessage() {
        return this.decoratedIOException.getLocalizedMessage();
    }

    public String getMessage() {
        return this.decoratedIOException.getMessage();
    }

    public StackTraceElement[] getStackTrace() {
        return this.decoratedIOException.getStackTrace();
    }

    public synchronized Throwable initCause(Throwable cause) {
        return this.decoratedIOException.initCause(cause);
    }

    public void printStackTrace() {
        this.decoratedIOException.printStackTrace();
    }

    public void printStackTrace(PrintStream s) {
        this.decoratedIOException.printStackTrace(s);
    }

    public void printStackTrace(PrintWriter s) {
        this.decoratedIOException.printStackTrace(s);
    }

    public void setStackTrace(StackTraceElement[] stackTrace) {
        this.decoratedIOException.setStackTrace(stackTrace);
    }

    public String toString() {
        return this.decoratedIOException.toString();
    }
}