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

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Wrapper for "keytool" utility main class. Loads class dynamically, trying
 * both the old java and new class names.
 * @see <a href="http://kris-sigur.blogspot.com/2014/10/heritrix-java-8-and-sunsecuritytoolskey.html">http://kris-sigur.blogspot.com/2014/10/heritrix-java-8-and-sunsecuritytoolskey.html</a>
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
			Method main = cl.getMethod("main", String[].class);
			main.invoke(null, (Object) args);
		} catch (IllegalAccessException e) {
			// java 16
			List<String> command = new ArrayList<>();
			command.add(System.getProperty("java.home") + File.separator + "bin" + File.separator + "keytool");
			command.addAll(Arrays.asList(args));
			try {
				new ProcessBuilder(command).inheritIO().start().waitFor();
			} catch (IOException e2) {
				throw new UncheckedIOException(e2);
			} catch (InterruptedException e2) {
				Thread.currentThread().interrupt();
			}
		} catch (Exception e) {
			if (e instanceof RuntimeException) {
				throw (RuntimeException) e;
			} else {
				throw new RuntimeException(e);
			}
		}
	}

}
