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

import java.lang.reflect.Method;

/**
 * Wrapper for "keytool" utility main class. Loads class dynamically, trying
 * both the old java and new class names.
 * @see http://kris-sigur.blogspot.com/2014/10/heritrix-java-8-and-sunsecuritytoolskey.html
 */
public class KeyTool {
	public static void main(String[] args) {
		try {
			Class<?> cl;
			try {
				// java 6 and 7
				cl = ClassLoader.getSystemClassLoader().loadClass("sun.security.tools.KeyTool");
			} catch (ClassNotFoundException e) {
				// java 8
				cl = ClassLoader.getSystemClassLoader().loadClass("sun.security.tools.keytool.Main");
			}
			Method main = cl.getMethod("main", new String[0].getClass());
			main.invoke(null, (Object) args);
		} catch (Exception e) {
			if (e instanceof RuntimeException) {
				throw (RuntimeException) e;
			} else {
				throw new RuntimeException(e);
			}
		}
	}

}
