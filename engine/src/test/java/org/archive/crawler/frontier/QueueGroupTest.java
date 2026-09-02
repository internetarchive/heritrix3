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

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the queue-group feature (issue #754): member matching,
 * shared politeness gate, and round-robin rotation.
 */
public class QueueGroupTest {

    private QueueGroup europa() {
        QueueGroup g = new QueueGroup("europa_eu");
        g.setMaxParallelInGroup(1);
        g.setGroupMinDelayMs(4000);
        g.setGroupMembersByHost(Arrays.asList(
                "european-union.europa.eu",           // exact host
                "commission.europa.eu"));             // exact host
        g.setGroupMembersByRegex(Arrays.asList(
                Pattern.compile(".*\\.example\\.org")));  // regex on host part
        g.setGroupMembersBySurt(Arrays.asList(
                "http://(eu,europa,"));                // surt prefix
        return g;
    }

    // ---- member matching ----

    @Test
    public void testExactHostMatch() {
        QueueGroup g = europa();
        assertTrue(g.matches("european-union.europa.eu"));
        assertTrue(g.matches("commission.europa.eu"));
        // hostname-policy suffixes
        assertTrue(g.matches("european-union.europa.eu#8080"));
        assertTrue(g.matches("european-union.europa.eu+2"));
        assertFalse(g.matches("evil-european-union.europa.eu"));
    }

    @Test
    public void testRegexMatchOnHostPart() {
        QueueGroup g = europa();
        assertTrue(g.matches("www.example.org"));
        assertTrue(g.matches("www.example.org#80"));
        assertFalse(g.matches("www.example.com"));
    }

    @Test
    public void testSurtPrefixMatch() {
        QueueGroup g = europa();
        assertTrue(g.matches("http://(eu,europa,commission,"));
        assertFalse(g.matches("http://(com,example,"));
    }

    @Test
    public void testRegexOnlyMatchesConfiguredPattern() {
        QueueGroup g = new QueueGroup("bad");
        g.setGroupMembersByRegex(Arrays.asList(Pattern.compile("good\\..*")));
        g.setGroupMembersByHost(Arrays.asList("good.host"));
        assertFalse(g.matches("anything"));
        assertTrue(g.matches("good.host"));
    }

    // ---- shared politeness gate ----

    @Test
    public void testGateConcurrencyCeiling() {
        QueueGroup g = new QueueGroup("g");
        g.setMaxParallelInGroup(1);
        g.setGroupMinDelayMs(0);
        long now = 1000L;
        assertTrue(g.canProceed(now));
        g.acquire();
        assertFalse(g.canProceed(now)); // ceiling reached
        g.release(now, 0);
        assertTrue(g.canProceed(now));  // slot freed, no cool-down
    }

    @Test
    public void testGateCoolDownUsesMaxOfDelays() {
        QueueGroup g = new QueueGroup("g");
        g.setMaxParallelInGroup(1);
        g.setGroupMinDelayMs(4000);
        long now = 1000L;
        g.acquire();
        // per-URI delay smaller than group min -> group min wins
        g.release(now, 500);
        assertEquals(now + 4000, g.getWakeTime());
        assertFalse(g.canProceed(now + 3999));
        assertTrue(g.canProceed(now + 4000));
    }

    @Test
    public void testGateCoolDownUsesPerUriDelayWhenLarger() {
        QueueGroup g = new QueueGroup("g");
        g.setMaxParallelInGroup(1);
        g.setGroupMinDelayMs(1000);
        long now = 1000L;
        g.acquire();
        g.release(now, 8000); // per-URI delay larger than group min
        assertEquals(now + 8000, g.getWakeTime());
    }

    // ---- round-robin rotation ----

    @Test
    public void testRoundRobinRotation() {
        QueueGroup g = new QueueGroup("g");
        g.noteMember("a");
        g.noteMember("b");
        g.noteMember("c");
        Predicate<String> allReady = k -> true;

        // initially it's a's turn
        assertTrue(g.isTurn(allReady, "a"));
        assertFalse(g.isTurn(allReady, "b"));
        g.advanceRotation("a");

        // now b's turn
        assertTrue(g.isTurn(allReady, "b"));
        assertFalse(g.isTurn(allReady, "c"));
        g.advanceRotation("b");

        // now c's turn
        assertTrue(g.isTurn(allReady, "c"));
        g.advanceRotation("c");

        // wraps back to a
        assertTrue(g.isTurn(allReady, "a"));
    }

    @Test
    public void testRotationSkipsNotReadyMembers() {
        QueueGroup g = new QueueGroup("g");
        g.noteMember("a");
        g.noteMember("b");
        g.noteMember("c");
        // only "c" is ready; a and b are snoozed/empty
        Set<String> ready = new HashSet<String>(Arrays.asList("c"));
        Predicate<String> readyChecker = ready::contains;

        // a is at the pointer but not ready -> skipped, so c gets the turn
        assertTrue(g.isTurn(readyChecker, "c"));
    }

    // ---- manager resolution and caching ----

    @Test
    public void testManagerGroupForAndDiscovery() {
        QueueGroupManager mgr = new QueueGroupManager();
        QueueGroup g = europa();
        mgr.setGroups(Arrays.asList(g));

        assertSame(g, mgr.groupFor("european-union.europa.eu"));
        assertSame(g, mgr.groupFor("commission.europa.eu"));
        assertNull(mgr.groupFor("unrelated.example.net"));

        // discovered members registered for rotation
        assertTrue(g.memberKeys().contains("european-union.europa.eu"));
        assertTrue(g.memberKeys().contains("commission.europa.eu"));
    }

    @Test
    public void testManagerNoGroupsAlwaysNull() {
        QueueGroupManager mgr = new QueueGroupManager();
        assertNull(mgr.groupFor("anything.example.com"));
    }

    @Test
    public void testManagerCrud() {
        QueueGroupManager mgr = new QueueGroupManager();
        QueueGroup g = new QueueGroup("g1");
        g.setGroupMembersByHost(Arrays.asList("a.example.com"));
        mgr.addGroup(g);
        assertSame(g, mgr.groupFor("a.example.com"));

        assertTrue(mgr.addMember("g1", "b.example.com"));
        assertSame(g, mgr.groupFor("b.example.com"));

        assertTrue(mgr.removeGroup("g1"));
        assertNull(mgr.groupFor("a.example.com"));
    }
}
