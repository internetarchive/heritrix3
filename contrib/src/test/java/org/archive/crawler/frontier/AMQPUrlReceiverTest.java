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

package org.archive.crawler.frontier;

import static org.archive.modules.CoreAttributeConstants.A_HERITABLE_KEYS;
import static org.archive.modules.CoreAttributeConstants.A_SOURCE_TAG;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;

import org.archive.modules.CrawlURI;
import org.archive.modules.extractor.Hop;
import org.archive.modules.extractor.LinkContext;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

/**
 * Tests {@link AMQPUrlReceiver.UrlConsumer#populateHeritableMetadata}, which
 * copies the {@code heritableData} of an AMQP message into a CrawlURI.
 */
public class AMQPUrlReceiverTest {

    protected static final String SEED = "https://example.org/seed";

    /** Invokes populateHeritableMetadata with the given heritableData json. */
    protected CrawlURI populate(String heritableDataJson) throws Exception {
        AMQPUrlReceiver receiver = new AMQPUrlReceiver();
        AMQPUrlReceiver.UrlConsumer consumer = receiver.new UrlConsumer(null);

        JSONObject parentUrlMetadata = new JSONObject(
                "{\"pathFromSeed\": \"L\", \"heritableData\": " + heritableDataJson + "}");

        CrawlURI curi = CrawlURI.fromHopsViaString("https://example.org/page LL");
        consumer.populateHeritableMetadata(curi, parentUrlMetadata);
        return curi;
    }

    @SuppressWarnings("unchecked")
    protected HashSet<String> heritableKeysOf(CrawlURI curi) {
        return (HashSet<String>) curi.getData().get(A_HERITABLE_KEYS);
    }

    // ---------------------------------------------------------------
    // the normal states, which must not regress
    // ---------------------------------------------------------------

    /**
     * A source-tagged parent: the tag is stored and the heritable key set is
     * rebuilt as a HashSet, which is the concrete type CrawlURI casts it to.
     */
    @Test
    public void testValidSourceIsStored() throws Exception {
        CrawlURI curi = populate(
                "{\"source\": \"" + SEED + "\", \"heritable\": [\"source\", \"heritable\"]}");

        assertTrue(curi.containsDataKey(A_SOURCE_TAG));
        assertEquals(SEED, curi.getSourceTag());

        HashSet<String> heritable = heritableKeysOf(curi);
        assertEquals(new HashSet<>(java.util.Arrays.asList("source", "heritable")), heritable);
    }

    /**
     * The heritable key set must keep naming itself, otherwise inheritance
     * stops after a single hop. Checks a grandchild, not just a child.
     */
    @Test
    public void testSourceTagIsInheritedBeyondOneHop() throws Exception {
        CrawlURI curi = populate(
                "{\"source\": \"" + SEED + "\", \"heritable\": [\"source\", \"heritable\"]}");

        CrawlURI child = curi.createCrawlURI(
                "https://example.org/child", LinkContext.NAVLINK_MISC, Hop.NAVLINK);
        assertEquals(SEED, child.getSourceTag());

        CrawlURI grandchild = child.createCrawlURI(
                "https://example.org/grandchild", LinkContext.NAVLINK_MISC, Hop.NAVLINK);
        assertEquals(SEED, grandchild.getSourceTag());
    }

    /** An untagged parent yields neither key -- absence is a supported state. */
    @Test
    public void testEmptyHeritableDataYieldsNothing() throws Exception {
        CrawlURI curi = populate("{}");

        assertFalse(curi.containsDataKey(A_SOURCE_TAG));
        assertFalse(curi.containsDataKey(A_HERITABLE_KEYS));
    }

    /**
     * Output for a parent with no source tag. The key must be
     * absent, not present-and-null: downstream readers such as
     * StatisticsTracker guard on containsDataKey(), so a present key with an
     * unusable value is what does the damage.
     */
    @Test
    public void testNullSourceIsSkipped() throws Exception {
        CrawlURI curi = populate("{\"source\": null, \"heritable\": []}");

        assertFalse(curi.containsDataKey(A_SOURCE_TAG));
        assertNull(curi.getSourceTag());
    }

    /**
     * The same, one hop on. CrawlURI.inheritFrom() copies every name in the
     * heritable set with a bare get(), so a skipped value paired with a set that
     * still named it would reintroduce the key with a java null.
     */
    @Test
    public void testSkippedSourceIsNotReintroducedByInheritance() throws Exception {
        CrawlURI curi = populate("{\"source\": null, \"heritable\": []}");

        CrawlURI child = curi.createCrawlURI(
                "https://example.org/child", LinkContext.NAVLINK_MISC, Hop.NAVLINK);

        assertFalse(child.containsDataKey(A_SOURCE_TAG));
    }

    @Test
    public void testNonStringScalarSourceIsSkipped() throws Exception {
        assertFalse(populate("{\"source\": 12345}").containsDataKey(A_SOURCE_TAG));
        assertFalse(populate("{\"source\": true}").containsDataKey(A_SOURCE_TAG));
        assertFalse(populate("{\"source\": {\"a\": \"b\"}}").containsDataKey(A_SOURCE_TAG));
        assertFalse(populate("{\"source\": [\"a\"]}").containsDataKey(A_SOURCE_TAG));
    }

    /** Non-string elements are dropped individually, not the whole set. */
    @Test
    public void testNonStringHeritableElementsAreSkipped() throws Exception {
        CrawlURI curi = populate(
                "{\"heritable\": [\"source\", null, 7, \"heritable\"]}");

        assertEquals(new HashSet<>(java.util.Arrays.asList("source", "heritable")),
                heritableKeysOf(curi));
    }

    /**
     * An empty set must not be stored: CrawlURI.makeHeritable() only registers
     * A_HERITABLE_KEYS in the set it creates, so leaving an empty one in place
     * would later produce a set that doesn't name itself.
     */
    @Test
    public void testEmptyHeritableArrayIsNotStored() throws Exception {
        assertFalse(populate("{\"heritable\": []}").containsDataKey(A_HERITABLE_KEYS));
    }

    /** A heritable value of the wrong shape is ignored rather than cast. */
    @Test
    public void testNonArrayHeritableIsIgnored() throws Exception {
        assertFalse(populate("{\"heritable\": \"source\"}").containsDataKey(A_HERITABLE_KEYS));
        assertFalse(populate("{\"heritable\": null}").containsDataKey(A_HERITABLE_KEYS));
    }

    /** Other string-valued heritable keys still pass through untouched. */
    @Test
    public void testOtherStringValuesAreStored() throws Exception {
        CrawlURI curi = populate(
                "{\"source\": \"" + SEED + "\", \"someOtherKey\": \"someValue\","
                        + " \"heritable\": [\"source\", \"someOtherKey\", \"heritable\"]}");

        assertEquals("someValue", curi.getData().get("someOtherKey"));
        assertEquals(SEED, curi.getSourceTag());
    }
}
