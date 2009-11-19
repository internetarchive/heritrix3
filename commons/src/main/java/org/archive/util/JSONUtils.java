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

import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Utilities for working with JSON/JSONObjects.
 * 
 */
public class JSONUtils {

    @SuppressWarnings("unchecked")
    public static void putAllLongs(Map<String,Long> targetMap, JSONObject sourceJson) throws JSONException {
        for(String k : new Iteratorable<String>(sourceJson.keys())) {
            targetMap.put(k, sourceJson.getLong(k));
        }
    }

    @SuppressWarnings("unchecked")
    public static void putAllAtomicLongs(Map<String,AtomicLong> targetMap, JSONObject sourceJson) throws JSONException {
        for(String k : new Iteratorable<String>(sourceJson.keys())) {
            targetMap.put(k, new AtomicLong(sourceJson.getLong(k)));
        }
    }
}
