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

package org.archive.crawler.restlet;

import org.json.JSONObject;
import org.webjars.WebJarVersionLocator;

import java.util.Map;

/**
 * Template helper for working with WebJars.
 */
public class WebJars {
    private final WebJarVersionLocator locator = new WebJarVersionLocator();

    /**
     * Generates a JavaScript importmap from a list of webjar libraries.
     * One library per line, with the original JavaScript name ("@codemirror/state" not "codemirror__state").
     */
    public String importMap(String libs) {
        JSONObject imports = new JSONObject();
        String base = "/engine/webjars/";
        for (String lib : libs.split("\n")) {
            if (lib.isBlank()) continue;
            String[] parts = lib.trim().split(" ");
            lib = parts[0];
            String webjar;
            String filePath;
            if (lib.endsWith("/")) {
                webjar = lib.substring(0, lib.length() - 1);
                filePath = "";
            } else {
                webjar = lib;
                filePath = "dist/index.js";
            }
            if (parts.length > 1) filePath = parts[1];
            webjar = webjar.replace("@", "").replace("/", "__");
            imports.put(lib, base + locator.path(webjar, filePath));
        }
        return new JSONObject(Map.of("imports", imports)).toString(2);
    }
}
