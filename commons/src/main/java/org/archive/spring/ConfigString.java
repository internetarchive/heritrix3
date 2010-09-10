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
package org.archive.spring;

import java.io.Reader;
import java.io.StringReader;

import org.archive.io.ReadSource;

/**
 * A configuration string that provides its own reader via the ReadSource  
 * interface, for convenient use in spring configuration where any of an 
 * inline string, path to local file (ConfigPath), or any other 
 * readable-text-source would all be equally welcome. 
 * 
 * @contributor gojomo
 */
public class ConfigString implements ReadSource {
    String value;
    public String getValue() {
        return value;
    }
    public void setValue(String value) {
        this.value = value;
    }
    public Reader obtainReader() {
        return new StringReader(value);
    }
    
    public ConfigString() {
    }
    public ConfigString(String value) {
        setValue(value); 
    }
}
