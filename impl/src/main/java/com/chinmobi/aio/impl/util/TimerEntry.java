/**
 * MIT License
 *
 * Copyright (c) 2018 Zhaoping Yu
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 *
 */
package com.chinmobi.aio.impl.util;

import java.util.concurrent.locks.Lock;

/**
 * @author <a href="mailto:yuzhaoping1970@gmail.com">Zhaoping Yu</a>
 *
 */
public abstract class TimerEntry extends RedBlackTree.Entry
	implements RedBlackTreeEntryModifiedVerifier {

	protected TimerEntrySet belongsTo;

	private long timeout;
	private long triggerTime;

	private int expectedModCount;


	protected TimerEntry() {
		super();
	}


	public final void registerTo(final TimerEntrySet entrySet) {
		final TimerEntrySet belongsTo = this.belongsTo;
		if (belongsTo == null) {
			this.belongsTo = entrySet;
		} else if (belongsTo != entrySet) {
			throw new IllegalTreeNodeStateException("The entry has already been registered to another set.");
		}
	}

	public final void unregister() {
		cancel(0);
		this.belongsTo = null;
	}

	@Override
	protected final int compareTo(final RedBlackTree.Entry entry) {
		final TimerEntry o = (TimerEntry)entry;

		final long sTriggerTime = this.triggerTime;
		final long oTriggerTime = o.triggerTime;

		if (sTriggerTime < oTriggerTime) {
			return -1;
		} else if (sTriggerTime > oTriggerTime) {
			return 1;
		}
		return 0;
	}

	@Override
	protected final RedBlackTree<TimerEntry> belongsTree() {
		final TimerEntrySet entrySet = this.belongsTo;
		return (entrySet != null) ? entrySet.entriesTree() : null;
	}

	@Override
	protected void free() {
	}


	public final void setExpectedModCount(final int modCount) {
		this.expectedModCount = modCount;
	}


	public final long getTimeout() {
		return this.timeout;
	}

	public final void setTimeout(final long timeout) {
		this.timeout = timeout;
	}

	public final boolean isScheduled() {
		return isAddedToTree();
	}

	public final boolean schedule(final int modCount) {
		if (isAddedToTree()) {
			return true;
		}

		final RedBlackTree<TimerEntry> tree = belongsTree();
		if (tree != null) {
			final Lock lock = tree.lock();
			lock.lock();
			try {
				if (!modificationVerified(modCount)) {
					return false;
				}

				if (this.timeout > 0 || enableZeroTimeout()) {

					final long now = System.currentTimeMillis();
					this.triggerTime = now + this.timeout;

					tree.put(this);
				} else {
					return false;
				}
			} finally {
				lock.unlock();
			}

			onScheduled();

			this.belongsTo.notifyEntryAdded(this);
			return true;
		} else {
			throw new IllegalTreeNodeStateException("The entry was not registered.");
		}
	}

	public final boolean schedule() {
		return schedule(0);
	}


	public abstract Runnable getRunnable();


	public final void cancel(final int modCount) {
		if (syncRemoveFromTree((RedBlackTreeEntryModifiedVerifier)this, modCount)) {
			onCancelled();
		}
	}

	public void cancel() {
		cancel(0);
	}

	public void fail(final Throwable cause) {
		cancel(0);
	}


	/*
	 * (non-Javadoc)
	 * @see com.chinmobi.aio.impl.util.RedBlackTreeEntryModifiedVerifier#modificationVerified(int)
	 */
	public final boolean modificationVerified(final int modCount) {
		if (modCount != 0) {
			return (this.expectedModCount == modCount);
		} else {
			this.expectedModCount = 0;
			return true;
		}
	}

	/**
	 *
	 * @param now
	 * @return 1 - the entry was timeout; 0 - the entry is not timeout; &lt;0 - the entry has been cancelled
	 */
	protected int checkTimeout(final long now, final long[] delayTime) {
		final long triggerTm = this.triggerTime;
		if (triggerTm <= now) {
			return 1;
		} else
		if (delayTime != null) {
			delayTime[0] = (triggerTm - now);
		}
		return 0;
	}

	protected boolean enableZeroTimeout() {
		return false;
	}

	protected void onScheduled() {
	}

	protected void onCancelled() {
	}

	protected void onDiscarded() {
		onCancelled();
	}

}
