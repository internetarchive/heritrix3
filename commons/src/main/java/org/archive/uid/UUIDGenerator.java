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
package org.archive.uid;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;
import java.util.UUID;

/**
 * Generates <a href="http://en.wikipedia.org/wiki/UUID">UUID</a>s, using
 * {@link java.util.UUID java.util.UUID}, formatted as URNs from the UUID
 * namespace [See <a href="http://www.ietf.org/rfc/rfc4122.txt">RFC4122</a>].
 * Here is an examples of the type of ID it makes: 
 * <code>urn:uuid:0161811f-5da6-4c6e-9808-a2fab97114cf</code>. Always makes a
 * new identifier even when passed qualifiers.
 *
 * @author stack
 * @version $Revision$ $Date$
 * @see <a href="http://ietf.org/rfc/rfc4122.txt">RFC4122</a>
 */
public class UUIDGenerator implements RecordIDGenerator {
	private static final String SCHEME = "urn:uuid";
	private static final String SCHEME_COLON = SCHEME + ":";
	
	public UUIDGenerator() {
		super();
	}

	public URI qualifyRecordID(URI recordId,
			final Map<String, String> qualifiers) {
		return getRecordID();
	}

	private String getUUID() {
		return UUID.randomUUID().toString();
	}
	
	public URI getRecordID() {
		try {
            return new URI(SCHEME_COLON + getUUID());
        } catch (URISyntaxException e) {
            // should be impossible
            throw new RuntimeException(e); 
        }
	}
	
	public URI getQualifiedRecordID(
			final String key, final String value){
		return getRecordID();
	}

	public URI getQualifiedRecordID(Map<String, String> qualifiers){
		return getRecordID();
	}
}