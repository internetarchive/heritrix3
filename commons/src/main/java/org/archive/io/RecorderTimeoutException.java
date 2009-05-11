/* Copyright (C) 2003 Internet Archive.
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
 *
 * RecorderTimeoutException.java
 * Created on Oct 22, 2003
 *
 * $Header$
 */
package org.archive.io;

/**
 * Indicates a timeout thrown by the RecordingInputStream.
 *
 * @author Gordon Mohr
 */
public class RecorderTimeoutException extends RecorderIOException {

    private static final long serialVersionUID = 7433214063765078269L;

    public RecorderTimeoutException() {
        super();
    }

    public RecorderTimeoutException(String msg) {
        super(msg);
    }
}
