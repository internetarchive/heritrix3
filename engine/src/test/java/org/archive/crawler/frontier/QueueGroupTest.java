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

import org.archive.modules.CrawlURI;
import org.archive.modules.deciderules.DecideRule;
import org.archive.modules.deciderules.MatchesRegexDecideRule;
import org.archive.net.UURIFactory;

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
        g.setGroupMembersByRegex(Arrays.asList(Pattern.compile(".*\\.ec\\.europa\\.eu")));  // regex on host part
        g.setGroupMembersBySurt(Arrays.asList("eu,europa,"));      // surt prefix (authority form, no scheme)
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
        assertTrue(g.matches("digital-strategy.ec.europa.eu"));
        assertTrue(g.matches("digital-strategy.ec.europa.eu#80"));
        assertFalse(g.matches("www.example.com"));
        assertFalse(g.matches("www.example.org"));
    }

    @Test
    public void testSurtPrefixMatch() {
        QueueGroup g = europa();
        // real classKeys produced by SurtAuthorityQueueAssignmentPolicy: the
        // authority part of the SURT, without scheme prefix or parentheses
        assertTrue(g.matches("eu,europa,commission,"));
        assertTrue(g.matches("eu,europa,digital-strategy,"));
        // non-default port: ':' is replaced by '#'
        assertTrue(g.matches("eu,europa,commission,#8080"));
        assertFalse(g.matches("com,example,"));
        assertFalse(g.matches("eu,other,"));
        // a scheme-prefixed SURT is never a real classKey and must not match
        assertFalse(g.matches("http://(eu,europa,commission,"));
    }

    @Test
    public void testRegexOnlyMatchesConfiguredPattern() {
        QueueGroup g = new QueueGroup("good");
        g.setGroupMembersByRegex(Arrays.asList(Pattern.compile("good\\..*")));
        g.setGroupMembersByHost(Arrays.asList("good.host"));
        assertFalse(g.matches("anything"));
        assertTrue(g.matches("good.host"));
    }

    // ---- DecideRule-based membership (evaluated against the real CrawlURI) ----

    private static DecideRule matchesUriRegex(String regex) {
        MatchesRegexDecideRule rule = new MatchesRegexDecideRule();
        rule.setRegex(Pattern.compile(regex));
        return rule;
    }

    private static CrawlURI curi(String uri, String classKey) throws Exception {
        CrawlURI c = new CrawlURI(UURIFactory.getInstance(uri));
        c.setClassKey(classKey);
        return c;
    }

    @Test
    public void testDecideRuleMembershipOnCrawlURI() throws Exception {
        QueueGroup g = new QueueGroup("byRule");
        g.setGroupMembersByDecideRule(matchesUriRegex("https?://.*\\.example\\.net/.*"));

        // the DecideRule accepts this URI -> member, regardless of classKey
        assertTrue(g.matches(curi("http://news.example.net/story", "whatever-key")));
        // non-matching URI -> not a member (no classKey matcher configured)
        assertFalse(g.matches(curi("http://news.example.com/story", "whatever-key")));
        // bare classKey lookup cannot use the rule
        assertFalse(g.matches("news.example.net"));
    }

    @Test
    public void testDecideRuleFallsBackToClassKeyMatchers() throws Exception {
        QueueGroup g = new QueueGroup("mixed");
        g.setGroupMembersByDecideRule(matchesUriRegex(".*\\.special\\.example/.*"));
        g.setGroupMembersByHost(Arrays.asList("plain.example"));

        // matched via the classKey host matcher even though the rule rejects it
        assertTrue(g.matches(curi("http://plain.example/x", "plain.example")));
        // matched via the DecideRule
        assertTrue(g.matches(curi("http://a.special.example/y", "a.special.example")));
    }

    @Test
    public void testManagerGroupForCrawlURICachesByClassKey() throws Exception {
        QueueGroupManager mgr = new QueueGroupManager();
        QueueGroup g = new QueueGroup("byRule");
        g.setGroupMembersByDecideRule(matchesUriRegex("https?://.*\\.example\\.net/.*"));
        mgr.setGroups(Arrays.asList(g));

        String classKey = "news.example.net";
        // a bare classKey lookup cannot resolve a DecideRule-only group
        assertNull(mgr.groupFor(classKey));

        // resolving with the real URI finds the group and caches it
        assertSame(g, mgr.groupFor(curi("http://news.example.net/story", classKey)));
        assertTrue(g.memberKeys().contains(classKey));

        // subsequent classKey-only lookups now return the cached group,
        // overriding the earlier NONE
        assertSame(g, mgr.groupFor(classKey));
    }

    @Test
    public void testDecideRuleMembershipSurvivesCheckpoint() throws Exception {
        QueueGroupManager mgr = new QueueGroupManager();
        QueueGroup g = new QueueGroup("byRule");
        g.setGroupMembersByDecideRule(matchesUriRegex("https?://.*\\.example\\.net/.*"));
        mgr.setGroups(Arrays.asList(g));

        String classKey = "news.example.net";
        // resolve membership once, with the real URI (as sendToQueue does)
        assertSame(g, mgr.groupFor(curi("http://news.example.net/story", classKey)));

        // serialize + deserialize the manager (simulating a checkpoint cycle,
        // which resets the transient cache and discovered members)
        QueueGroupManager restored = roundTrip(mgr);

        // after restore, a bare classKey lookup still resolves the group even
        // though no new URI has been enqueued and the DecideRule cannot be
        // re-evaluated from the classKey alone
        QueueGroup rg = restored.groupFor(classKey);
        assertNotNull(rg);
        assertEquals("byRule", rg.getName());
        // the discovered members (round-robin rotation) have been restored too
        assertTrue(rg.memberKeys().contains(classKey));
    }

    @Test
    public void testStalePersistedMembershipDroppedOnRebind() {
        // emulate a checkpoint whose persisted mapping references a group name
        // that no longer exists in the (reconfigured) set of groups
        QueueGroupManager mgr = new QueueGroupManager();
        mgr.groups = new java.util.concurrent.CopyOnWriteArrayList<QueueGroup>(
                Arrays.asList(new QueueGroup("current")));
        mgr.persistedMembership = new java.util.concurrent.ConcurrentHashMap<String, String>();
        mgr.persistedMembership.put("stale.host", "removed-group");
        mgr.persistedMembership.put("live.host", "current");

        mgr.rebindPersistedMembership();

        // the stale entry (group no longer configured) is dropped defensively
        assertFalse(mgr.persistedMembership.containsKey("stale.host"));
        // the valid entry survives and is re-bound to the live group instance
        assertTrue(mgr.persistedMembership.containsKey("live.host"));
        assertEquals("current", mgr.groupFor("live.host").getName());
    }

    private static byte[] serialize(Object o) throws Exception {
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        try (java.io.ObjectOutputStream oos = new java.io.ObjectOutputStream(bos)) {
            oos.writeObject(o);
        }
        return bos.toByteArray();
    }

    private static QueueGroupManager deserialize(byte[] bytes) throws Exception {
        try (java.io.ObjectInputStream ois = new java.io.ObjectInputStream(
                new java.io.ByteArrayInputStream(bytes))) {
            return (QueueGroupManager) ois.readObject();
        }
    }

    private static QueueGroupManager roundTrip(QueueGroupManager mgr)
            throws Exception {
        return deserialize(serialize(mgr));
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

    // ---- anti-starvation (fairness) ----

    /**
     * Anti-starvation test: a very prolific host ("a", always ready) shares a
     * single group slot with two quieter hosts ("b" and "c", also ready). This
     * simulates what the frontier's serving loop does at each opening of the
     * shared gate: among the ready members, only the one for which
     * {@link QueueGroup#isTurn} returns true is emitted, then
     * {@link QueueGroup#advanceRotation} moves the pointer forward.
     *
     * <p>Without the intra-group round-robin, a prolific "a" (always at the head
     * of the global FIFO because it keeps re-filling) could keep grabbing the
     * shared slot and starve "b" and "c". With the rotation, each member gets a
     * roughly equal share of the gate openings.</p>
     */
    @Test
    public void testProlificHostDoesNotStarveOthers() {
        QueueGroup g = new QueueGroup("g");
        g.setMaxParallelInGroup(1);
        g.noteMember("a"); // prolific host, always ready
        g.noteMember("b"); // quieter host
        g.noteMember("c"); // quieter host

        // All three members are always ready to be served.
        Predicate<String> allReady = k -> true;
        java.util.List<String> members = g.memberKeys();

        java.util.Map<String, Integer> served =
                new java.util.HashMap<String, Integer>();

        int openings = 30; // number of times the shared gate opens
        for (int i = 0; i < openings; i++) {
            // Emulate the frontier: find which member's turn it is now and
            // serve exactly that one, then advance the rotation.
            String servedKey = null;
            for (String member : members) {
                if (g.isTurn(allReady, member)) {
                    servedKey = member;
                    break;
                }
            }
            assertNotNull(servedKey, "some ready member must win the turn");
            served.merge(servedKey, 1, Integer::sum);
            g.advanceRotation(servedKey);
        }

        // Deterministic round-robin: each of the 3 members served exactly 10
        // times out of 30 openings. The prolific "a" cannot monopolize.
        assertEquals(Integer.valueOf(10), served.get("a"));
        assertEquals(Integer.valueOf(10), served.get("b"));
        assertEquals(Integer.valueOf(10), served.get("c"));
    }

    /**
     * Anti-starvation with quiet hosts occasionally unavailable: the prolific
     * host "a" is always ready, while "b" and "c" are only intermittently
     * ready. The rotation must still hand the slot to "b"/"c" whenever they are
     * ready (they must not be permanently starved by "a"), while never blocking
     * the group when they are not.
     */
    @Test
    public void testProlificHostYieldsWheneverQuietHostsAreReady() {
        QueueGroup g = new QueueGroup("g");
        g.setMaxParallelInGroup(1);
        g.noteMember("a");
        g.noteMember("b");
        g.noteMember("c");
        java.util.List<String> members = g.memberKeys();

        java.util.Map<String, Integer> served =
                new java.util.HashMap<String, Integer>();

        int openings = 60;
        for (int i = 0; i < openings; i++) {
            final int tick = i;
            // "a" always ready; "b" ready every 3rd tick; "c" every 5th tick.
            Predicate<String> readyChecker = k -> {
                if (k.equals("a")) return true;
                if (k.equals("b")) return tick % 3 == 0;
                if (k.equals("c")) return tick % 5 == 0;
                return false;
            };
            String servedKey = null;
            for (String member : members) {
                if (readyChecker.test(member) && g.isTurn(readyChecker, member)) {
                    servedKey = member;
                    break;
                }
            }
            assertNotNull(servedKey);
            served.merge(servedKey, 1, Integer::sum);
            g.advanceRotation(servedKey);
        }

        // The quiet hosts get served whenever they were ready: they are not
        // starved by the prolific "a".
        assertTrue(served.getOrDefault("b", 0) > 0, "b must not be starved");
        assertTrue(served.getOrDefault("c", 0) > 0, "c must not be starved");
        // "a" fills the remaining openings but does not take them all.
        assertTrue(served.getOrDefault("a", 0) < openings,
                "a must not monopolize the group");
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

        assertTrue(mgr.addHostMember("g1", "b.example.com"));
        assertSame(g, mgr.groupFor("b.example.com"));

        assertTrue(mgr.removeGroup("g1"));
        assertNull(mgr.groupFor("a.example.com"));
    }

    @Test
    public void testManagerRemoveHostMember() {
        QueueGroupManager mgr = new QueueGroupManager();
        QueueGroup g = new QueueGroup("g1");
        g.setGroupMembersByHost(Arrays.asList("a.example.com", "b.example.com"));
        mgr.addGroup(g);
        assertSame(g, mgr.groupFor("a.example.com"));
        assertSame(g, mgr.groupFor("b.example.com"));
        assertEquals(2, g.memberKeys().size());

        assertTrue(mgr.removeHostMember("g1", "b.example.com"));
        assertNull(mgr.groupFor("b.example.com"));
        assertSame(g, mgr.groupFor("a.example.com"));
        // left the rotation too
        assertEquals(Arrays.asList("a.example.com"), g.memberKeys());

        // unknown member / group
        assertFalse(mgr.removeHostMember("g1", "b.example.com"));
        assertFalse(mgr.removeHostMember("nope", "a.example.com"));
        assertFalse(mgr.removeHostMember("g1", null));
    }

    @Test
    public void testManagerRemoveRegexAndSurtMember() {
        QueueGroupManager mgr = new QueueGroupManager();
        QueueGroup g = new QueueGroup("g1");
        mgr.addGroup(g);
        assertTrue(mgr.addRegexMember("g1", ".*\\.example\\.com"));
        assertTrue(mgr.addSurtMember("g1", "eu,europa,"));
        assertSame(g, mgr.groupFor("www.example.com"));
        assertSame(g, mgr.groupFor("eu,europa,commission"));

        assertTrue(mgr.removeRegexMember("g1", ".*\\.example\\.com"));
        assertFalse(mgr.removeRegexMember("g1", ".*\\.example\\.com"));
        assertNull(mgr.groupFor("www.example.com"));
        assertSame(g, mgr.groupFor("eu,europa,commission"));

        assertTrue(mgr.removeSurtMember("g1", "eu,europa,"));
        assertFalse(mgr.removeSurtMember("g1", "eu,europa,"));
        assertNull(mgr.groupFor("eu,europa,commission"));
        assertTrue(g.memberKeys().isEmpty());
    }

    @Test
    public void testForgetMemberResetsRotation() {
        QueueGroup g = new QueueGroup("g");
        g.noteMember("a");
        g.noteMember("b");
        g.advanceRotation("b"); // pointer wraps to 0
        g.advanceRotation("a"); // pointer on "b" (index 1)
        assertTrue(g.forgetMember("b"));
        assertFalse(g.forgetMember("b"));
        // pointer must not be out of range
        assertTrue(g.isTurn(k -> true, "a"));
    }

    // ---- waiting list (members parked at the concurrency ceiling) ----

    @Test
    public void testParkIsIdempotentAndUnpark() {
        QueueGroup g = new QueueGroup("g");
        assertEquals(0, g.getWaitingCount());
        assertNull(g.pollWaiting());

        g.park("a");
        g.park("a");
        assertEquals(1, g.getWaitingCount());
        assertTrue(g.isWaiting("a"));

        assertTrue(g.unpark("a"));
        assertFalse(g.unpark("a"));
        assertEquals(0, g.getWaitingCount());
        assertFalse(g.isWaiting("a"));
        g.park(null);
        assertEquals(0, g.getWaitingCount());
    }

    @Test
    public void testPollWaitingFollowsRotation() {
        QueueGroup g = new QueueGroup("g");
        g.noteMember("a");
        g.noteMember("b");
        g.noteMember("c");
        // "a" was just served: rotation pointer now on "b"
        g.advanceRotation("a");

        g.park("a");
        g.park("c");
        g.park("b");

        assertEquals("b", g.pollWaiting());
        assertEquals("c", g.pollWaiting());
        assertEquals("a", g.pollWaiting());
        assertNull(g.pollWaiting());
        assertEquals(0, g.getWaitingCount());
    }

    @Test
    public void testPollWaitingUnknownKeysServedLast() {
        QueueGroup g = new QueueGroup("g");
        g.noteMember("a");
        g.park("zzz-unknown");
        g.park("a");
        assertEquals("a", g.pollWaiting());
        assertEquals("zzz-unknown", g.pollWaiting());
        assertNull(g.pollWaiting());
    }

    @Test
    public void testDrainWaiting() {
        QueueGroup g = new QueueGroup("g");
        assertTrue(g.drainWaiting().isEmpty());
        g.park("a");
        g.park("b");
        assertEquals(Arrays.asList("a", "b"), g.drainWaiting());
        assertEquals(0, g.getWaitingCount());
        assertTrue(g.drainWaiting().isEmpty());
    }

    @Test
    public void testParkedQueueHandedBackAfterRelease() {
        // simulate the frontier's gate: one slot, no cool-down
        QueueGroup g = new QueueGroup("g");
        g.setMaxParallelInGroup(1);
        g.setGroupMinDelayMs(0);
        g.noteMember("a");
        g.noteMember("b");
        long now = 1_000_000L;

        assertTrue(g.canProceed(now));
        g.acquire();
        // second member arrives while slot busy: gate closed, wakeTime in past
        assertFalse(g.canProceed(now));
        assertTrue(g.getWakeTime() <= now);
        g.park("b");

        // slot released: frontier pops the parked member
        g.release(now, 0);
        assertEquals("b", g.pollWaiting());
        assertTrue(g.canProceed(now));
        assertNull(g.pollWaiting());
    }
}
