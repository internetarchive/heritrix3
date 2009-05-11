/* SuccessCountsQueuePrecedencePolicy
*
* $Id: CostAssignmentPolicy.java 4981 2007-03-12 07:06:01Z paul_jack $
*
* Created on Nov 17, 2007
*
* Copyright (C) 2007 Internet Archive.
*
* This file is part of the Heritrix web crawler (crawler.archive.org).
*
* Heritrix is free software; you can redistribute it and/or modify
* it under the terms of the GNU Lesser Public License as published by
* the Free Software Foundation; either version 2.1 of the License, or
* any later version.
*
* Heritrix is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
* GNU Lesser Public License for more details.
*
* You should have received a copy of the GNU Lesser Public License
* along with Heritrix; if not, write to the Free Software
* Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
*/
package org.archive.crawler.frontier.precedence;

import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.Transformer;
import org.archive.crawler.frontier.WorkQueue;

/**
 * QueuePrecedencePolicy that sets a uri-queue's precedence to a configured
 * base value, then lowers its precedence with each tier of successful URIs
 * completed. Any number of comma-separated tier sizes may be provided, with 
 * the last value assumed to repeat indefinitely. For example, with a 
 * 'base-precedence' value of 2, and 'increment-counts' of "100,1000", the
 * queue will have a precedence of 2 until 100 URIs are successfully fetched, 
 * then a precedence of 3 for the next 1000 URIs successfully fetched, then 
 * continue to drop one precedence rank for each 1000 URIs successfully 
 * fetched.
 */
public class SuccessCountsQueuePrecedencePolicy extends BaseQueuePrecedencePolicy {
    private static final long serialVersionUID = -4469760728466350850L;

// TODO: determine why this doesn't work
//
//    /** comma-separated list of success-counts at which precedence is bumped*/
//    final public static Key<List<Integer>> INCREMENT_COUNTS = 
//        Key.make((List<Integer>)Arrays.asList(new Integer[] {100}));
//
//    /**
//     * @param wq
//     * @return
//     */
//    protected int calculatePrecedence(WorkQueue wq) {
//        // FIXME: it's inefficient to do this every time; optimizing 
//        // should be possible via more sophisticated custom PrecedenceProvider
//        int precedence = wq.get(this,BASE_PRECEDENCE) - 1;
//        Iterator<Integer> iter = wq.get(this,INCREMENT_COUNTS).iterator();
//        int increment = iter.next(); 
//        long successes = wq.getSubstats().getFetchSuccesses();
//        while(successes>0) {
//            successes -= increment;
//            precedence++;
//            increment = iter.hasNext() ? iter.next() : increment; 
//        }
//        return precedence;
//    }

    /** comma-separated list of success-counts at which precedence is bumped*/
    {
        setIncrementCounts("100,1000");
    }
    public String getIncrementCounts() {
        return (String) kp.get("incrementCounts");
    }
    public void setIncrementCounts(String counts) {
        kp.put("incrementCounts",counts);
    }

    /* (non-Javadoc)
     * @see org.archive.crawler.frontier.QueuePrecedencePolicy#queueReevaluate(org.archive.crawler.frontier.WorkQueue)
     */
    @SuppressWarnings("unchecked")
    @Override
    protected int calculatePrecedence(WorkQueue wq) {
        // FIXME: it's ridiculously inefficient to do this every time, 
        // and optimizing will probably require inserting stateful policy 
        // helper object into WorkQueue -- expected when URI-precedence is
        // also supported
        int precedence = getBasePrecedence() - 1;
        Collection<Integer> increments = CollectionUtils.collect(
                Arrays.asList(getIncrementCounts().split(",")),
                new Transformer() {
                    public Object transform(final Object string) {
                        return Integer.parseInt((String)string);
                    }});
        Iterator<Integer> iter = increments.iterator();
        int increment = iter.next(); 
        long successes = wq.getSubstats().getFetchSuccesses();
        while(successes>=0) {
            successes -= increment;
            precedence++;
            increment = iter.hasNext() ? iter.next() : increment; 
        }
        return precedence;
    }
}
