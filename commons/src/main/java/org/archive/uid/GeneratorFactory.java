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

/**
 * Factory that generates uids.
 * Singleton.  Default implementation is {@link UUIDGenerator}. To
 * change, specify alternate implementation of {@link Generator} with
 * {@link #SYSTEM_PROPERTY_GENERATOR_KEY} system property.
 * @author stack
 * @version $Revision$ $Date$
 */
public class GeneratorFactory implements Generator {
	public final String SYSTEM_PROPERTY_GENERATOR_KEY =
		this.getClass().toString() + ".generator";
	private static final String DEFAULT_GENERATOR =
		"org.archive.uid.UUIDGenerator";
	private static final GeneratorFactory factory = new GeneratorFactory();
	private final Generator generator;
	
	private GeneratorFactory() {
		super();
		String className = System.getProperty(SYSTEM_PROPERTY_GENERATOR_KEY,
			DEFAULT_GENERATOR);
		Generator ridg = null;
		try {
			Class<?> c = Class.forName(className);
			ridg = (Generator) c.newInstance();
		} catch (Exception e) {
			e.printStackTrace();
		}
		this.generator = ridg;
	}
	
	public URI getRecordID() throws URISyntaxException {
		return this.generator.getRecordID();
	}
	
	public URI getQualifiedRecordID(Map<String, String> qualifiers)
	throws URISyntaxException {
		return this.generator.getQualifiedRecordID(qualifiers);
	}
	
	public URI getQualifiedRecordID(String key, String value)
	throws URISyntaxException {
		return this.generator.getQualifiedRecordID(key, value);
	}
	
	public URI qualifyRecordID(final URI uri,
			final Map<String, String> qualifiers)
	throws URISyntaxException {
		return this.generator.qualifyRecordID(uri, qualifiers);
	}

	public static GeneratorFactory getFactory() {
		return factory;
	}
}
