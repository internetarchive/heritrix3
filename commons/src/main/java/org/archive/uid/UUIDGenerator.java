/* $Id$
 *
 * Created on July 27th, 2006
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
class UUIDGenerator implements Generator {
	private static final String SCHEME = "urn:uuid";
	private static final String SCHEME_COLON = SCHEME + ":";
	
	UUIDGenerator() {
		super();
	}

	public synchronized URI qualifyRecordID(URI recordId,
			final Map<String, String> qualifiers)
	throws URISyntaxException {
		return getRecordID();
	}

	private String getUUID() {
		return UUID.randomUUID().toString();
	}
	
	public URI getRecordID() throws URISyntaxException {
		return new URI(SCHEME_COLON + getUUID());
	}
	
	public URI getQualifiedRecordID(
			final String key, final String value)
	throws URISyntaxException {
		return getRecordID();
	}

	public URI getQualifiedRecordID(Map<String, String> qualifiers)
	throws URISyntaxException {
		return getRecordID();
	}
}