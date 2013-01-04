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
import java.io.PrintWriter;
import java.io.Serializable;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.archive.crawler.framework.Frontier;
import org.archive.crawler.frontier.precedence.PrecedenceProvider;
import org.archive.crawler.frontier.precedence.SimplePrecedenceProvider;
import org.archive.modules.CrawlURI;
import org.archive.modules.fetcher.FetchStats;
import org.archive.modules.fetcher.FetchStats.Stage;
import org.archive.util.ArchiveUtils;
import org.archive.util.IdentityCacheable;
import org.archive.util.ObjectIdentityCache;
import org.archive.util.ReportUtils;
import org.archive.util.Reporter;

/**
 * A single queue of related URIs to visit, grouped by a classKey
 * (typically "hostname:port" or similar) 
 * 
 * @author gojomo
 * @author Christian Kohlschuetter 
 */
public abstract class WorkQueue implements Frontier.FrontierGroup,
        Serializable, Reporter, Delayed, IdentityCacheable {
    private static final long serialVersionUID = -3199666138837266341L;
    private static final Logger logger =
        Logger.getLogger(WorkQueue.class.getName());
    
    /** The classKey */
    protected final String classKey;

    /** whether queue is active (ready/in-process/snoozed) or on a waiting queue */
    protected boolean active = false;

    /** Total number of stored items */
    protected long count = 0;

    /** Total number of items ever enqueued */
    protected long enqueueCount = 0;
    
    /** Whether queue is already in lifecycle stage */
    protected boolean isManaged = false;

    /** Time to wake, if snoozed */
    protected long wakeTime = 0;

    /** assigned precedence */
    protected PrecedenceProvider precedenceProvider = new SimplePrecedenceProvider(1);
            
    /** Per-session 'budget' controlling activity duration */
    protected int sessionBudget = 0;

    /** Cost of the last item to be charged against queue */
    protected int lastCost = 0;

    /** Total number of items charged against queue; with totalExpenditure
     * can be used to calculate 'average cost'. */
    protected long costCount = 0;

    /** Running tally of total expenditures on this queue */
    protected long totalExpenditure = 0;

    /** Record of expenditures at last activation (session start) */
    protected long expenditureAtLastActivation = 0;
    
    /** Total to spend on this queue over its lifetime */
    protected long totalBudget = 0;

    /** The next item to be returned */
    transient protected CrawlURI peekItem = null;

    /** Last URI enqueued */
    protected String lastQueued;

    /** Last URI peeked */
    protected String lastPeeked;

    /** time of last dequeue (disposition of some URI) **/ 
    protected long lastDequeueTime;
    
    /** count of errors encountered */
    protected long errorCount = 0;
    
    /** Substats for all CrawlURIs in this group */
    protected FetchStats substats = new FetchStats();

    protected boolean retired;

    public WorkQueue(final String pClassKey) {
        this.classKey = pClassKey;
    }

    /**
     * Delete URIs matching the given pattern from this queue. 
     * @param frontier
     * @param match
     * @return count of deleted URIs
     */
    public synchronized long deleteMatching(final WorkQueueFrontier frontier, String match) {
        try {
            final long deleteCount = deleteMatchingFromQueue(frontier, match);
            this.count -= deleteCount;
            return deleteCount;
        } catch (IOException e) {
            //FIXME better exception handling
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    /**
     * Add the given CrawlURI, noting its addition in running count. (It
     * should not already be present.)
     * 
     * @param frontier Work queues manager.
     * @param curi CrawlURI to insert.
     */
    protected synchronized long enqueue(final WorkQueueFrontier frontier,
        CrawlURI curi) {
        try {
            insert(frontier, curi, false);
        } catch (IOException e) {
            //FIXME better exception handling
            e.printStackTrace();
            throw new RuntimeException(e);
        }
        count++;
        enqueueCount++;
        return count;
    }

    /**
     * Return the topmost queue item -- and remember it,
     * such that even later higher-priority inserts don't
     * change it. 
     * 
     * TODO: evaluate if this is really necessary
     * @param frontier Work queues manager
     * 
     * @return topmost queue item, or null
     */
    public synchronized CrawlURI peek(final WorkQueueFrontier frontier) {
        if(peekItem == null && count > 0) {
            try {
                peekItem = peekItem(frontier);
            } catch (IOException e) {
                //FIXME better exception handling
                logger.log(Level.SEVERE,"peek failure",e);
                e.printStackTrace();
                // throw new RuntimeException(e);
            }
            if(peekItem != null) {
                lastPeeked = peekItem.toString();
            }
        }
        return peekItem;
    }

    /**
     * Remove the peekItem from the queue and adjusts the count.
     * 
     * @param frontier  Work queues manager.
     */
    protected synchronized void dequeue(final WorkQueueFrontier frontier, CrawlURI expected) {
        try {
            deleteItem(frontier, peekItem);
        } catch (IOException e) {
            //FIXME better exception handling
            e.printStackTrace();
            throw new RuntimeException(e);
        }
        unpeek(expected);
        count--;
        lastDequeueTime = System.currentTimeMillis();
    }

    /**
     * Set the session 'activity budget' to the given value. Automatically
     * reset continually as new CrawlURIs are enqueued; a direct change
     * here by operator will not persist. Instead, change the 'balanceReplenishAmount' 
     * (or overlay its value with a URI/queue-specific value) to affect this
     * value.
     * 
     * @param balance to use
     */
    protected void setSessionBudget(int budget) {
        this.sessionBudget = budget;
    }

    /**
     * Return current session 'activity budget balance' 
     * 
     * @return session balance
     */
    public int getSessionBudget() {
        return this.sessionBudget;
    }

    /**
     * Begin an 'active' session, which begins when a queue first offers a
     * URI for crawling, and continues until it is deactivated (for example, 
     * for session-budget reasons). 
     */
    public synchronized void considerActive() {
        if(active) {
            return; 
        }
        active=true; 
        expenditureAtLastActivation = totalExpenditure;
    }
    
    /**
     * Set the total expenditure level allowable before queue is 
     * considered inherently 'over-budget'. 
     * 
     * Automatically reset continually as new CrawlURIs are enqueued; a direct change
     * here by operator will not persist. Instead, change the 'queueTotalBudget' 
     * (or overlay its value with a URI/queue-specific value) to affect this
     * value.
     * 
     * @param budget
     */
    protected void setTotalBudget(long budget) {
        this.totalBudget = budget;
    }

    /**
     * Check whether queue has temporarily (session) exceeded its budget.
     * 
     * @return true if queue is over either of its set budget(s)
     */
    public boolean isOverSessionBudget() {
        // check whether session budget exceeded
        // or totalExpenditure exceeds totalBudget
        return (sessionBudget > 0 && (totalExpenditure - expenditureAtLastActivation) > sessionBudget);
    }

    /**
     * Check whether queue has permanently (total) exceeded its budget.
     * 
     * @return true if queue is over either of its set budget(s)
     */
    public boolean isOverTotalBudget() {
        // check whether session budget exceeded
        // or totalExpenditure exceeds totalBudget
        return (this.totalBudget >= 0 && this.totalExpenditure >= this.totalBudget);
    }
    
    /**
     * Return the tally of all expenditures on this queue
     * 
     * @return total amount expended on this queue
     */
    public long getTotalExpenditure() {
        return totalExpenditure;
    }

    /**
     * Decrease the internal running budget by the given amount. (Use
     * negative value to effect 'refund'/undo.)
     * 
     * @param amount tp decrement
     * @return updated budget value
     */
    public void expend(int amount) {
        this.totalExpenditure = this.totalExpenditure + amount;
        if(amount >= 0) {
            this.lastCost = amount;
            this.costCount++;
        } else {
            this.costCount--; 
        }
    }

    
    /**
     * Note an error and assess an extra penalty. 
     * @param penalty additional amount to deduct
     */
    public void noteError(int penalty) {
        this.totalExpenditure = this.totalExpenditure + penalty;
        errorCount++;
    }
    
    /**
     * @param l
     */
    public void setWakeTime(long l) {
        wakeTime = l;
    }

    /**
     * @return wakeTime
     */
    public long getWakeTime() {
        return wakeTime;
    }

    /**
     * @return classKey, the 'identifier', for this queue.
     */
    public String getClassKey() {
        return this.classKey;
    }

    /**
     * Forgive the peek, allowing a subsequent peek to 
     * return a different item. 
     * 
     */
    public synchronized void unpeek(CrawlURI expected) {
        assert expected == peekItem : "unexpected peekItem";
        peekItem = null;
    }

    /* (non-Javadoc)
     * @see java.util.concurrent.Delayed#getDelay(java.util.concurrent.TimeUnit)
     */
    public long getDelay(TimeUnit unit) {
        return unit.convert(
                getWakeTime()-System.currentTimeMillis(),
                TimeUnit.MILLISECONDS);
    }

    public final int compareTo(Delayed obj) {
        if(this == obj) {
            return 0; // for exact identity only
        }
        WorkQueue other = (WorkQueue) obj;
        if(getWakeTime() > other.getWakeTime()) {
            return 1;
        }
        if(getWakeTime() < other.getWakeTime()) {
            return -1;
        }
        // at this point, the ordering is arbitrary, but still
        // must be consistent/stable over time
        return this.classKey.compareTo(other.getClassKey());
    }

    /**
     * Update the given CrawlURI, which should already be present. (This
     * is not checked.) Equivalent to an enqueue without affecting the count.
     * 
     * @param frontier Work queues manager.
     * @param curi CrawlURI to update.
     */
    protected void update(final WorkQueueFrontier frontier, CrawlURI curi) {
        try {
            insert(frontier, curi, true);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Count of URIs in this queue. Only precise if called within frontier's
     * manager thread. 
     * 
     * @return Returns the count.
     */
    public synchronized long getCount() {
        return this.count;
    }

    /**
     * Insert the given curi, whether it is already present or not. 
     * @param frontier WorkQueueFrontier.
     * @param curi CrawlURI to insert.
     * @throws IOException
     */
    private void insert(final WorkQueueFrontier frontier, CrawlURI curi,
            boolean overwriteIfPresent)
        throws IOException {
        insertItem(frontier, curi, overwriteIfPresent);
        lastQueued = curi.toString();
    }

    /**
     * Insert the given curi, whether it is already present or not.
     * Hook for subclasses. 
     * 
     * @param frontier WorkQueueFrontier.
     * @param curi CrawlURI to insert.
     * @throws IOException  if there was a problem while inserting the item
     */
    protected abstract void insertItem(final WorkQueueFrontier frontier,
        CrawlURI curi, boolean overwriteIfPresent) throws IOException;

    /**
     * Delete URIs matching the given pattern from this queue. 
     * @param frontier WorkQueues manager.
     * @param match  the pattern to match
     * @return count of deleted URIs
     * @throws IOException  if there was a problem while deleting
     */
    protected abstract long deleteMatchingFromQueue(
        final WorkQueueFrontier frontier, final String match)
        throws IOException;

    /**
     * Removes the given item from the queue.
     * 
     * This is only used to remove the first item in the queue,
     * so it is not necessary to implement a random-access queue.
     * 
     * @param frontier  Work queues manager.
     * @throws IOException  if there was a problem while deleting the item
     */
    protected abstract void deleteItem(final WorkQueueFrontier frontier,
        final CrawlURI item) throws IOException;

    /**
     * Returns first item from queue (does not delete)
     * 
     * @return The peeked item, or null
     * @throws IOException  if there was a problem while peeking
     */
    protected abstract CrawlURI peekItem(final WorkQueueFrontier frontier)
        throws IOException;

    // 
    // Reporter
    //

    @Override
    public synchronized Map<String, Object> shortReportMap() {
        Map<String,Object> map = new LinkedHashMap<String, Object>();

        map.put("queueName", classKey);
        map.put("precedence", getPrecedence());
        map.put("itemCount", count);
        map.put("enqueueCount", enqueueCount);
        map.put("sessionBalance", getSessionBalance());
        map.put("lastCost", lastCost);
        map.put("averageCost", (double) totalExpenditure / costCount);
        if (lastDequeueTime != 0) {
            map.put("lastDequeueTime", new Date(lastDequeueTime));
        } else {
            map.put("lastDequeueTime", null);
        }
        if (wakeTime != 0) {
            map.put("lastDequeueTime", new Date(wakeTime));
        } else {
            map.put("lastDequeueTime", null);
        }
        map.put("totalExpenditure", totalExpenditure);
        map.put("totalBudget", totalBudget);
        map.put("errorCount", errorCount);
        map.put("lastPeeked", lastPeeked);
        map.put("lastQueued", lastQueued);

        return map;
    }

    protected long getSessionBalance() {
        return sessionBudget - (totalExpenditure-expenditureAtLastActivation);
    }

    @Override
    public synchronized void shortReportLineTo(PrintWriter writer) {
        // queue name
        writer.print(classKey);
        writer.print(" ");
        // precedence
        writer.print(getPrecedence());
        writer.print(" ");
        // count of items
        writer.print(Long.toString(count));
        writer.print(" ");
        // enqueue count
        writer.print(Long.toString(enqueueCount));
        writer.print(" ");
        writer.print(getSessionBalance());
        writer.print(" ");
        writer.print(lastCost);
        writer.print("(");
        writer.print(ArchiveUtils.doubleToString(
                    ((double) totalExpenditure / costCount), 1));
        writer.print(")");
        writer.print(" ");
        // last dequeue time, if any, or '-'
        if (lastDequeueTime != 0) {
            writer.print(ArchiveUtils.getLog17Date(lastDequeueTime));
        } else {
            writer.print("-");
        }
        writer.print(" ");
        // wake time if snoozed, or '-'
        if (wakeTime != 0) {
            writer.print(ArchiveUtils.formatMillisecondsToConventional(wakeTime - System.currentTimeMillis()));
        } else {
            writer.print("-");
        }
        writer.print(" ");
        writer.print(Long.toString(totalExpenditure));
        writer.print("/");
        writer.print(Long.toString(totalBudget));
        writer.print(" ");
        writer.print(Long.toString(errorCount));
        writer.print(" ");
        writer.print(lastPeeked);
        writer.print(" ");
        writer.print(lastQueued);
        writer.print("\n");
    }

    @Override
    public String shortReportLegend() {
        return "queue precedence currentSize totalEnqueues sessionBalance " +
                "lastCost (averageCost) lastDequeueTime wakeTime " +
                "totalSpend/totalBudget errorCount lastPeekUri lastQueuedUri";
    }
    
    public String shortReportLine() {
        return ReportUtils.shortReportLine(this);
    }
    
    /**
     * @param writer
     * @throws IOException
     */
    @Override
    public synchronized void reportTo(PrintWriter writer) {
        // name is ignored: only one kind of report for now
        writer.print("Queue ");
        writer.print(classKey);
        writer.print(" (p");
        writer.print(getPrecedence());
        writer.print(")\n");
        writer.print("  ");
        writer.print(Long.toString(count));
        writer.print(" items");
        if (wakeTime != 0) {
            writer.print("\n   wakes in: "+ArchiveUtils.formatMillisecondsToConventional(wakeTime - System.currentTimeMillis()));
        }
        writer.print("\n    last enqueued: ");
        writer.print(lastQueued);
        writer.print("\n      last peeked: ");
        writer.print(lastPeeked);
        writer.print("\n");
        writer.print("   total expended: ");
        writer.print(Long.toString(totalExpenditure));
        writer.print(" (total budget: ");
        writer.print(Long.toString(totalBudget));
        writer.print(")\n");
        writer.print("   active balance: ");
        writer.print(getSessionBalance());
        writer.print("\n   last(avg) cost: ");
        writer.print(lastCost);
        writer.print("(");
        writer.print(ArchiveUtils.doubleToString(
                    ((double) totalExpenditure / costCount), 1));
        writer.print(")\n   ");
        writer.print(getSubstats().shortReportLegend());
        writer.print("\n   ");
        writer.print(getSubstats().shortReportLine());
        writer.print("\n   ");
        writer.print(getPrecedenceProvider().shortReportLegend());
        writer.print("\n   ");
        writer.print(getPrecedenceProvider().shortReportLine());
        writer.print("\n\n");
    }
    
    public FetchStats getSubstats() {
        return substats;
    }

    /**
     * Set the retired status of this queue.
     * 
     * @param b new value for retired status
     */
    protected void setRetired(boolean b) {
        this.retired = b;
    }
    
    public boolean isRetired() {
        return retired;
    }

    /**
     * @return the precedenceProvider
     */
    public PrecedenceProvider getPrecedenceProvider() {
        return precedenceProvider;
    }

    /**
     * @param precedenceProvider the precedenceProvider to set
     */
    public void setPrecedenceProvider(PrecedenceProvider precedenceProvider) {
        this.precedenceProvider = precedenceProvider;
    }
    
    /**
     * @return the precedence
     */
    public int getPrecedence() {
        return precedenceProvider.getPrecedence();
    }

    /* (non-Javadoc)
     * @see org.archive.modules.fetcher.FetchStats.HasFetchStats#tally(org.archive.modules.CrawlURI, org.archive.modules.fetcher.FetchStats.Stage)
     */
    public void tally(CrawlURI curi, Stage stage) {
        substats.tally(curi, stage);
        precedenceProvider.tally(curi, stage);
    }

    /**
     * Update queue state to recognize it has been sent to one of the
     * inactive (by-precedence) queues, waiting for a turn. 
     */
    public synchronized void noteDeactivated() {
        active = false;
        isManaged = true; 
        makeDirty();
    }
    
    /**
     * Update queue state to recognize it has been completely exhausted,
     * and is no longer on any of the ready/inactive queues-of-queues
     */
    public synchronized void noteExhausted() {
        active = false;
        isManaged = false; 
        makeDirty();
    }

    /**
     * Whether the queue is already in a lifecycle stage --
     * such as ready, in-progress, snoozed -- and thus should
     * not be redundantly inserted to readyClassQueues
     * 
     * @return isManaged
     */
    public boolean isManaged() {
        return isManaged;
    }
    
    /* (non-Javadoc)
     * @see java.lang.Object#toString()
     */
    public String toString() {
        return super.toString()+"("+getClassKey()+")";
    }

    
    //
    // IdentityCacheable support
    //
    transient private ObjectIdentityCache<?> cache;
    @Override
    public String getKey() {
        return getClassKey();
    }

    @Override
    public void makeDirty() {
        cache.dirtyKey(getKey());
    }

    @Override
    public void setIdentityCache(ObjectIdentityCache<?> cache) {
        this.cache = cache; 
    } 
}
