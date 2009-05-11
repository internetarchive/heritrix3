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

/**
 * A <code>record-id</code> generator.
 * {@link GeneratorFactory} assumes implementations have a no-arg Constructor.
 * @see GeneratorFactory
 * @author stack
 * @version $Revision$ $Date$
 */
public interface Generator {
	/**
	 * @return A URI that can serve as a record-id.
	 * @throws URISyntaxException
	 */
	public URI getRecordID() throws URISyntaxException;
	
	/**
	 * @param qualifiers Qualifiers to add.
	 * @return A URI qualified with passed <code>qualifiers</code> that can
	 * serve as a record-id, or, a new, unique record-id without qualifiers
	 * (if qualifiers not easily implemented using passed URI scheme).
	 * @throws URISyntaxException
	 */
	public URI getQualifiedRecordID(final Map<String, String> qualifiers)
	throws URISyntaxException;
	
	/**
	 * @param key Name of qualifier
	 * @param value Value of qualifier
	 * @return A URI qualified with passed <code>qualifiers</code> that can
	 * serve as a record-id, or, a new, unique record-id without qualifiers
	 * (if qualifiers not easily implemented using passed URI scheme).
	 * @throws URISyntaxException
	 */
	public URI getQualifiedRecordID(final String key, final String value)
	throws URISyntaxException;
	
	/**
	 * Append (or if already present, update) qualifiers to passed
	 * <code>recordId</code>.  Use with caution. Guard against turning up a
	 * result that already exists.  Use when writing a group of records inside
	 * a single transaction. 
	 * 
	 * How qualifiers are appended/updated varies with URI scheme. Its allowed
	 * that an invocation of this method does nought but call
	 * {@link #getRecordID()}, returning a new URI unrelated to the passed
	 * recordId and passed qualifier.  
	 * @param recordId URI to append qualifier to.
	 * @param qualifiers Map of qualifier values keyed by qualifier name.
	 * @return New URI based off passed <code>uri</code> and passed qualifier.
	 * @throws URISyntaxException if probably constructing URI OR if the
	 * resultant UUID does not differ from the one passed.
	 */
	public URI qualifyRecordID(final URI recordId,
	    final Map<String, String>  qualifiers)
	throws URISyntaxException;
}
