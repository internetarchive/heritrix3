/*
 * This file is part of the Heritrix web crawler (crawler.archive.org).
 *
 * Licensed to the Internet Archive (IA) by one or more individual
 * contributors.
 *
 * The IA licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.archive.modules.deciderules;

import java.util.HashSet;
import java.util.Set;

import org.archive.modules.CrawlURI;
import org.archive.modules.seeds.SeedModule;

/**
 * Rule applies the configured decision for any URI with discovered from one of
 * the seeds in {@code sourceSeeds}.
 *
 * {@link SeedModule#getSourceTagSeeds()} must be enabled or the rule will never
 * apply.
 *
 * @contributor nlevitt
 */
public class SourceSeedDecideRule extends PredicatedDecideRule {

    private static final long serialVersionUID = 1l;

    protected Set<String> sourceSeeds = new HashSet<String>();
    public void setSourceSeeds(Set<String> sourceSeeds) {
        this.sourceSeeds = sourceSeeds;
    }
    public Set<String> getSourceSeeds() {
        return sourceSeeds;
    }

    @Override
    protected boolean evaluate(CrawlURI curi) {
        return sourceSeeds.contains(curi.getSourceTag());
    }
}
