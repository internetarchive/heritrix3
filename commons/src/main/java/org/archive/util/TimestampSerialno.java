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