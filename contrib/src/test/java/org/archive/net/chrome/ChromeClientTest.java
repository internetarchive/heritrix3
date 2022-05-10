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

package org.archive.net.chrome;

import org.json.JSONObject;
import org.junit.Test;

import java.io.IOException;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeNoException;

public class ChromeClientTest {
    @Test
    public void navigate() throws Exception {
        ChromeProcess triedProcess;
        try {
            triedProcess = new ChromeProcess(null, Collections.emptyList());
        } catch (IOException e) {
            assumeNoException("Chrome unavailable", e);
            return;
        }
        try (ChromeProcess process = triedProcess;
             ChromeClient client = new ChromeClient(process.getDevtoolsUrl());
             ChromeWindow window = client.createWindow(1024, 768)) {
            window.navigateAsync("data:text/html,<h1>hi</h1>").get(10, TimeUnit.SECONDS);
            JSONObject result = window.eval("document.getElementsByTagName('h1')[0].textContent");
            assertEquals("hi", result.getString("value"));
        }
    }
}