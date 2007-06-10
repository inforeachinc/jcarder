package com.enea.jcarder.analyzer;

import java.util.Map;

import net.jcip.annotations.NotThreadSafe;

import com.enea.jcarder.common.LockingContext;
import com.enea.jcarder.common.contexts.ContextReaderIfc;

/**
 * A LockEdge instance represents a directed edge from a source LockNode to a
 * target LockNode.
 */
@NotThreadSafe
class LockEdge {
    private final LockNode mSource;
    private final LockNode mTarget;
    private final long mThreadId; // The thread that did the synchronization.
    private int mSourceContextId;
    private int mTargetContextId;
    private long mNumberOfDuplicates;

    LockEdge(LockNode source,
             LockNode target,
             long threadId,
             int sourceLockingContextId,
             int targetLockingContextId) {
        mSource = source;
        mTarget = target;
        mThreadId = threadId;
        mSourceContextId = sourceLockingContextId;
        mTargetContextId = targetLockingContextId;
        mNumberOfDuplicates = 0;
    }

    void merge(LockEdge other) {
        assert this.equals(other);
        mNumberOfDuplicates += (other.mNumberOfDuplicates + 1);
    }

    long getDuplicates() {
        return mNumberOfDuplicates;
    }

    boolean alike(LockEdge other, ContextReaderIfc ras) {
        /*
         * TODO Some kind of cache to improve performance? Note that the context
         * IDs are not declared final.
         */
        LockingContext thisSourceContext =
            ras.readContext(mSourceContextId);
        LockingContext otherSourceContext =
            ras.readContext(other.mSourceContextId);
        LockingContext thisTargetContext =
            ras.readContext(mTargetContextId);
        LockingContext otherTargetContext =
            ras.readContext(other.mTargetContextId);
        return thisSourceContext.alike(otherSourceContext)
               && thisTargetContext.alike(otherTargetContext)
               && mSource.alike(other.mSource, ras)
               && mTarget.alike(other.mTarget, ras);
    }

    public boolean equals(Object obj) {
        /*
         * TODO It might be a potential problem to use LockEdges in HashMaps
         * since they are mutable and this equals method depends on them?
         */
        try {
            LockEdge other = (LockEdge) obj;
            return (mTarget.getLockId() == other.mTarget.getLockId())
            && (mSource.getLockId() == other.mSource.getLockId())
            && (mThreadId == other.mThreadId)
            && (mSourceContextId == other.mSourceContextId)
            && (mTargetContextId == other.mTargetContextId);
        } catch (Exception e) {
            return false;
        }
    }

    public int hashCode() {
        // TODO Improve hashCode algorithm to improve performance?
        return mTarget.getLockId() + mSource.getLockId();
    }

    LockNode getTarget() {
        return mTarget;
    }

    LockNode getSource() {
        return mSource;
    }

    int getSourceLockingContextId() {
        return mSourceContextId;
    }

    int getTargetLockingContextId() {
        return mTargetContextId;
    }

    /**
     * Translate the source and target context ID according to a translation
     * map.
     */
    void translateContextIds(Map<Integer, Integer> translation) {
        final Integer newSourceId = translation.get(mSourceContextId);
        if (newSourceId != null && newSourceId != mSourceContextId) {
            mSourceContextId = newSourceId;
        }
        final Integer newTargetId = translation.get(mTargetContextId);
        if (newTargetId != null && newSourceId != mTargetContextId) {
            mTargetContextId = newTargetId;
        }
    }

    long getThreadId() {
        return mThreadId;
    }

    public String toString() {
        return "  " + mSource + "->" + mTarget;
    }
}
