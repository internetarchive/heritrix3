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

package org.archive.modules;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;

/**
 * Connection and channel lifecycle. The client recovers a connection unless it
 * is aborted, so dropping a reference without aborting leaves it open and
 * reconnecting where this producer can no longer reach it.
 */
public class AMQPProducerTest {

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

    /** A connection that records abort() and reports the given open state. */
    protected static Connection connection(boolean open, AtomicBoolean aborted, Channel toCreate) {
        return connectionProxy((proxy, method, args) -> {
            switch (method.getName()) {
            case "isOpen": return open;
            case "abort": aborted.set(true); return null;
            case "createChannel": return toCreate;
            default: return defaultValue(method);
            }
        });
    }

    protected static Channel channel(boolean open, AtomicBoolean aborted) {
        return channelProxy((proxy, method, args) -> {
            switch (method.getName()) {
            case "isOpen": return open;
            case "abort": aborted.set(true); return null;
            default: return defaultValue(method);
            }
        });
    }

    protected static AMQPProducer producer() {
        // Port 1 so any connection this fails to short-circuit fails fast
        // rather than reaching a real broker.
        return new AMQPProducer("amqp://guest:guest@127.0.0.1:1/%2f", "ex", "rk");
    }

    /**
     * connect() used to assign over the field. The displaced connection stayed
     * open and recovering, one orphan per drop.
     */
    @Test
    public void testExistingConnectionIsAbortedBeforeReconnecting() throws Exception {
        AtomicBoolean aborted = new AtomicBoolean();
        AMQPProducer producer = producer();
        producer.connection = connection(false, aborted, null);

        // The replacement attempt fails -- nothing is listening on port 1 --
        // but the abort must already have happened by then.
        assertThrows(Exception.class, () -> producer.channel());
        assertTrue(aborted.get(), "the displaced connection should have been aborted");
    }

    /** The same on shutdown, including when the connection is already closed. */
    @Test
    public void testStopAbortsAnAlreadyClosedConnection() {
        AtomicBoolean aborted = new AtomicBoolean();
        AMQPProducer producer = producer();
        producer.connection = connection(false, aborted, null);

        producer.stop();

        assertTrue(aborted.get(), "a closed connection still needs aborting to stop recovery");
        assertNull(producer.connection);
    }

    @Test
    public void testStopAbortsAnOpenConnection() {
        AtomicBoolean aborted = new AtomicBoolean();
        AMQPProducer producer = producer();
        producer.connection = connection(true, aborted, null);

        producer.stop();

        assertTrue(aborted.get());
        assertNull(producer.connection);
    }

    /** stop() is called on crawl end and must tolerate never having connected. */
    @Test
    public void testStopWithoutAConnectionIsHarmless() {
        producer().stop();
    }

    /**
     * A dead thread channel must be aborted, not just dropped, or it stays
     * registered on the connection for as long as the connection lives.
     */
    @Test
    public void testDeadChannelIsAbortedBeforeBeingReplaced() throws Exception {
        AtomicBoolean channelAborted = new AtomicBoolean();
        AtomicBoolean connectionAborted = new AtomicBoolean();
        Channel fresh = channel(true, new AtomicBoolean());

        AMQPProducer producer = producer();
        producer.connection = connection(true, connectionAborted, fresh);
        producer.threadChannel.set(channel(false, channelAborted));

        assertSame(fresh, producer.channel());
        assertTrue(channelAborted.get(), "the dead channel should have been aborted");
        assertFalse(connectionAborted.get(), "an open connection should be reused");
    }

    /** An open channel is handed back untouched. */
    @Test
    public void testOpenChannelIsReused() throws Exception {
        AtomicBoolean aborted = new AtomicBoolean();
        Channel open = channel(true, aborted);

        AMQPProducer producer = producer();
        producer.connection = connection(true, new AtomicBoolean(), null);
        producer.threadChannel.set(open);

        assertSame(open, producer.channel());
        assertFalse(aborted.get());
    }
}
