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

package org.archive.net.webdriver;

// https://w3c.github.io/webdriver-bidi/#module-browsingContext
public interface BrowsingContext extends BiDiModule {
    CreateResult create(CreateType type);

    NavigateResult navigate(Context context, String url, ReadinessState wait);

    void close(Context context);

    enum CreateType {tab, window}

    enum ReadinessState {none, interactive, complete}

    record CreateResult(Context context) {
    }

    record Load(Context context, Navigation navigation, long timestamp, String url) implements BiDiEvent {
    }

    record NavigateResult(Navigation navigation, String url) {
    }

    // This is browsingContext.BrowsingContext in the standard, but Java doesn't let us name it that.
    record Context(String id) implements BiDiJson.Identifier {
    }

    record Navigation(String id) implements BiDiJson.Identifier {
    }
}
