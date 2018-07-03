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
package com.chinmobi.aiotest.level0;

import com.chinmobi.aio.impl.util.TimerEntry;
import com.chinmobi.aio.impl.util.TimerEntrySet;
import com.chinmobi.testapp.BaseTestAction;

/**
 * @author <a href="mailto:yuzhaoping1970@gmail.com">Zhaoping Yu</a>
 *
 */
public final class TimerTestAction extends BaseTestAction implements TimerEntrySet.Observer {


	private final class Entry extends TimerEntry implements Runnable {

		private final boolean enableZeroTimeout;

		private int status;


		Entry(final boolean enableZeroTimeout) {
			this.enableZeroTimeout = enableZeroTimeout;
			this.status = 0;
		}


		@Override
		protected final void free() {
			TimerTestAction.this.count++;
		}

		@Override
		public final Runnable getRunnable() {
			return this;
		}

		@Override
		public final void cancel() {
			super.cancel();
			TimerTestAction.this.count++;
		}

		@Override
		protected final int checkTimeout(final long now, final long[] delayTime) {
			if (this.status >= 0) {
				return super.checkTimeout(now, delayTime);
			} else {
				return this.status;
			}
		}

		/* (non-Javadoc)
		 * @see java.lang.Runnable#run()
		 */
		public final void run() {
		}

		@Override
		protected final boolean enableZeroTimeout() {
			return this.enableZeroTimeout;
		}

		final void setStatus(final int status) {
			this.status = status;
		}

	}


	private final TimerEntrySet entrySet;

	private final boolean[] hasNext;

	private int count;


	public TimerTestAction() {
		super();

		this.entrySet = new TimerEntrySet(this);
		this.hasNext = new boolean[1];
	}


	public final void notifyTimerEntryAdded(final TimerEntrySet entrySet, final TimerEntry timerEntry) {
	}

	public final void notifyTimerEntryRuntimeException(final TimerEntry timerEntry, final RuntimeException ex) {
	}

	/*
	 * Test methods
	 */

	public final void testBasicTimerEntry0() {
		final TimerEntrySet entrySet = this.entrySet;

		assertFalse(entrySet.hasTimeoutEntry());
		assertNull(entrySet.checkTimeoutEntry(this.hasNext));

		Entry entry = new Entry(false);
		entry.registerTo(entrySet);

		assertFalse(entry.isScheduled());

		entry.setTimeout(0);
		assertFalse(entry.schedule());
		assertFalse(entry.isScheduled());

		entry.setTimeout(1000);
		assertTrue(entry.schedule());
		assertTrue(entry.isScheduled());

		entry.cancel();
		assertFalse(entry.isScheduled());

		// -------------------------------------------------
		assertTrue(entry.schedule());
		assertTrue(entry.isScheduled());
		assertTrue(entry.schedule());
		assertTrue(entry.isScheduled());

		entry.cancel();
		assertFalse(entry.isScheduled());

		// -------------------------------------------------
		entry = new Entry(true);
		entry.registerTo(entrySet);

		assertFalse(entry.isScheduled());

		entry.setTimeout(0);
		assertTrue(entry.schedule());
		assertTrue(entry.isScheduled());

		entry.cancel();
		assertFalse(entry.isScheduled());
	}

	public final void testBasicTimerEntry1() {
		final TimerEntrySet entrySet = this.entrySet;

		assertFalse(entrySet.hasTimeoutEntry());
		assertNull(entrySet.checkTimeoutEntry(this.hasNext));

		Entry entry = new Entry(false);
		entry.registerTo(entrySet);

		entry.setTimeout(1000);

		// -------------------------------------------------
		assertTrue(entry.schedule());
		assertTrue(entry.isScheduled());

		assertFalse(entrySet.hasTimeoutEntry());
		assertNull(entrySet.checkTimeoutEntry(this.hasNext));

		long now = System.currentTimeMillis();
		now += 1000;

		assertTrue(entrySet.hasTimeoutEntry(now));
		assertNotNull(entrySet.checkTimeoutEntry(now, this.hasNext));
		assertFalse(this.hasNext[0]);

		assertFalse(entry.isScheduled());
	}

	public final void testBasicTimerEntry2() {
		final TimerEntrySet entrySet = this.entrySet;

		assertFalse(entrySet.hasTimeoutEntry());
		assertNull(entrySet.checkTimeoutEntry(this.hasNext));

		Entry entry1 = new Entry(false);
		entry1.registerTo(entrySet);

		entry1.setTimeout(1000);

		assertTrue(entry1.schedule());
		assertTrue(entry1.isScheduled());

		// -------------------------------------------------
		Entry entry2 = new Entry(false);
		entry2.registerTo(entrySet);

		entry2.setTimeout(800);

		assertTrue(entry2.schedule());
		assertTrue(entry2.isScheduled());

		// -------------------------------------------------
		assertFalse(entrySet.hasTimeoutEntry());
		assertNull(entrySet.checkTimeoutEntry(this.hasNext));

		final long[] scheduleTimeout = new long[1];
		long result = entrySet.adjustScheduleTimeout(scheduleTimeout);
		assertTrue(result > 0);

		assertTrue(scheduleTimeout[0] <= 800);

		// -------------------------------------------------
		long now = System.currentTimeMillis();
		now += 1000;

		assertTrue(entrySet.hasTimeoutEntry(now));

		assertTrue(entry2.isScheduled());
		assertTrue(entry2 == entrySet.checkTimeoutEntry(now, this.hasNext));
		assertTrue(this.hasNext[0]);
		assertFalse(entry2.isScheduled());

		assertTrue(entry1.isScheduled());
		assertTrue(entry1 == entrySet.checkTimeoutEntry(now, this.hasNext));
		assertFalse(this.hasNext[0]);
		assertFalse(entry1.isScheduled());
	}

	public final void testBasicTimerEntry3() {
		final TimerEntrySet entrySet = this.entrySet;

		assertFalse(entrySet.hasTimeoutEntry());
		assertNull(entrySet.checkTimeoutEntry(this.hasNext));

		Entry entry1 = new Entry(false);
		entry1.registerTo(entrySet);

		entry1.setTimeout(1000);

		assertTrue(entry1.schedule());
		assertTrue(entry1.isScheduled());

		// -------------------------------------------------
		Entry entry2 = new Entry(false);
		entry2.registerTo(entrySet);

		entry2.setTimeout(800);

		assertTrue(entry2.schedule());
		assertTrue(entry2.isScheduled());

		// -------------------------------------------------
		assertFalse(entrySet.hasTimeoutEntry());
		assertNull(entrySet.checkTimeoutEntry(this.hasNext));

		entry2.setStatus(-1);

		assertTrue(entry1.isScheduled());
		assertTrue(entry2.isScheduled());

		final long[] scheduleTimeout = new long[1];
		long result = entrySet.adjustScheduleTimeout(scheduleTimeout);
		assertTrue(result > 0);

		assertTrue(entry1.isScheduled());
		assertFalse(entry2.isScheduled());

		assertTrue(scheduleTimeout[0] > 800);
		assertTrue(scheduleTimeout[0] <= 1000);

		assertTrue(entry2.schedule());

		// -------------------------------------------------
		long now = System.currentTimeMillis();
		now += 1000;

		assertTrue(entry1.isScheduled());
		assertTrue(entry2.isScheduled());

		assertTrue(entrySet.hasTimeoutEntry(now));

		assertTrue(entry1.isScheduled());
		assertFalse(entry2.isScheduled());

		assertTrue(entry2.schedule());

		// -------------------------------------------------
		assertTrue(entry1.isScheduled());
		assertTrue(entry2.isScheduled());

		assertTrue(entry1 == entrySet.checkTimeoutEntry(now, this.hasNext));
		assertFalse(this.hasNext[0]);
		assertFalse(entry1.isScheduled());
		assertFalse(entry2.isScheduled());
	}

	public final void testBasicTimerEntry4() {
		final TimerEntrySet entrySet = this.entrySet;

		assertFalse(entrySet.hasTimeoutEntry());
		assertNull(entrySet.checkTimeoutEntry(this.hasNext));

		Entry entry1 = new Entry(false);
		entry1.registerTo(entrySet);

		entry1.setTimeout(1000);

		assertTrue(entry1.schedule());
		assertTrue(entry1.isScheduled());

		// -------------------------------------------------
		Entry entry2 = new Entry(false);
		entry2.registerTo(entrySet);

		entry2.setTimeout(800);

		assertTrue(entry2.schedule());
		assertTrue(entry2.isScheduled());

		// -------------------------------------------------
		this.count = 0;
		entrySet.shuttingDown();

		assertFalse(entry1.isScheduled());
		assertFalse(entry2.isScheduled());

		assertEquals(2, this.count);
	}

	public final void testBasicTimerEntry5() {
		final TimerEntrySet entrySet = this.entrySet;

		assertFalse(entrySet.hasTimeoutEntry());
		assertNull(entrySet.checkTimeoutEntry(this.hasNext));

		Entry entry1 = new Entry(false);
		entry1.registerTo(entrySet);

		entry1.setTimeout(1000);

		assertTrue(entry1.schedule());
		assertTrue(entry1.isScheduled());

		// -------------------------------------------------
		Entry entry2 = new Entry(false);
		entry2.registerTo(entrySet);

		entry2.setTimeout(800);

		assertTrue(entry2.schedule());
		assertTrue(entry2.isScheduled());

		// -------------------------------------------------
		this.count = 0;
		entrySet.clear();

		assertFalse(entry1.isScheduled());
		assertFalse(entry2.isScheduled());

		assertEquals(2, this.count);
	}

}
