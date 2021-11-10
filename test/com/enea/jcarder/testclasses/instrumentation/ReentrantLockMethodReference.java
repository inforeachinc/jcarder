package com.enea.jcarder.testclasses.instrumentation;

import com.enea.jcarder.agent.LockTracer;
import com.enea.jcarder.agent.instrument.MonitorWithContext;

import java.util.concurrent.locks.ReentrantLock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ReentrantLockMethodReference implements SynchronizationTestIfc {

    private final ReentrantLock lock = new ReentrantLock();

    private AutoCloseable guard()
    {
        lock.lock();
        return lock::unlock;
    }

    public void go() {
        assertFalse(lock.isHeldByCurrentThread());
        try (AutoCloseable guard = guard()) {
            assertTrue(lock.isHeldByCurrentThread());
        } catch (Exception ignore) {
        }
        assertFalse(lock.isHeldByCurrentThread());
    }

    public MonitorWithContext[] getExpectedMonitorEnterings() {
        return MonitorWithContext.create(LockTracer.getSyncObject(lock),
                                         getClass(), "guard:()Ljava/lang/AutoCloseable;",
                                         getClass().getName() + ".lock",
                                         17);
    }

    public MonitorWithContext[] getExpectedMonitorExitings() {
        return MonitorWithContext.create(LockTracer.getSyncObject(lock),
                                         getClass(), "guard:()Ljava/lang/AutoCloseable;",
                                         getClass().getName() + ".lock",
                                         18);
    }
}