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
package org.archive.crawler.frontier.precedence;

import com.sleepycat.je.DatabaseEntry;
import org.archive.bdb.KryoBinding;
import org.archive.state.ModuleTestBase;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests for HighestUriQueuePrecedencePolicy
 */
public class HighestUriQueuePrecedencePolicyTest extends ModuleTestBase {

    @Test
    public void testHighestUriPrecedenceProvider() {
        HighestUriQueuePrecedencePolicy policy = new HighestUriQueuePrecedencePolicy();
        HighestUriQueuePrecedencePolicy.HighestUriPrecedenceProvider provider = policy.new HighestUriPrecedenceProvider(0);

        KryoBinding<HighestUriQueuePrecedencePolicy.HighestUriPrecedenceProvider> binding = new KryoBinding<>(HighestUriQueuePrecedencePolicy.HighestUriPrecedenceProvider.class);
        DatabaseEntry dbEntry = new DatabaseEntry();
        binding.objectToEntry(provider, dbEntry);

        HighestUriQueuePrecedencePolicy.HighestUriPrecedenceProvider provider2 = binding.entryToObject(dbEntry);
        assertEquals(provider.getClass(), provider2.getClass());
    }
}

