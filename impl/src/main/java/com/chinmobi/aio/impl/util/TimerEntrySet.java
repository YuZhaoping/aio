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

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;

import com.chinmobi.aio.util.AIOTimer;
import com.chinmobi.aio.util.AIOTimerCallable;

/**
 * @author <a href="mailto:yuzhaoping1970@gmail.com">Zhaoping Yu</a>
 *
 */
public final class TimerEntrySet {

	public interface Observer {
		public void notifyTimerEntryAdded(TimerEntrySet entrySet, TimerEntry timerEntry);
		public void notifyTimerEntryRuntimeException(TimerEntry timerEntry, RuntimeException ex);
	}


	private final Observer observer;

	private final RedBlackTree<TimerEntry> timerEntries;


	public TimerEntrySet(final Observer observer) {
		super();

		this.observer = observer;
		this.timerEntries = new RedBlackTree<TimerEntry>();
	}


	final RedBlackTree<TimerEntry> entriesTree() {
		return this.timerEntries;
	}

	final void notifyEntryAdded(final TimerEntry timerEntry) {
		this.observer.notifyTimerEntryAdded(this, timerEntry);
	}

	final void notifyEntryRuntimeException(final TimerEntry timerEntry, final RuntimeException ex) {
		this.observer.notifyTimerEntryRuntimeException(timerEntry, ex);
	}


	public final Lock lock() {
		return this.timerEntries.lock();
	}

	public final boolean isEmpty() {
		return this.timerEntries.synchronizer().isEmpty();
	}


	public final TimerCallableEntry schedule(final AIOTimer timer,
			final AIOTimerCallable callable, final long delay, final TimeUnit unit,
			final Object attachment) {

		final TimerCallableEntry entry = new TimerCallableEntry(timer, callable, attachment);
		entry.registerTo(this);
		entry.setTimeout(unit.toMillis(delay));

		entry.schedule();

		return entry;
	}


	public final boolean hasTimeoutEntry(final long now) {
		for (;;) {
			TimerEntry removedEntry = null;

			final Lock lock = this.timerEntries.lock();
			lock.lock();
			try {
				final TimerEntry entry = hasTimeoutEntry0(now, true);

				if (entry != null) {
					if (entry.isAddedToTree()) {
						return true;
					} else {
						removedEntry = entry;
					}
				} else {
					return false;
				}
			} finally {
				lock.unlock();
			}

			if (removedEntry != null) {
				removedEntry.onDiscarded();
			}
		}
	}

	public final boolean hasTimeoutEntry() {
		final long now = System.currentTimeMillis();
		return hasTimeoutEntry(now);
	}

	public final TimerEntry checkTimeoutEntry(final long now, final boolean[] hasNext) {
		for (;;) {
			TimerEntry removedEntry = null;

			final Lock lock = this.timerEntries.lock();
			lock.lock();
			try {
				TimerEntry entry = this.timerEntries.firstEntry();
				while (entry != null) {
					try {
						final int status = entry.checkTimeout(now, null);
						if (status > 0) {
							this.timerEntries.remove(entry);

							if (hasNext != null) {
								hasNext[0] = (hasTimeoutEntry0(now, false) != null);
							}

							return entry;
						} else if (status == 0) {
							break;
						} else {
							this.timerEntries.remove(entry);
							removedEntry = entry;
							break;
						}
					} catch (RuntimeException ex) {
						this.timerEntries.remove(entry);
						notifyEntryRuntimeException(entry, ex);
						removedEntry = entry;
						break;
					}
				}
			} finally {
				lock.unlock();
			}

			if (removedEntry != null) {
				removedEntry.onDiscarded();
				continue;
			}

			break;
		}

		return null;
	}

	public final TimerEntry checkTimeoutEntry(final long now) {
		return checkTimeoutEntry(now, null);
	}

	public final TimerEntry checkTimeoutEntry(final boolean[] hasNext) {
		final long now = System.currentTimeMillis();
		return checkTimeoutEntry(now, hasNext);
	}

	public final TimerEntry checkTimeoutEntry() {
		final long now = System.currentTimeMillis();
		return checkTimeoutEntry(now, null);
	}

	private final TimerEntry hasTimeoutEntry0(final long now, final boolean removeInvalid) {
		TimerEntry entry = this.timerEntries.firstEntry();
		while (entry != null) {
			try {
				final int status = entry.checkTimeout(now, null);
				if (status > 0) {
					return entry;
				} else if (status == 0) {
					return null;
				} else {
					if (removeInvalid) {
						this.timerEntries.remove(entry);
					}
					return entry;
				}
			} catch (RuntimeException ex) {
				this.timerEntries.remove(entry);
				notifyEntryRuntimeException(entry, ex);
				entry = this.timerEntries.firstEntry();
			}
		}
		return null;
	}


	public final void shuttingDown() {
		final Lock lock = this.timerEntries.lock();
		lock.lock();
		try {
			final RedBlackTreeIter<TimerEntry> iter = this.timerEntries.iterator();

			TimerEntry entry = iter.next();
			while (entry != null) {
				entry.cancel();

				entry = iter.next();
			}
		} finally {
			lock.unlock();
		}
	}

	public final void clear() {
		final Lock lock = this.timerEntries.lock();
		lock.lock();
		try {
			this.timerEntries.clear();
		} finally {
			lock.unlock();
		}
	}

	public final long adjustScheduleTimeout(final long[] scheduleTimeout) {

		scheduleTimeout[0] = 0;

		for (;;) {
			final long now = System.currentTimeMillis();

			TimerEntry removedEntry = null;

			final Lock lock = this.timerEntries.lock();
			lock.lock();
			try {
				TimerEntry entry = this.timerEntries.firstEntry();
				while (entry != null) {
					try {
						final int status = entry.checkTimeout(now, scheduleTimeout);
						if (status > 0) {
							return -1;
						} else if (status == 0) {
							break;
						} else {
							this.timerEntries.remove(entry);
							removedEntry = entry;
							break;
						}
					} catch (RuntimeException ex) {
						this.timerEntries.remove(entry);
						notifyEntryRuntimeException(entry, ex);
						removedEntry = entry;
						break;
					}
				}
			} finally {
				lock.unlock();
			}

			if (removedEntry != null) {
				removedEntry.onDiscarded();
				continue;
			}

			break;
		}

		return scheduleTimeout[0];
	}

}
