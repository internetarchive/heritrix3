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

import java.util.Random;

/**
 * BloomFilter64 tests
 * 
 * @contributor gojomo
 * @version $Date: 2009-11-19 14:39:53 -0800 (Thu, 19 Nov 2009) $, $Revision: 6674 $
 */
public class BloomFilter64bitTest extends BloomFilterTest {
    @Override
    BloomFilter createBloom(long n, int d, Random weightsGenerator) {
        return new BloomFilter64bit(n, d, weightsGenerator, false);
    }
}
