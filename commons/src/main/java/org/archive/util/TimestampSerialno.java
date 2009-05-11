/* TimestampSerialno
*
* $Id$
*
* Created July 19th, 2006
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
package org.archive.util;

/**
 * Immutable data structure that holds a timestamp and an accompanying
 * serial number.
 * 
 * For Igor!
 *
 * @author stack
 */
public class TimestampSerialno {
	private final String ts;
	private final int serialNumber;

	public TimestampSerialno(String ts, int serialNo) {
		this.ts = ts;
		this.serialNumber = serialNo;
	}
    
    public TimestampSerialno(int serialNo) {
        this.ts = ArchiveUtils.get14DigitDate();
        this.serialNumber = serialNo;
    }

	/**
	 * @return Returns the now.
	 */
	public String getTimestamp() {
		return this.ts;
	}

	/**
	 * @return Returns the serialNumber.
	 */
	public int getSerialNumber() {
		return this.serialNumber;
	}
}