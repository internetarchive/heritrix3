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

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;

import org.archive.modules.CrawlURI;

/**
 * Optional bean holding the configured {@link QueueGroup}s and resolving a
 * queue's {@code classKey} to the group (if any) it belongs to.
 *
 * <p>This bean is entirely opt-in: if it is not declared in the crawl beans,
 * {@link WorkQueueFrontier} injects {@code null} and the frontier behaves
 * exactly as before. If it is declared but has no groups, every lookup returns
 * {@code null} as well.</p>
 *
 * <p>Lookups are cached per classKey. A small CRUD API is provided so groups
 * can also be adjusted at runtime from the Heritrix scripting console; any
 * mutation clears the cache.</p>
 */
public class QueueGroupManager implements Serializable {
    private static final long serialVersionUID = 1L;

    /** Sentinel cached for classKeys that belong to no group. */
    private static final QueueGroup NONE = new QueueGroup("\u0000none");

    protected List<QueueGroup> groups = new CopyOnWriteArrayList<QueueGroup>();

    /** classKey -> group (or {@link #NONE}); rebuilt lazily, transient. */
    protected transient ConcurrentHashMap<String, QueueGroup> cache =
            new ConcurrentHashMap<String, QueueGroup>();

    /**
     * Durable classKey -&gt; group <i>name</i> mapping. Unlike {@link #cache}
     * (which holds live {@link QueueGroup} references and is transient), this
     * map is serialized with the checkpoint so that DecideRule-based
     * membership (resolved only from a real {@link CrawlURI} at enqueue time)
     * survives a restore: on deserialization it is re-bound to the
     * Spring-reconstructed {@link QueueGroup} instances by name, repopulating
     * both {@link #cache} and each group's discovered members. The {@link #NONE}
     * sentinel is never stored here (only positive matches are persisted).
     */
    protected ConcurrentHashMap<String, String> persistedMembership =
            new ConcurrentHashMap<String, String>();

    public QueueGroupManager() {
    }

    public List<QueueGroup> getGroups() {
        return groups;
    }

    public void setGroups(List<QueueGroup> groups) {
        this.groups = new CopyOnWriteArrayList<QueueGroup>(
                groups != null ? groups : new ArrayList<QueueGroup>());
        clearCache();
    }

    protected ConcurrentHashMap<String, QueueGroup> cache() {
        if (cache == null) {
            cache = new ConcurrentHashMap<String, QueueGroup>();
        }
        return cache;
    }

    public void clearCache() {
        cache().clear();
        if (persistedMembership != null) {
            persistedMembership.clear();
        }
    }

    protected ConcurrentHashMap<String, String> persistedMembership() {
        if (persistedMembership == null) {
            persistedMembership = new ConcurrentHashMap<String, String>();
        }
        return persistedMembership;
    }

    /** @return the group with the given name among {@link #groups}, or null. */
    protected QueueGroup findGroupByName(String name) {
        if (name == null || groups == null) {
            return null;
        }
        for (QueueGroup g : groups) {
            if (name.equals(g.getName())) {
                return g;
            }
        }
        return null;
    }

    /**
     * Re-bind the persisted {@code classKey -> group name} mapping to the live
     * {@link QueueGroup} instances after deserialization, rebuilding the
     * in-memory {@link #cache} and restoring each group's discovered members so
     * the round-robin rotation starts complete. Entries whose group name no
     * longer exists (group renamed/removed in the config since the checkpoint)
     * are dropped defensively.
     */
    protected void rebindPersistedMembership() {
        if (persistedMembership == null || persistedMembership.isEmpty()) {
            return;
        }
        for (Map.Entry<String, String> e : persistedMembership.entrySet()) {
            String classKey = e.getKey();
            QueueGroup g = findGroupByName(e.getValue());
            if (g != null) {
                cache().put(classKey, g);
                g.noteMember(classKey);
            } else {
                // stale entry: group no longer configured, drop it
                persistedMembership.remove(classKey);
            }
        }
    }

    private void readObject(ObjectInputStream in)
            throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        cache = new ConcurrentHashMap<String, QueueGroup>();
        if (persistedMembership == null) {
            persistedMembership = new ConcurrentHashMap<String, String>();
        }
        rebindPersistedMembership();
    }

    /**
     * @return the group that the given classKey belongs to, or {@code null} if
     *         none. The classKey is registered as a concrete member of its
     *         group so it takes part in the round-robin rotation.
     */
    public QueueGroup groupFor(String classKey) {
        if (classKey == null || groups == null || groups.isEmpty()) {
            return null;
        }
        QueueGroup cached = cache().get(classKey);
        if (cached != null) {
            return cached == NONE ? null : cached;
        }
        QueueGroup found = null;
        for (QueueGroup g : groups) {
            if (g.matches(classKey)) {
                found = g;
                break;
            }
        }
        cache().put(classKey, found == null ? NONE : found);
        if (found != null) {
            found.noteMember(classKey);
            persistedMembership().put(classKey, found.getName());
        }
        return found;
    }

    /**
     * Resolve the group of a URI using the real {@link CrawlURI}, so that
     * {@link QueueGroup#groupMembersByDecideRule DecideRule-based} membership
     * (which needs a {@code CrawlURI}, not a bare classKey) can be evaluated.
     *
     * <p>The resolution is cached per {@code classKey} (just like
     * {@link #groupFor(String)}), so subsequent classKey-only lookups keep
     * returning the same group. This method is meant to be called once, when a
     * URI is first enqueued and its classKey is known. A positive match
     * overrides a previously-cached {@link #NONE} (e.g. if a classKey-only
     * lookup happened first and found nothing).</p>
     *
     * @return the group the URI belongs to, or {@code null} if none.
     */
    public QueueGroup groupFor(CrawlURI curi) {
        if (curi == null || groups == null || groups.isEmpty()) {
            return null;
        }
        String classKey = curi.getClassKey();
        if (classKey == null) {
            return null;
        }
        QueueGroup cached = cache().get(classKey);
        if (cached != null && cached != NONE) {
            return cached;
        }
        QueueGroup found = null;
        for (QueueGroup g : groups) {
            if (g.matches(curi)) {
                found = g;
                break;
            }
        }
        if (found != null) {
            // override any previously-cached NONE with the URI-based match
            cache().put(classKey, found);
            found.noteMember(classKey);
            persistedMembership().put(classKey, found.getName());
            return found;
        }
        // no URI-based match: fall back to the classKey-only resolution, which
        // also populates the cache (with the group or NONE).
        return groupFor(classKey);
    }

    // ---- runtime CRUD (scripting console) ----

    public synchronized QueueGroup addGroup(QueueGroup group) {
        if (group != null) {
            groups.add(group);
            clearCache();
        }
        return group;
    }

    public synchronized boolean removeGroup(String name) {
        boolean removed = false;
        for (QueueGroup g : new ArrayList<QueueGroup>(groups)) {
            if (g.getName() != null && g.getName().equals(name)) {
                groups.remove(g);
                removed = true;
            }
        }
        if (removed) {
            clearCache();
        }
        return removed;
    }
    
    public synchronized boolean addHostMember(String groupName, String member) {
        for (QueueGroup g : groups) {
            if (g.getName() != null && g.getName().equals(groupName)) {
                g.getGroupMembersByHost().add(member);
                clearCache();
                return true;
            }
        }
        return false;
    }

    public synchronized boolean addRegexMember(String groupName, String member) {
        for (QueueGroup g : groups) {
            if (g.getName() != null && g.getName().equals(groupName)) {
                g.getGroupMembersByRegex().add(Pattern.compile(member));
                clearCache();
                return true;
            }
        }
        return false;
    }

    public synchronized boolean addSurtMember(String groupName, String member) {
        for (QueueGroup g : groups) {
            if (g.getName() != null && g.getName().equals(groupName)) {
                g.getGroupMembersBySurt().add(member);
                clearCache();
                return true;
            }
        }
        return false;
    }
}
