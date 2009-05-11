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

package org.archive.util.anvl;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

import org.archive.io.UTF8Bytes;

/**
 * List of {@link ANVLRecord}s.
 * @author stack
 * @version $Date$ $Version$
 */
public class ANVLRecords extends ArrayList<ANVLRecord> implements UTF8Bytes {
	private static final long serialVersionUID = 5361551920550106113L;

	public ANVLRecords() {
	    super();
	}

	public ANVLRecords(int initialCapacity) {
		super(initialCapacity);
	}

	public ANVLRecords(Collection<ANVLRecord> c) {
		super(c);
	}

	public byte[] getUTF8Bytes() throws UnsupportedEncodingException {
		return toString().getBytes(UTF8);
	}
	
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		for (final Iterator<ANVLRecord> i = iterator(); i.hasNext();) {
			sb.append(i.next().toString());
		}
		return super.toString();
	}
}