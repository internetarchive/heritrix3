/* $Id$
 *
 * Created on August 21st, 2006
 *
 * Copyright (C) 2006 Internet Archive.
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