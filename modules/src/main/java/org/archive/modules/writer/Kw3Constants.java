/*
 * Created on 2007-feb-09
 *
 * Copyright (C) 2007 Royal Library of Sweden.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package org.archive.modules.writer;

public interface Kw3Constants {

    /* 
     * A bunch of keys for the header fields in the ArchiveInfo part 
     * of the MIME-file. 
     */
    public static String COLLECTION_KEY = "HTTP-Collection";
    public static String HARVESTER_KEY = "HTTP-Harvester";
    public static String URL_KEY = "HTTP-URL";
    public static String IP_ADDRESS_KEY = "HTTP-IP-Address";
    public static String HEADER_LENGTH_KEY = "HTTP-Header-Length";
    public static String HEADER_MD5_KEY = "HTTP-Header-MD5";
    public static String CONTENT_LENGTH_KEY = "HTTP-Content-Length";
    public static String CONTENT_MD5_KEY = "HTTP-Content-MD5";
    public static String ARCHIVE_TIME_KEY = "HTTP-Archive-Time";
    public static String STATUS_CODE_KEY = "HTTP-Status-Code";
    public static String CONTENT_TYPE_KEY = "Content-Type";
    
}
