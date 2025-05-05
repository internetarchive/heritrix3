package org.archive.util;

import java.time.Duration;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A synchronization aid that tracks concurrent activities and lets callers
 * wait until the system has been continuously <em>idle</em> for a specified
 * duration.
 *
 * <p>Call {@link #begin()} when an activity starts and {@link #end()} when it
 * finishes.  A thread may then invoke {@link #awaitIdleFor(Duration, Duration)}
 * to block until:
 * <ol>
 *   <li>there are no in‑flight activities, and</li>
 *   <li>that idle state has lasted for at least the requested idle period.</li>
 * </ol>
 */
public class IdleBarrier {
    private final ReentrantLock lock = new ReentrantLock(true);
    private final Condition becameIdle = lock.newCondition();
    private long inflight = 0;
    private long lastBecameIdleAt = System.nanoTime();

    /**
     * Registers the start of an activity.
     *
     * <p>Every call to {@code begin()} <strong>must</strong> be paired with a
     * later call to {@link #end()}, typically using a {@code try/finally}
     * block.
     */
    public void begin() {
        lock.lock();
        try {
            inflight++;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Registers the completion of a previously started activity.
     *
     * @throws IllegalStateException if called more times than {@code begin()}
     */
    public void end() {
        lock.lock();
        try {
            if (inflight <= 0) throw new IllegalStateException("end() called more times than begin()");
            inflight--;
            if (inflight == 0) {
                lastBecameIdleAt = System.nanoTime();
                becameIdle.signalAll();
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Blocks the calling thread until the system has been idle for at least
     * {@code minIdle} or the overall {@code timeout} expires.
     *
     * @return {@code true} if the idle period condition was satisfied,
     *         {@code false} if the {@code timeout} was reached
     * @throws InterruptedException if the thread was interrupted while waiting
     */
    public boolean awaitIdleFor(Duration idlePeriod, Duration timeout) throws InterruptedException {
        long idlePeriodNanos = idlePeriod.toNanos();
        long deadline = System.nanoTime() + timeout.toNanos();
        lock.lockInterruptibly();
        try {
            while (true) {
                long now = System.nanoTime();
                long untilTimeout = deadline - now;
                if (untilTimeout <= 0) {
                    return false;
                }

                if (inflight == 0) {
                    long idleSoFar = now - lastBecameIdleAt;
                    if (idleSoFar >= idlePeriodNanos) {
                        return true;
                    }

                    // Reached idle, but need to wait
                    long waitTime = Math.min(idlePeriodNanos - idleSoFar, untilTimeout);
                    long ignore = becameIdle.awaitNanos(waitTime);
                } else {
                    long ignore = becameIdle.awaitNanos(untilTimeout);
                }
            }
        } finally {
            lock.unlock();
        }
    }
}