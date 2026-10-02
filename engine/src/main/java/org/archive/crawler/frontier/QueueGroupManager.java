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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;

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
        }
        return found;
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

    public synchronized boolean addMember(String groupName, String member) {
        return addHostMember(groupName, member);
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
