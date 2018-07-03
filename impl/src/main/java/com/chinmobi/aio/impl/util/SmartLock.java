/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

/*
 * This file is available under and governed by the GNU General Public
 * License version 2 only, as published by the Free Software Foundation.
 * However, the following notice accompanied the original version of this
 * file:
 *
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/licenses/publicdomain
 */

package com.chinmobi.aio.impl.util;

import java.util.Date;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.LockSupport;

/**
 * @author Doug Lea
 *
 * @author <a href="mailto:yuzhaoping1970@gmail.com">Zhaoping Yu</a>
 *
 */
public final class SmartLock implements Lock {


	public static class WorkThread extends Thread {

		private Node cachedNode;


		public WorkThread() {
			super();
		}

		public WorkThread(final Runnable target) {
			super(target);
		}

		public WorkThread(final Runnable target, final String name) {
			super(target, name);
		}

		public WorkThread(final String name) {
			super(name);
		}


		private final Node createNode() {
			Node node = this.cachedNode;
			if (node != null) {
				this.cachedNode = null;
			} else {
				node = new Node();
			}
			return node;
		}

		private final void cacheNode(final Node node) {
			this.cachedNode = node;
		}

	}


	private static final class Node {
		/** Marker to indicate a node is waiting in exclusive mode */
		private static final Node EXCLUSIVE = null;

		/**
		 * Link to predecessor node that current node/thread relies on
		 * for checking waitStatus. Assigned during enqueing, and nulled
		 * out (for sake of GC) only upon dequeuing.  Also, upon
		 * cancellation of a predecessor, we short-circuit while
		 * finding a non-cancelled one, which will always exist
		 * because the head node is never cancelled: A node becomes
		 * head only as a result of successful acquire. A
		 * cancelled thread never succeeds in acquiring, and a thread only
		 * cancels itself, not any other node.
		 */
		private final AtomicReference<Node> prev;

		/**
		 * Link to the successor node that the current node/thread
		 * unparks upon release. Assigned during enqueuing, adjusted
		 * when bypassing cancelled predecessors, and nulled out (for
		 * sake of GC) when dequeued.  The enq operation does not
		 * assign next field of a predecessor until after attachment,
		 * so seeing a null next field does not necessarily mean that
		 * node is at end of queue. However, if a next field appears
		 * to be null, we can scan prev's from the tail to
		 * double-check.  The next field of cancelled nodes is set to
		 * point to the node itself instead of null, to make life
		 * easier for isOnSyncQueue.
		 */
		private final AtomicReference<Node> next;


		/** waitStatus value to indicate thread has cancelled */
		private static final int CANCELLED =  1;
		/** waitStatus value to indicate successor's thread needs unparking */
		private static final int SIGNAL    = -1;
		/** waitStatus value to indicate thread is waiting on condition */
		private static final int CONDITION = -2;
		/**
		 * waitStatus value to indicate the next acquireShared should
		 * unconditionally propagate
		 */
		//static final int PROPAGATE = -3;

		/**
		 * Status field, taking on only the values:
		 *   SIGNAL:     The successor of this node is (or will soon be)
		 *               blocked (via park), so the current node must
		 *               unpark its successor when it releases or
		 *               cancels. To avoid races, acquire methods must
		 *               first indicate they need a signal,
		 *               then retry the atomic acquire, and then,
		 *               on failure, block.
		 *   CANCELLED:  This node is cancelled due to timeout or interrupt.
		 *               Nodes never leave this state. In particular,
		 *               a thread with cancelled node never again blocks.
		 *   CONDITION:  This node is currently on a condition queue.
		 *               It will not be used as a sync queue node
		 *               until transferred, at which time the status
		 *               will be set to 0. (Use of this value here has
		 *               nothing to do with the other uses of the
		 *               field, but simplifies mechanics.)
		 *   PROPAGATE:  A releaseShared should be propagated to other
		 *               nodes. This is set (for head node only) in
		 *               doReleaseShared to ensure propagation
		 *               continues, even if other operations have
		 *               since intervened.
		 *   0:          None of the above
		 *
		 * The values are arranged numerically to simplify use.
		 * Non-negative values mean that a node doesn't need to
		 * signal. So, most code doesn't need to check for particular
		 * values, just for sign.
		 *
		 * The field is initialized to 0 for normal sync nodes, and
		 * CONDITION for condition nodes.  It is modified using CAS
		 * (or when possible, unconditional volatile writes).
		 */
		private final AtomicInteger waitStatus;


		private volatile Thread thread;


		Node() {
			this.prev = new AtomicReference<Node>();
			this.next = new AtomicReference<Node>();
			this.waitStatus = new AtomicInteger();
		}


		private final void reset(final Thread thread) {
			this.prev.set(null);
			this.next.set(null);

			this.waitStatus.set(0);

			this.thread = thread;
		}

		private final Node predecessor() throws NullPointerException {
			final Node p = this.prev.get();
			if (p != null) {
				return p;
			} else {
				throw new NullPointerException();
			}
		}

		private final boolean compareAndSetNext(final Node expect, final Node update) {
			return this.next.compareAndSet(expect, update);
		}

		private final int waitStatus() {
			return this.waitStatus.get();
		}

		private final boolean compareAndSetWaitStatus(final int expect, final int update) {
			return this.waitStatus.compareAndSet(expect, update);
		}

		private final void setWaitStatus(final int newValue) {
			this.waitStatus.set(newValue);
		}


		/**
		 * Link to next node waiting on condition, or the special
		 * value SHARED.  Because condition queues are accessed only
		 * when holding in exclusive mode, we just need a simple
		 * linked queue to hold nodes while they are waiting on
		 * conditions. They are then transferred to the queue to
		 * re-acquire. And because conditions can only be exclusive,
		 * we save a field by using special value to indicate shared
		 * mode.
		 */
		private Node nextWaiter;


	}

	private static final Node createCurrentThreadNode() {
		final Thread current = Thread.currentThread();

		final Node node;
		if (current instanceof WorkThread) {
			node = ((WorkThread)current).createNode();
		} else {
			node = new Node();
		}
		node.reset(current);

		return node;
	}

	private static final Node createCurrentThreadNode(final Node mode) { // Used by addWaiter
		final Node node = createCurrentThreadNode();
		node.nextWaiter = mode;
		return node;
	}

	private static final Node createCurrentThreadNode(final int waitStatus) { // Used by Condition
		final Node node = createCurrentThreadNode();
		node.nextWaiter = null;
		node.setWaitStatus(waitStatus);
		return node;
	}

	private static void recycleCurrentThreadNode(final Node node) {
		final Thread current = Thread.currentThread();
		if (current instanceof WorkThread) {
			((WorkThread)current).cacheNode(node);
		}
	}


	private static final class Synchronizer {

	    /**
	     * The number of nanoseconds for which it is faster to spin
	     * rather than to use timed park. A rough estimate suffices
	     * to improve responsiveness with very short timeouts.
	     */
	    private static final long spinForTimeoutThreshold = 1000L;


		private final AtomicInteger state;

		/**
		 * Head of the wait queue, lazily initialized.  Except for
		 * initialization, it is modified only via method setHead.  Note:
		 * If head exists, its waitStatus is guaranteed not to be
		 * CANCELLED.
		 */
		private final AtomicReference<Node> head;

		/**
		 * Tail of the wait queue, lazily initialized.  Modified only via
		 * method enq to add new wait node.
		 */
		private final AtomicReference<Node> tail;


		/**
		 * The current owner of exclusive mode synchronization.
		 */
		private transient Thread exclusiveOwnerThread;


		Synchronizer() {
			this.state = new AtomicInteger();

			this.head = new AtomicReference<Node>();
			this.tail = new AtomicReference<Node>();
		}


		private final int getState() {
			return this.state.get();
		}

		private final void setState(final int newState) {
			this.state.set(newState);
		}

		private final boolean compareAndSetState(final int expect, final int update) {
			return this.state.compareAndSet(expect, update);
		}

		private final boolean compareAndSetHead(final Node update) {
			return this.head.compareAndSet(null, update);
		}

		private final boolean compareAndSetTail(final Node expect, final Node update) {
			return this.tail.compareAndSet(expect, update);
		}

		/**
		 * Inserts node into queue, initializing if necessary. See picture above.
		 * @param node the node to insert
		 * @return node's predecessor
		 */
		private Node enq(final Node node) {
			for (;;) {
				final Node t = this.tail.get();
				if (t == null) { // Must initialize
					if (compareAndSetHead(new Node())) {
						this.tail.set(this.head.get());
					}
				} else {
					node.prev.set(t);
					if (compareAndSetTail(t, node)) {
						t.next.set(node);
						return t;
					}
				}
			}
		}

		private final Node addWaiter(final Node mode) {
			final Node node = createCurrentThreadNode(mode);

			// Try the fast path of enq; backup to full enq on failure
			final Node pred = this.tail.get();
			if (pred != null) {
				node.prev.set(pred);
				if (compareAndSetTail(pred, node)) {
					pred.next.set(node);
					return node;
				}
			}
			enq(node);
			return node;
		}

		/**
		 * Sets head of queue to be node, thus dequeuing. Called only by
		 * acquire methods.  Also nulls out unused fields for sake of GC
		 * and to suppress unnecessary signals and traversals.
		 *
		 * @param node the node
		 */
		private final void setHead(final Node node) {
			final Node h = this.head.getAndSet(node);

			node.thread = null;
			node.prev.set(null);

			recycleCurrentThreadNode(h);
		}

		/**
		 * Sets the thread that currently owns exclusive access. A
		 * <tt>null</tt> argument indicates that no thread owns access.
		 * This method does not otherwise impose any synchronization or
		 * <tt>volatile</tt> field accesses.
		 */
		private final void setExclusiveOwnerThread(final Thread t) {
			this.exclusiveOwnerThread = t;
		}

		/**
		 * Returns the thread last set by
		 * <tt>setExclusiveOwnerThread</tt>, or <tt>null</tt> if never
		 * set.  This method does not otherwise impose any synchronization
		 * or <tt>volatile</tt> field accesses.
		 * @return the owner thread
		 */
		private final Thread getExclusiveOwnerThread() {
			return this.exclusiveOwnerThread;
		}


		final void lock() {
			if (compareAndSetState(0, 1)) {
				setExclusiveOwnerThread(Thread.currentThread());
			} else {
				acquire(1);
			}
		}

		/**
		 * Releases in exclusive mode.  Implemented by unblocking one or
		 * more threads if {@link #tryRelease} returns true.
		 * This method can be used to implement method {@link Lock#unlock}.
		 *
		 * @param arg the release argument.  This value is conveyed to
		 *        {@link #tryRelease} but is otherwise uninterpreted and
		 *        can represent anything you like.
		 * @return the value returned from {@link #tryRelease}
		 */
		final boolean release(final int arg) {
			if (tryRelease(arg)) {
				final Node h = this.head.get();
				if (h != null && h.waitStatus() != 0) {
					unparkSuccessor(h);
				}
				return true;
			}
			return false;
		}


		/**
		 * Acquires in exclusive mode, ignoring interrupts.  Implemented
		 * by invoking at least once {@link #tryAcquire},
		 * returning on success.  Otherwise the thread is queued, possibly
		 * repeatedly blocking and unblocking, invoking {@link
		 * #tryAcquire} until success.  This method can be used
		 * to implement method {@link Lock#lock}.
		 *
		 * @param arg the acquire argument.  This value is conveyed to
		 *        {@link #tryAcquire} but is otherwise uninterpreted and
		 *        can represent anything you like.
		 */
		private final void acquire(final int arg) {
			if (!tryAcquire(arg) &&
				acquireQueued(addWaiter(Node.EXCLUSIVE), arg)) {
				selfInterrupt();
			}
		}

		private final boolean tryAcquire(final int acquires) {
			return nonfairTryAcquire(acquires);
		}

		/**
		 * Performs non-fair tryLock.  tryAcquire is
		 * implemented in subclasses, but both need nonfair
		 * try for trylock method.
		 */
		private final boolean nonfairTryAcquire(final int acquires) {
			final Thread current = Thread.currentThread();
			final int c = getState();

			if (c == 0) {
				if (compareAndSetState(0, acquires)) {
					setExclusiveOwnerThread(current);
					return true;
				}
			} else if (current == getExclusiveOwnerThread()) {
				final int nextc = c + acquires;
				if (nextc < 0) { // overflow
					throw new Error("Maximum lock count exceeded");
				}
				setState(nextc);
				return true;
			}
			return false;
		}

		private final boolean tryRelease(final int releases) {
			final int c = getState() - releases;
			if (Thread.currentThread() != getExclusiveOwnerThread()) {
				throw new IllegalMonitorStateException();
			}
			boolean free = false;
			if (c == 0) {
				free = true;
				setExclusiveOwnerThread(null);
			}
			setState(c);
			return free;
		}

		/**
		 * Acquires in exclusive uninterruptible mode for thread already in
		 * queue. Used by condition wait methods as well as acquire.
		 *
		 * @param node the node
		 * @param arg the acquire argument
		 * @return {@code true} if interrupted while waiting
		 */
		private final boolean acquireQueued(final Node node, final int arg) {
			boolean failed = true;
			try {
				boolean interrupted = false;
				for (;;) {
					final Node p = node.predecessor();
					if (p == this.head.get() && tryAcquire(arg)) {
						setHead(node);
						p.next.set(null); // help GC
						failed = false;
						return interrupted;
					}
					if (shouldParkAfterFailedAcquire(p, node) && parkAndCheckInterrupt()) {
						interrupted = true;
					}
				}
			} finally {
				if (failed) {
					cancelAcquire(node);
				}
			}
		}

		/**
		 * Checks and updates status for a node that failed to acquire.
		 * Returns true if thread should block. This is the main signal
		 * control in all acquire loops.  Requires that pred == node.prev
		 *
		 * @param pred node's predecessor holding status
		 * @param node the node
		 * @return {@code true} if thread should block
		 */
		private static boolean shouldParkAfterFailedAcquire(Node pred, final Node node) {
			final int ws = pred.waitStatus();
			//NOTE: if (ws == Node.SIGNAL) {
			if (ws < 0) {
				/*
				 * This node has already set status asking a release
				 * to signal it, so it can safely park.
				 */
				return true;
			} else
			if (ws > 0) {
				/*
				 * Predecessor was cancelled. Skip over predecessors and
				 * indicate retry.
				 */
				do {
					pred = pred.prev.get();
					node.prev.set(pred);
				} while (pred.waitStatus() > 0);

				pred.next.set(node);
			} else {
				/*
				 * waitStatus must be 0 or PROPAGATE.  Indicate that we
				 * need a signal, but don't park yet.  Caller will need to
				 * retry to make sure it cannot acquire before parking.
				 */
				//NOTE: pred.compareAndSetWaitStatus(ws, Node.SIGNAL);
				pred.compareAndSetWaitStatus(0, Node.SIGNAL);
			}
			return false;
		}

		private static final void selfInterrupt() {
			Thread.currentThread().interrupt();
		}

		private static final boolean parkAndCheckInterrupt() {
			LockSupport.park();
			return Thread.interrupted();
		}


		/**
		 * Cancels an ongoing attempt to acquire.
		 *
		 * @param node the node
		 */
		private final void cancelAcquire(final Node node) {
			// Ignore if node doesn't exist
			if (node == null) return;

			node.thread = null;

			// Skip cancelled predecessors
			Node pred = node.prev.get();
			while (pred.waitStatus() > 0) {
				pred = pred.prev.get();
				node.prev.set(pred);
			}

			// predNext is the apparent node to unsplice. CASes below will
			// fail if not, in which case, we lost race vs another cancel
			// or signal, so no further action is necessary.
			final Node predNext = pred.next.get();

			// Can use unconditional write instead of CAS here.
			// After this atomic step, other Nodes can skip past us.
			// Before, we are free of interference from other threads.
			node.setWaitStatus(Node.CANCELLED);

			// If we are the tail, remove ourselves.
			if (node == this.tail.get() && compareAndSetTail(node, pred)) {
				pred.compareAndSetNext(predNext, null);
			} else {
				// If successor needs signal, try to set pred's next-link
				// so it will get one. Otherwise wake it up to propagate.
				/*NOTE: int ws;
				if (pred != this.head.get() &&
					((ws = pred.waitStatus()) == Node.SIGNAL ||
						(ws <= 0 && pred.compareAndSetWaitStatus(ws, Node.SIGNAL))
					) &&
					pred.thread != null) {*/
				if (pred != this.head.get() &&
					(pred.waitStatus() == Node.SIGNAL ||
						pred.compareAndSetWaitStatus(0, Node.SIGNAL)
					) &&
					pred.thread != null) {

					final Node next = node.next.get();
					if (next != null && next.waitStatus() <= 0) {
						pred.compareAndSetNext(predNext, next);
					}
				} else {
					unparkSuccessor(node);
				}

				node.next.set(node); // help GC
			}
		}

		/**
		 * Wakes up node's successor, if one exists.
		 *
		 * @param node the node
		 */
		private final void unparkSuccessor(final Node node) {
			/*
			 * If status is negative (i.e., possibly needing signal) try
			 * to clear in anticipation of signalling.  It is OK if this
			 * fails or if status is changed by waiting thread.
			 */
			/*NOTE: final int ws = node.waitStatus();
			if (ws < 0) {
				node.compareAndSetWaitStatus(ws, 0);
			}*/
			node.compareAndSetWaitStatus(Node.SIGNAL, 0);

			/*
			 * Thread to unpark is held in successor, which is normally
			 * just the next node.  But if cancelled or apparently null,
			 * traverse backwards from tail to find the actual
			 * non-cancelled successor.
			 */
			Node s = node.next.get();
			if (s == null || s.waitStatus() > 0) {
				s = null;
				for (Node t = this.tail.get(); t != null && t != node; t = t.prev.get()) {
					if (t.waitStatus() <= 0) {
						s = t;
					}
				}
			}

			if (s != null) {
				LockSupport.unpark(s.thread);
			}
		}


		final ConditionObject newCondition() {
			return new ConditionObject(this);
		}

		// Internal support methods for Conditions

		/**
		 * Invokes release with current state value; returns saved state.
		 * Cancels node and throws exception on failure.
		 * @param node the condition node for this wait
		 * @return previous sync state
		 */
		private final int fullyRelease(final Node node) {
			boolean failed = true;
			try {
				final int savedState = getState();
				if (release(savedState)) {
					failed = false;
					return savedState;
				} else {
					throw new IllegalMonitorStateException();
				}
			} finally {
				if (failed) {
					node.setWaitStatus(Node.CANCELLED);
				}
			}
		}

		/**
		 * Returns true if a node, always one that was initially placed on
		 * a condition queue, is now waiting to reacquire on sync queue.
		 * @param node the node
		 * @return true if is reacquiring
		 */
		private final boolean isOnSyncQueue(final Node node) {
			if (node.waitStatus() == Node.CONDITION || node.prev.get() == null) {
				return false;
			}
			if (node.next.get() != null) { // If has successor, it must be on queue
				return true;
			}
			/*
			 * node.prev can be non-null, but not yet on queue because
			 * the CAS to place it on queue can fail. So we have to
			 * traverse from tail to make sure it actually made it.  It
			 * will always be near the tail in calls to this method, and
			 * unless the CAS failed (which is unlikely), it will be
			 * there, so we hardly ever traverse much.
			 */
			return findNodeFromTail(node);
		}

		/**
		 * Returns true if node is on sync queue by searching backwards from tail.
		 * Called only when needed by isOnSyncQueue.
		 * @return true if present
		 */
		private boolean findNodeFromTail(final Node node) {
			Node t = this.tail.get();
			for (;;) {
				if (t == node) {
					return true;
				}
				if (t == null) {
					return false;
				}
				t = t.prev.get();
			}
		}

		/**
		 * Transfers node, if necessary, to sync queue after a interrupted-cancelled
		 * wait. Returns true if thread was cancelled before being
		 * signalled.
		 * @param node its node
		 * @return true if cancelled before the node was signalled
		 */
		private final boolean transferAfterCancelledWait(final Node node) {
			if (node.compareAndSetWaitStatus(Node.CONDITION, 0)) {
				enq(node);
				return true;
			}
			/*
			 * If we lost out to a signal(), then we can't proceed
			 * until it finishes its enq().  Cancelling during an
			 * incomplete transfer is both rare and transient, so just
			 * spin.
			 */
			while (!isOnSyncQueue(node)) {
				Thread.yield();
			}
			return false;
		}

		/**
		 * Transfers a node from a condition queue onto sync queue.
		 * Returns true if successful.
		 * @param node the node
		 * @return true if successfully transferred (else the node was
		 * cancelled before signal).
		 */
		private final boolean transferForSignal(final Node node) {
			/*
			 * If cannot change waitStatus, the node has been cancelled (interrupted).
			 */
			if (!node.compareAndSetWaitStatus(Node.CONDITION, 0)) {
				return false;
			}

			/*
			 * Splice onto queue and try to set waitStatus of predecessor to
			 * indicate that thread is (probably) waiting. If cancelled or
			 * attempt to set waitStatus fails, wake up to resync (in which
			 * case the waitStatus can be transiently and harmlessly wrong).
			 */
			final Node p = enq(node);
			final int ws = p.waitStatus();
			if (ws > 0 || !p.compareAndSetWaitStatus(ws, Node.SIGNAL)) {
				LockSupport.unpark(node.thread);
			}
			return true;
		}

		/*private final boolean isHeldExclusively() {
			// While we must in general read state before owner,
			// we don't need to do so to check if current thread is owner
			return getExclusiveOwnerThread() == Thread.currentThread();
		}*/


		/*final void acquireInterruptibly(final int arg) throws InterruptedException {
			if (Thread.interrupted()) {
				throw new InterruptedException();
			}

			if (!tryAcquire(arg)) {
				doAcquireInterruptibly(arg);
			}
		}*/

		/**
		 * Acquires in exclusive interruptible mode.
		 * @param arg the acquire argument
		 */
		/*private final void doAcquireInterruptibly(final int arg)
				throws InterruptedException {
			final Node node = addWaiter(Node.EXCLUSIVE);
			boolean failed = true;
			try {
				for (;;) {
					final Node p = node.predecessor();
					if (p == this.head.get() && tryAcquire(arg)) {
						setHead(node);
						p.next.set(null); // help GC
						failed = false;
						return;
					}
					if (shouldParkAfterFailedAcquire(p, node) && parkAndCheckInterrupt()) {
						throw new InterruptedException();
					}
				}
			} finally {
				if (failed) {
					cancelAcquire(node);
				}
			}
		}*/


		/*final boolean tryAcquireNanos(final int arg, final long nanosTimeout)
				throws InterruptedException {
			if (Thread.interrupted()) {
				throw new InterruptedException();
			}
			return tryAcquire(arg) ||
					doAcquireNanos(arg, nanosTimeout);
		}*/

		/**
		 * Acquires in exclusive timed mode.
		 *
		 * @param arg the acquire argument
		 * @param nanosTimeout max wait time
		 * @return {@code true} if acquired
		 */
		/*private boolean doAcquireNanos(final int arg, long nanosTimeout)
				throws InterruptedException {
			long lastTime = System.nanoTime();
			final Node node = addWaiter(Node.EXCLUSIVE);
			boolean failed = true;
			try {
				for (;;) {
					final Node p = node.predecessor();
					if (p == this.head.get() && tryAcquire(arg)) {
						setHead(node);
						p.next.set(null); // help GC
						failed = false;
						return true;
					}
					if (nanosTimeout <= 0) {
						return false;
					}
					if (shouldParkAfterFailedAcquire(p, node) &&
							nanosTimeout > spinForTimeoutThreshold) {
						LockSupport.parkNanos(nanosTimeout);
					}
					long now = System.nanoTime();
					nanosTimeout -= now - lastTime;
					lastTime = now;
					if (Thread.interrupted()) {
						throw new InterruptedException();
					}
				}
			} finally {
				if (failed) {
					cancelAcquire(node);
				}
			}
		}*/

	}


	private final Synchronizer sync;


	public SmartLock() {
		this.sync = new Synchronizer();
	}


	/*
	 * (non-Javadoc)
	 * @see java.util.concurrent.locks.Lock#lock()
	 */
	public final void lock() {
		this.sync.lock();
	}

	/*
	 * (non-Javadoc)
	 * @see java.util.concurrent.locks.Lock#lockInterruptibly()
	 */
	public final void lockInterruptibly() throws InterruptedException {
		/*this.sync.acquireInterruptibly(1);*/
		throw new UnsupportedOperationException();
	}

	/*
	 * (non-Javadoc)
	 * @see java.util.concurrent.locks.Lock#tryLock()
	 */
	public final boolean tryLock() {
		return this.sync.nonfairTryAcquire(1);
	}

	/*
	 * (non-Javadoc)
	 * @see java.util.concurrent.locks.Lock#tryLock(long, java.util.concurrent.TimeUnit)
	 */
	public final boolean tryLock(final long timeout, final TimeUnit unit)
			throws InterruptedException {
		/*return this.sync.tryAcquireNanos(1, unit.toNanos(timeout));*/
		throw new UnsupportedOperationException();
	}

	/*
	 * (non-Javadoc)
	 * @see java.util.concurrent.locks.Lock#unlock()
	 */
	public final void unlock() {
		this.sync.release(1);
	}

	/*
	 * (non-Javadoc)
	 * @see java.util.concurrent.locks.Lock#newCondition()
	 */
	public final ExtendedCondition newCondition() {
		return this.sync.newCondition();
	}


	private static final class ConditionObject implements ExtendedCondition {

		private final Synchronizer sync;


		/** Mode meaning to reinterrupt on exit from wait */
		private static final int REINTERRUPT =  1;
		/** Mode meaning to throw InterruptedException on exit from wait */
		private static final int THROW_IE    = -1;


		/** First node of condition queue. */
		private transient Node firstWaiter;
		/** Last node of condition queue. */
		private transient Node lastWaiter;


		ConditionObject(final Synchronizer sync) {
			this.sync = sync;
		}


		public final void await() throws InterruptedException {
			if (Thread.interrupted()) {
				throw new InterruptedException();
			}
			final Node node = addConditionWaiter();
			final int savedState = this.sync.fullyRelease(node);
			int interruptMode = 0;
			while (!this.sync.isOnSyncQueue(node)) {
				LockSupport.park();
				if ((interruptMode = checkInterruptWhileWaiting(node)) != 0) {
					break;
				}
			}
			if (this.sync.acquireQueued(node, savedState) && interruptMode != THROW_IE) {
				interruptMode = REINTERRUPT;
			}
			if (node.nextWaiter != null) { // clean up if cancelled
				unlinkCancelledWaiters();
			}
			if (interruptMode != 0) {
				reportInterruptAfterWait(interruptMode);
			}
		}

		public final void awaitUninterruptibly() {
			/*final Node node = addConditionWaiter();
			final int savedState = this.sync.fullyRelease(node);
			boolean interrupted = false;
			while (!this.sync.isOnSyncQueue(node)) {
				LockSupport.park();
				if (Thread.interrupted()) {
					interrupted = true;
				}
			}
			if (this.sync.acquireQueued(node, savedState) || interrupted) {
				Synchronizer.selfInterrupt();
			}*/
			throw new UnsupportedOperationException();
		}

		public final long awaitNanos(long nanosTimeout) throws InterruptedException {
			/*if (Thread.interrupted()) {
				throw new InterruptedException();
			}
			final Node node = addConditionWaiter();
			final int savedState = this.sync.fullyRelease(node);
			long lastTime = System.nanoTime();
			int interruptMode = 0;
			while (!this.sync.isOnSyncQueue(node)) {
				if (nanosTimeout <= 0L) {
					this.sync.transferAfterCancelledWait(node);
					break;
				}
				LockSupport.parkNanos(nanosTimeout);
				if ((interruptMode = checkInterruptWhileWaiting(node)) != 0) {
					break;
				}

				long now = System.nanoTime();
				nanosTimeout -= now - lastTime;
				lastTime = now;
			}
			if (this.sync.acquireQueued(node, savedState) && interruptMode != THROW_IE) {
				interruptMode = REINTERRUPT;
			}
			if (node.nextWaiter != null) {
				unlinkCancelledWaiters();
			}
			if (interruptMode != 0) {
				reportInterruptAfterWait(interruptMode);
			}
			return nanosTimeout - (System.nanoTime() - lastTime);*/
			throw new UnsupportedOperationException();
		}

		public final boolean await(final long time, final TimeUnit unit)
				throws InterruptedException {
			if (unit == null) {
				throw new NullPointerException();
			}
			long nanosTimeout = unit.toNanos(time);
			if (Thread.interrupted()) {
				throw new InterruptedException();
			}
			final Node node = addConditionWaiter();
			final int savedState = this.sync.fullyRelease(node);
			long lastTime = System.nanoTime();
			boolean timedout = false;
			int interruptMode = 0;
			while (!this.sync.isOnSyncQueue(node)) {
				if (nanosTimeout <= 0L) {
					timedout = this.sync.transferAfterCancelledWait(node);
					break;
				}
				if (nanosTimeout >= Synchronizer.spinForTimeoutThreshold) {
					LockSupport.parkNanos(nanosTimeout);
				}
				if ((interruptMode = checkInterruptWhileWaiting(node)) != 0) {
					break;
				}
				long now = System.nanoTime();
				nanosTimeout -= now - lastTime;
				lastTime = now;
			}
			if (this.sync.acquireQueued(node, savedState) && interruptMode != THROW_IE) {
				interruptMode = REINTERRUPT;
			}
			if (node.nextWaiter != null) {
				unlinkCancelledWaiters();
			}
			if (interruptMode != 0) {
				reportInterruptAfterWait(interruptMode);
			}
			return !timedout;
		}

		public final boolean awaitUntil(final Date deadline) throws InterruptedException {
			/*if (deadline == null) {
				throw new NullPointerException();
			}
			final long abstime = deadline.getTime();
			if (Thread.interrupted()) {
				throw new InterruptedException();
			}
			final Node node = addConditionWaiter();
			final int savedState = this.sync.fullyRelease(node);
			boolean timedout = false;
			int interruptMode = 0;
			while (!this.sync.isOnSyncQueue(node)) {
				if (System.currentTimeMillis() > abstime) {
					timedout = this.sync.transferAfterCancelledWait(node);
					break;
				}
				LockSupport.parkUntil(abstime);
				if ((interruptMode = checkInterruptWhileWaiting(node)) != 0) {
					break;
				}
			}
			if (this.sync.acquireQueued(node, savedState) && interruptMode != THROW_IE) {
				interruptMode = REINTERRUPT;
			}
			if (node.nextWaiter != null) {
				unlinkCancelledWaiters();
			}
			if (interruptMode != 0) {
				reportInterruptAfterWait(interruptMode);
			}
			return !timedout;*/
			throw new UnsupportedOperationException();
		}

		public final void signal() {
			/*if (!this.sync.isHeldExclusively()) {
				throw new IllegalMonitorStateException();
			}*/
			final Node first = this.firstWaiter;
			if (first != null) {
				doSignal(first);
			}
		}

		public final void signalAll() {
			/*if (!this.sync.isHeldExclusively()) {
				throw new IllegalMonitorStateException();
			}*/
			final Node first = this.firstWaiter;
			if (first != null) {
				doSignalAll(first);
			}
		}

		public final boolean hasWaiters() {
			/*if (!this.sync.isHeldExclusively()) {
				throw new IllegalMonitorStateException();
			}*/
			for (Node w = this.firstWaiter; w != null; w = w.nextWaiter) {
				if (w.waitStatus.get() == Node.CONDITION) {
					return true;
				}
			}
			return false;
		}

		// Internal methods

		/**
		 * Adds a new waiter to wait queue.
		 * @return its new wait node
		 */
		private final Node addConditionWaiter() {
			Node t = this.lastWaiter;
			// If lastWaiter is cancelled, clean out.
			if (t != null && t.waitStatus() != Node.CONDITION) {
				unlinkCancelledWaiters();
				t = this.lastWaiter;
			}

			final Node node = createCurrentThreadNode(Node.CONDITION);

			if (t == null) {
				this.firstWaiter = node;
			} else {
				t.nextWaiter = node;
			}
			this.lastWaiter = node;

			return node;
		}

		/**
		 * Unlinks cancelled waiter nodes from condition queue.
		 * Called only while holding lock. This is called when
		 * cancellation occurred during condition wait, and upon
		 * insertion of a new waiter when lastWaiter is seen to have
		 * been cancelled. This method is needed to avoid garbage
		 * retention in the absence of signals. So even though it may
		 * require a full traversal, it comes into play only when
		 * timeouts or cancellations occur in the absence of
		 * signals. It traverses all nodes rather than stopping at a
		 * particular target to unlink all pointers to garbage nodes
		 * without requiring many re-traversals during cancellation
		 * storms.
		 */
		private final void unlinkCancelledWaiters() {
			Node t = this.firstWaiter;
			Node trail = null;
			while (t != null) {
				final Node next = t.nextWaiter;
				if (t.waitStatus() != Node.CONDITION) {
					t.nextWaiter = null;
					if (trail == null) {
						this.firstWaiter = next;
					} else {
						trail.nextWaiter = next;
					}
					if (next == null) {
						this.lastWaiter = trail;
					}
				} else {
					trail = t;
				}
				t = next;
			}
		}

		/**
		 * Checks for interrupt, returning THROW_IE if interrupted
		 * before signalled, REINTERRUPT if after signalled, or
		 * 0 if not interrupted.
		 */
		private final int checkInterruptWhileWaiting(final Node node) {
			return Thread.interrupted() ?
				(this.sync.transferAfterCancelledWait(node) ? THROW_IE : REINTERRUPT) :
				0;
		}

		/**
		 * Throws InterruptedException, reinterrupts current thread, or
		 * does nothing, depending on mode.
		 */
		private static final void reportInterruptAfterWait(final int interruptMode)
				throws InterruptedException {
			if (interruptMode == THROW_IE) {
				throw new InterruptedException();
			} else if (interruptMode == REINTERRUPT) {
				Synchronizer.selfInterrupt();
			}
		}

		/**
		 * Removes and transfers nodes until hit non-cancelled one or
		 * null. Split out from signal in part to encourage compilers
		 * to inline the case of no waiters.
		 * @param first (non-null) the first node on condition queue
		 */
		private final void doSignal(Node first) {
			do {
				if ((this.firstWaiter = first.nextWaiter) == null) {
					this.lastWaiter = null;
				}
				first.nextWaiter = null;
			} while (!this.sync.transferForSignal(first) &&
						(first = this.firstWaiter) != null);
		}

		/**
		 * Removes and transfers all nodes.
		 * @param first (non-null) the first node on condition queue
		 */
		private final void doSignalAll(Node first) {
			this.lastWaiter = this.firstWaiter = null;
			do {
				final Node next = first.nextWaiter;
				first.nextWaiter = null;
				this.sync.transferForSignal(first);
				first = next;
			} while (first != null);
		}

	}

}
