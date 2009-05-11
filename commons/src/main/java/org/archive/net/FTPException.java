/* FTPException.java
 *
 * $Id$
 *
 * Created on Jun 5, 2003
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
package org.archive.net;

import java.io.IOException;

/**
 * Indicates that a FTP operation failed due to a protocol violation.
 * For instance, if authentication fails.
 * 
 * @author pjack
 */
public class FTPException extends IOException {
    private static final long serialVersionUID = 1L;
    
    /**
     * The reply code from the FTP server.
     */
    private int code;
    
    /**
     * Constructs a new <code>FTPException</code>.
     * 
     * @param code  the error code from the FTP server
     */
    public FTPException(int code) {
        super("FTP error code: " + code);
        this.code = code;
    }


    /**
     * Returns the error code from the FTP server.
     * 
     * @return  the error code from the FTP server
     */
    public int getReplyCode() {
        return code;
    }
}
