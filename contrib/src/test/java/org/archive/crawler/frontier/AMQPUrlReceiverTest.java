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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashSet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.archive.modules.CrawlURI;
import org.archive.modules.extractor.Hop;
import org.archive.modules.extractor.LinkContext;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import com.rabbitmq.client.AlreadyClosedException;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.Envelope;
import com.rabbitmq.client.ShutdownSignalException;

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

    // ---------------------------------------------------------------
    // connection lifecycle
    //
    // The client closes the whole channel when handleDelivery throws, so an
    // escaping exception takes down every other consumer and in-flight
    // delivery on it. These pin that nothing escapes, and that dead objects
    // are aborted rather than merely dereferenced.
    // ---------------------------------------------------------------

    /** Default return for proxied methods a test does not care about. */
    protected static Object defaultValue(Method method) {
        Class<?> t = method.getReturnType();
        if (t == boolean.class) return false;
        if (t == int.class) return 0;
        if (t == long.class) return 0L;
        if (t == String.class) return "";
        return null;
    }

    protected static Channel channelProxy(InvocationHandler handler) {
        return (Channel) Proxy.newProxyInstance(Channel.class.getClassLoader(),
                new Class<?>[] { Channel.class }, handler);
    }

    protected static Connection connectionProxy(InvocationHandler handler) {
        return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
                new Class<?>[] { Connection.class }, handler);
    }

    protected static Envelope envelope() {
        return new Envelope(1L, false, "penumbra_exchange", "test_queue");
    }

    /** A channel that records acks and fails them as a closed channel would. */
    protected static Channel closedChannelRecording(AtomicBoolean acked) {
        return channelProxy((proxy, method, args) -> {
            if ("basicAck".equals(method.getName())) {
                acked.set(true);
                throw new AlreadyClosedException(
                        new ShutdownSignalException(false, false, null, null));
            }
            return defaultValue(method);
        });
    }

    protected static void deliver(Channel channel, String body) throws Exception {
        AMQPUrlReceiver receiver = new AMQPUrlReceiver();
        receiver.new UrlConsumer(channel).handleDelivery(
                "amq.ctag-test", envelope(), null, body.getBytes("UTF-8"));
    }

    /**
     * A failed ack must not escape. The broker redelivers, so swallowing it
     * costs a duplicate; letting it out costs the channel.
     */
    @Test
    public void testAckFailureOnClosedChannelDoesNotEscape() throws Exception {
        AtomicBoolean acked = new AtomicBoolean();

        // Not a GET, so the handler skips processing and goes to the ack.
        deliver(closedChannelRecording(acked),
                "{\"url\": \"https://example.org/\", \"method\": \"POST\"}");

        assertTrue(acked.get(), "the ack should have been attempted");
    }

    /**
     * Malformed json is a poison message: parsing it used to run outside the
     * try and kill the channel for every other consumer on it.
     */
    @Test
    public void testMalformedJsonDoesNotEscape() throws Exception {
        AtomicBoolean acked = new AtomicBoolean();

        deliver(closedChannelRecording(acked), "this is not json");

        assertTrue(acked.get(), "a poison message should still be acked, not redelivered forever");
    }

    /** A message with no method at all is ignored, not thrown on. */
    @Test
    public void testMissingMethodDoesNotEscape() throws Exception {
        AtomicBoolean acked = new AtomicBoolean();

        deliver(closedChannelRecording(acked), "{\"url\": \"https://example.org/\"}");

        assertTrue(acked.get());
    }

    @SuppressWarnings("unchecked")
    protected static AtomicReference<String> consumerTagOf(AMQPUrlReceiver receiver)
            throws Exception {
        Field field = AMQPUrlReceiver.class.getDeclaredField("consumerTag");
        field.setAccessible(true);
        return (AtomicReference<String>) field.get(receiver);
    }

    /**
     * A late shutdown signal must not clear a tag belonging to a consumer that
     * has since replaced it, or StarterRestarter starts a second consumer
     * alongside the live one and they accumulate on every failure.
     */
    @Test
    public void testShutdownSignalLeavesANewerConsumerTagAlone() throws Exception {
        AMQPUrlReceiver receiver = new AMQPUrlReceiver();
        AtomicReference<String> tag = consumerTagOf(receiver);
        tag.set("amq.ctag-new");

        ShutdownSignalException sig = new ShutdownSignalException(false, true, null, null);
        receiver.new UrlConsumer(null).handleShutdownSignal("amq.ctag-old", sig);

        assertEquals("amq.ctag-new", tag.get());
    }

    /** The matching signal does still clear it, so the restarter rebuilds. */
    @Test
    public void testShutdownSignalClearsItsOwnConsumerTag() throws Exception {
        AMQPUrlReceiver receiver = new AMQPUrlReceiver();
        AtomicReference<String> tag = consumerTagOf(receiver);
        tag.set("amq.ctag-live");

        ShutdownSignalException sig = new ShutdownSignalException(false, true, null, null);
        receiver.new UrlConsumer(null).handleShutdownSignal("amq.ctag-live", sig);

        assertNull(tag.get());
    }

    /**
     * Dropping the reference alone leaves the old channel live and consuming,
     * invisible to this bean, since the client would go on recovering it.
     */
    @Test
    public void testDeadChannelIsAbortedBeforeBeingReplaced() throws Exception {
        AtomicBoolean aborted = new AtomicBoolean();
        Channel dead = channelProxy((proxy, method, args) -> {
            switch (method.getName()) {
            case "isOpen": return false;
            case "abort": aborted.set(true); return null;
            default: return defaultValue(method);
            }
        });
        Channel fresh = channelProxy((proxy, method, args) ->
                "isOpen".equals(method.getName()) ? true : defaultValue(method));
        Connection connection = connectionProxy((proxy, method, args) -> {
            switch (method.getName()) {
            case "isOpen": return true;
            case "createChannel": return fresh;
            default: return defaultValue(method);
            }
        });

        AMQPUrlReceiver receiver = new AMQPUrlReceiver();
        receiver.connection = connection;
        receiver.channel = dead;

        assertSame(fresh, receiver.channel());
        assertTrue(aborted.get(), "the dead channel should have been aborted");
    }

    /** The same, one level up: an unaborted connection keeps its consumers. */
    @Test
    public void testDeadConnectionIsAbortedBeforeBeingReplaced() throws Exception {
        AtomicBoolean aborted = new AtomicBoolean();
        Connection dead = connectionProxy((proxy, method, args) -> {
            switch (method.getName()) {
            case "isOpen": return false;
            case "abort": aborted.set(true); return null;
            default: return defaultValue(method);
            }
        });

        AMQPUrlReceiver receiver = new AMQPUrlReceiver();
        receiver.setAmqpUri("amqp://guest:guest@127.0.0.1:1/%2f");
        receiver.connection = dead;

        // The replacement attempt fails -- nothing is listening -- but the
        // abort must already have happened by then.
        assertThrows(Exception.class, () -> receiver.connection());
        assertTrue(aborted.get(), "the dead connection should have been aborted");
    }

    /** An open channel is handed back untouched. */
    @Test
    public void testOpenChannelIsReused() throws Exception {
        AtomicBoolean aborted = new AtomicBoolean();
        Channel open = channelProxy((proxy, method, args) -> {
            switch (method.getName()) {
            case "isOpen": return true;
            case "abort": aborted.set(true); return null;
            default: return defaultValue(method);
            }
        });

        AMQPUrlReceiver receiver = new AMQPUrlReceiver();
        receiver.channel = open;

        assertSame(open, receiver.channel());
        assertFalse(aborted.get());
    }
}
