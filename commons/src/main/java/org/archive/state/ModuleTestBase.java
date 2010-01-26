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
package org.archive.state;


import java.io.File;
import java.io.Serializable;
import java.util.Arrays;

import junit.framework.TestCase;

import org.apache.commons.lang.SerializationUtils;


/**
 * Base class for unit testing Module implementations.
 * 
 * @author pjack
 */
public abstract class ModuleTestBase extends TestCase {


    /**
     * Magical constructor that attempts to auto-create static key field
     * descriptions for your module class.
     * 
     * <p>If {@link #getSourceCodeDir} and {@link #getResourceDir} both return
     * non-null values, then the constructor will look in the resources 
     * directory for an English resource file for the class.  If it finds
     * one, nothing magical happens.
     * 
     * <p>Otherwise, the source code for the module being tested is loaded, 
     * and parsed to extract the JavaDoc descriptions for the static key 
     * fields.  The results are stored in the appropriate English locale file 
     * in the resource directory.  
     * 
     * <p>Note the parsing is naive; at minimum, you should load the resulting
     * locale file and remove any HTML markup.
     */    
    public ModuleTestBase() {
        getSourceCodeDir();
        getResourceDir();
    }

    
    /**
     * Returns the location of the source code directory for your project.
     * This defaults to "src/main/java", which is the standard for projects
     * built with maven2.  If you use a different source code directory,
     * you should override this method.
     * 
     * <p>If you want to disable automatic key description generation,
     * return null from this method.
     * 
     * @return   the source code directory for the project
     */
    protected File getSourceCodeDir() {
        return getProjectDir("src/main/java");
    }
    

    /**
     * Returns the location of the Java resources directory for your project.
     * This defaults to "src/resources/java", which is the standard for projects
     * built with maven2.  If you use a different source code directory --
     * for instance, if your resources directory is the same as your source
     * code directory -- you should override this method.
     * 
     * <p>If you want to disable automatic key description generation,
     * return null from this method.
     * 
     * @return   the source code directory for the project
     */
    protected File getResourceDir() {
        return getProjectDir("src/main/resources");
    }
    
    
    /**
     * Returns a project directory for a Heritrix subproject.  This is here
     * so that the src and resources directories can be found whether the
     * unit test is run using maven2 or using Eclipse.  The two build systems
     * use different working directories.
     * 
     * @param path  the path   the path to find
     * @return   the found path
     */
    private File getProjectDir(String path) {
        File r = new File(path);
        if (r.exists()) {
            return r;
        }
        String cname = getClass().getName();
        if (cname.startsWith("org.archive.processors")) {
            return new File("modules/" + path);
        }
        if (cname.startsWith("org.archive.deciderules")) {
            return new File("modules/" + path);
        }
        if (cname.startsWith("org.archive.crawler")) {
            return new File("engine/" + path);
        }
        return null;
    }
    
    /**
     * Returns the class of the module to test. Deduces from 
     * test class name if possible. 
     * 
     * @return   the class of the module to test
     */
    protected Class<?> getModuleClass() {
        String myClassName = this.getClass().getCanonicalName();
        if(!myClassName.endsWith("Test")) {
            throw new UnsupportedOperationException(
                    "Cannot get module class of "+myClassName);
        }
        String moduleClassName = myClassName.substring(0,myClassName.length()-4);
        try {
            return Class.forName(moduleClassName);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Return an example instance of the module.  This is used by 
     * testSerialization to ensure the module can be serialized.
     * 
     * @return   an example instance of the module
     * @throws Exception   if the module cannot be constructed for any reason
     */
    protected Object makeModule() throws Exception {
        return getModuleClass().newInstance();
    }

    /**
     * Tests that the module can be serialized.  The value returned by 
     * {@link #makeModule} is serialized to a byte array, and then 
     * deserialized, and then serialized to a second byte array.  The results
     * are passed to {@link #verifySerialization}, which will simply compare
     * the two byte arrays for equality.  (That won't always work; see
     * that method for details).
     * 
     * <p>If nothing else, this test is useful for catching NotSerializable
     * exceptions for your module or classes it depends on.
     * 
     * @throws Exception   if the module cannot be serialized
     */
    public void testSerializationIfAppropriate() throws Exception {
        Object first = makeModule();
        if(!(first instanceof Serializable)) {
            return; 
        }
        byte[] firstBytes = SerializationUtils.serialize((Serializable)first); 
         
        Object second = SerializationUtils.deserialize(firstBytes);
        byte[] secondBytes = SerializationUtils.serialize((Serializable)second);
        
        Object third = SerializationUtils.deserialize(secondBytes);
        byte[] thirdBytes = SerializationUtils.serialize((Serializable)third);
        
        // HashMap serialization reverses order of items in linked buckets 
        // each roundtrip -- so don't check one roundtrip, check two
//        verifySerialization(first, firstBytes, second, secondBytes);
        verifySerialization(first, firstBytes, third, thirdBytes);
    }

    /**
     * Verifies that serialization was successful.
     * 
     * <p>By default, this method simply compares the first and second byte
     * arrays for equality.  That may not work if you use custom serialization
     * -- for instance, if you're serializing a timestamp.  If that's the case
     * you should override this method to compare the given objects, or to 
     * simply do nothing.  (If this method does nothing, then the 
     * {@link #testSerialization} test is still useful for catching
     * NotSerializable problems).
     * 
     * @param first   the first object that was serialized
     * @param firstBytes   the byte array the first object was serialized to
     * @param second  the second object that was serialized
     * @param secondBytes  the byte array the second object was serialized to
     * @throws Exception   if anyt problem occurs
     */
    protected void verifySerialization(Object first, byte[] firstBytes, 
            Object second, byte[] secondBytes) throws Exception {
        assertTrue(Arrays.equals(firstBytes, secondBytes));
    }
}
