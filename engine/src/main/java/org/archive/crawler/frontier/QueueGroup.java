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

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * A "queue group": a set of otherwise-independent per-host work queues
 * that a crawler should treat as sharing a single web server for the
 * purposes of politeness and scheduling (issue #754, "option 1").
 *
 * <p>The URLs are <b>not</b> merged into a single queue: each host keeps its
 * own {@link WorkQueue} (its own {@code classKey}). The group only adds, on
 * top of the existing flat scheduler:</p>
 * <ul>
 *   <li>a <b>shared politeness gate</b>: at most {@link #maxParallelInGroup}
 *       member queues may be "in process" simultaneously, and a shared
 *       cool-down of at least {@link #groupMinDelayMs} ms is enforced between
 *       fetches of the group;</li>
 *   <li>a <b>round-robin rotation</b> among the group's member queues, so the
 *       crawler alternates between hosts (european-union.europa.eu, then
 *       commission.europa.eu, ...) instead of draining one host first. Members
 *       that are not currently ready (snoozed, empty) are simply skipped.</li>
 * </ul>
 *
 * <p>With {@code maxParallelInGroup=1} and a shared cool-down, the whole group
 * behaves as a single scheduling unit toward the server (the "17/50" balance
 * described in the ticket): its member hosts share a single fetch slot.</p>
 *
 * <p>All rotation/gate state is transient and simply reset on
 * restart/checkpoint.</p>
 *
 * <p>Members are matched against a queue's {@code classKey}. A member can be
 * declared in three separate, dedicated lists:</p>
 * <ul>
 *   <li><b>{@link #groupMembersByHost}</b> (e.g. {@code european-union.europa.eu}):
 *       exact host match;</li>
 *   <li><b>{@link #groupMembersByRegex}</b> (e.g. {@code .*\.europa\.eu}): full
 *       match on the host part of the classKey;</li>
 *   <li><b>{@link #groupMembersBySurt}</b> (e.g. {@code http://(eu,europa,}):
 *       classKey starts with the given SURT prefix.</li>
 * </ul>
 */
public class QueueGroup implements Serializable {
    private static final long serialVersionUID = 1L;

    /** Human-readable, unique name of the group (e.g. "europa_eu"). */
    protected String name;

    /** Exact host member matchers (see class javadoc). */
    protected List<String> groupMembersByHost = new ArrayList<String>();

    /** Regex member matchers, matched against the host part of the classKey. */
    protected List<Pattern> groupMembersByRegex = new ArrayList<Pattern>();

    /** SURT-prefix member matchers, matched against the classKey. */
    protected List<String> groupMembersBySurt = new ArrayList<String>();

    /** Max member queues collected simultaneously (shared concurrency). */
    protected int maxParallelInGroup = 1;

    /** Minimal shared delay (ms) between two fetches of the group. */
    protected long groupMinDelayMs = 0;

    // ---- transient scheduling/politeness state ----

    /** Number of member queues currently "in process". */
    protected transient int activeCount = 0;

    /** Shared cool-down: no fetch before this epoch-ms. */
    protected transient long wakeTime = 0;

    /** Rotation pointer for round-robin over members. */
    protected transient int rotationIndex = 0;

    /**
     * Concrete member queue keys discovered at runtime, in first-seen order.
     * The configured member lists are only <i>matchers</i>; the actual
     * per-host classKeys that fall into this group are collected here to drive
     * the round-robin rotation. Transient: rebuilt as queues are encountered.
     */
    protected transient java.util.LinkedHashSet<String> discoveredMembers =
            new java.util.LinkedHashSet<String>();

    public QueueGroup() {
    }

    public QueueGroup(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<String> getGroupMembersByHost() {
        return groupMembersByHost;
    }

    public void setGroupMembersByHost(List<String> groupMembersByHost) {
        this.groupMembersByHost = (groupMembersByHost != null)
                ? new ArrayList<String>(groupMembersByHost)
                : new ArrayList<String>();
    }

    public List<Pattern> getGroupMembersByRegex() {
        return groupMembersByRegex;
    }

    public void setGroupMembersByRegex(List<Pattern> groupMembersByRegex) {
        this.groupMembersByRegex = (groupMembersByRegex != null)
                ? new ArrayList<Pattern>(groupMembersByRegex)
                : new ArrayList<Pattern>();
    }

    public List<String> getGroupMembersBySurt() {
        return groupMembersBySurt;
    }

    public void setGroupMembersBySurt(List<String> groupMembersBySurt) {
        this.groupMembersBySurt = (groupMembersBySurt != null)
                ? new ArrayList<String>(groupMembersBySurt)
                : new ArrayList<String>();
    }

    public int getMaxParallelInGroup() {
        return maxParallelInGroup;
    }

    public void setMaxParallelInGroup(int maxParallelInGroup) {
        this.maxParallelInGroup = maxParallelInGroup;
    }

    public long getGroupMinDelayMs() {
        return groupMinDelayMs;
    }

    public void setGroupMinDelayMs(long groupMinDelayMs) {
        this.groupMinDelayMs = groupMinDelayMs;
    }

    public long getWakeTime() {
        return wakeTime;
    }

    public int getActiveCount() {
        return activeCount;
    }

    // ---- membership matching ----

    /**
     * @return the host part of a classKey, i.e. the substring before any
     *         {@code '#'} (host/port separator) or {@code '+'} (subqueue
     *         separator).
     */
    protected static String hostPart(String classKey) {
        if (classKey == null) {
            return null;
        }
        int hash = classKey.indexOf('#');
        int plus = classKey.indexOf('+');
        int cut = -1;
        if (hash >= 0 && plus >= 0) {
            cut = Math.min(hash, plus);
        } else if (hash >= 0) {
            cut = hash;
        } else if (plus >= 0) {
            cut = plus;
        }
        return (cut >= 0) ? classKey.substring(0, cut) : classKey;
    }

    /**
     * @return true if the given exact-host value matches the given classKey.
     */
    protected boolean matchesHost(String value, String classKey) {
        if (value == null || value.isEmpty() || classKey == null) {
            return false;
        }
        if (classKey.equals(value)) {
            return true;
        }
        // hostname policy suffixes: host#port and host+subqueue
        if (classKey.startsWith(value + "#")
                || classKey.startsWith(value + "+")) {
            return true;
        }
        
        return false;
    }

    /**
     * @return true if the given regex pattern matches the host part of classKey.
     */
    protected boolean matchesRegex(Pattern pattern, String classKey) {
        if (pattern == null || classKey == null) {
            return false;
        }
        String host = hostPart(classKey);
        return host != null && pattern.matcher(host).matches();
    }

    /**
     * @return true if the given SURT prefix is a prefix of the classKey.
     */
    protected boolean matchesSurt(String value, String classKey) {
        if (value == null || value.isEmpty() || classKey == null) {
            return false;
        }
        return classKey.startsWith(value);
    }

    /**
     * @return true if any member of this group matches the given classKey.
     */
    public boolean matches(String classKey) {
        if (classKey == null) {
            return false;
        }
        if (groupMembersByHost != null) {
            for (String value : groupMembersByHost) {
                if (matchesHost(value, classKey)) {
                    return true;
                }
            }
        }
        if (groupMembersByRegex != null) {
            for (Pattern pattern : groupMembersByRegex) {
                if (matchesRegex(pattern, classKey)) {
                    return true;
                }
            }
        }
        if (groupMembersBySurt != null) {
            for (String value : groupMembersBySurt) {
                if (matchesSurt(value, classKey)) {
                    return true;
                }
            }
        }
        return false;
    }

    // ---- shared politeness gate ----

    /**
     * @return true if the group can currently emit a URI: neither at its
     *         concurrency ceiling nor in a shared cool-down.
     */
    public synchronized boolean canProceed(long now) {
        return activeCount < maxParallelInGroup && now >= wakeTime;
    }

    /**
     * Reserve a shared fetch slot (call only when {@link #canProceed(long)} is
     * true, under the same lock).
     */
    public synchronized void acquire() {
        activeCount++;
    }

    /**
     * Release a previously-acquired slot and arm the shared cool-down. The
     * cool-down is the greater of the URI's own politeness delay and the
     * group's configured minimum, so all members are spaced like a single
     * visitor.
     *
     * @param now      current epoch-ms
     * @param delay_ms the per-URI politeness delay just computed
     */
    public synchronized void release(long now, long delay_ms) {
        if (activeCount > 0) {
            activeCount--;
        }
        long candidate = now + Math.max(delay_ms, groupMinDelayMs);
        if (candidate > wakeTime) {
            wakeTime = candidate;
        }
    }

    // ---- round-robin rotation over members ----

    /**
     * Register a concrete member queue key as belonging to this group, so it
     * participates in the round-robin rotation. Idempotent.
     */
    public synchronized void noteMember(String classKey) {
        if (classKey != null && discoveredMembers != null) {
            discoveredMembers.add(classKey);
        }
    }

    /**
     * @return a stable snapshot of the concrete member keys seen so far.
     */
    public synchronized List<String> memberKeys() {
        return new ArrayList<String>(discoveredMembers);
    }

    /**
     * Round-robin turn test over the concrete member keys discovered so far.
     * Starting from the rotation pointer, the first member reported ready by
     * {@code readyChecker} wins the current turn; members that are not ready
     * are skipped, so a silent/snoozed/empty host never blocks the group.
     *
     * @param readyChecker tells whether a member key is currently ready
     * @param classKey     the candidate member key being considered
     * @return true if {@code classKey} is the next ready member in rotation
     */
    public synchronized boolean isTurn(
            java.util.function.Predicate<String> readyChecker, String classKey) {
        if (discoveredMembers == null || discoveredMembers.isEmpty()) {
            return true;
        }
        List<String> ordered = new ArrayList<String>(discoveredMembers);
        int n = ordered.size();
        if (rotationIndex >= n) {
            rotationIndex = 0;
        }
        // scan from rotationIndex for the first ready member
        for (int i = 0; i < n; i++) {
            String candidate = ordered.get((rotationIndex + i) % n);
            if (candidate.equals(classKey)) {
                // reached the candidate before finding any other ready member
                return true;
            }
            if (readyChecker.test(candidate)) {
                // another member is ahead of classKey in rotation and ready
                return false;
            }
        }
        // classKey not among discovered members (shouldn't happen): allow it
        return true;
    }

    /**
     * Advance the rotation pointer past the member that was just served.
     *
     * @param servedKey the member key that has just been emitted
     */
    public synchronized void advanceRotation(String servedKey) {
        if (discoveredMembers == null || discoveredMembers.isEmpty()) {
            return;
        }
        List<String> ordered = new ArrayList<String>(discoveredMembers);
        int idx = ordered.indexOf(servedKey);
        if (idx >= 0) {
            rotationIndex = (idx + 1) % ordered.size();
        }
    }
}
