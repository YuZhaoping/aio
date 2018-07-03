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

import java.util.concurrent.TimeUnit;

import com.chinmobi.aio.impl.util.TimerCallableEntry;
import com.chinmobi.aio.impl.util.TimerEntry;
import com.chinmobi.aio.impl.util.TimerEntrySet;
import com.chinmobi.aio.util.AIOTimer;
import com.chinmobi.aio.util.AIOTimerCallable;
import com.chinmobi.testapp.BaseTestAction;

/**
 * @author <a href="mailto:yuzhaoping1970@gmail.com">Zhaoping Yu</a>
 *
 */
public class TimerCallableTestAction extends BaseTestAction
	implements TimerEntrySet.Observer, AIOTimerCallable {

	private final TimerEntrySet entrySet;

	private int cancelledStatus;


	public TimerCallableTestAction() {
		super();
		this.entrySet = new TimerEntrySet(this);
		this.cancelledStatus = -1;
	}


	public final void notifyTimerEntryAdded(final TimerEntrySet entrySet, final TimerEntry timerEntry) {
	}

	public final void notifyTimerEntryRuntimeException(final TimerEntry timerEntry, final RuntimeException ex) {
	}

	public final void aioTimerCall(final AIOTimer timer, final Object attachment, final boolean isCancelled) {
		if (isCancelled) {
			this.cancelledStatus = 1;
		} else {
			this.cancelledStatus = 0;
		}
	}


	/*
	 * Test methods
	 */

	public final void testTimerCallableEntry0() {
		final TimerEntrySet entrySet = this.entrySet;
		this.cancelledStatus = -1;

		TimerCallableEntry entry = entrySet.schedule(null, this, 0, TimeUnit.MILLISECONDS, null);

		assertTrue(entry.isScheduled());

		assertFalse(entry.future().isCancelled());
		assertFalse(entry.future().isDone());
		assertNull(entry.future().attachment());

		assertTrue(entry.future().cancel());
		assertTrue(entry.future().isCancelled());
		assertTrue(entry.future().isDone());

		assertTrue(entry.future().cancel());

		assertFalse(entry.isScheduled());
		assertEquals(-1, this.cancelledStatus);
	}

	public final void testTimerCallableEntry1() {
		final TimerEntrySet entrySet = this.entrySet;
		this.cancelledStatus = -1;

		TimerCallableEntry entry = entrySet.schedule(null, this, 1000, TimeUnit.MILLISECONDS, null);

		assertTrue(entry.isScheduled());

		assertFalse(entry.future().isCancelled());
		assertFalse(entry.future().isDone());
		assertNull(entry.future().attachment());

		entry.cancel();

		assertFalse(entry.future().isCancelled());
		assertTrue(entry.future().isDone());

		assertFalse(entry.future().cancel());

		assertTrue(entry.isScheduled());
		assertEquals(-1, this.cancelledStatus);

		assertTrue(entry == entrySet.checkTimeoutEntry());

		assertFalse(entry.isScheduled());

		entry.getRunnable().run();

		assertEquals(1, this.cancelledStatus);
	}

	public final void testTimerCallableEntry2() {
		final TimerEntrySet entrySet = this.entrySet;
		this.cancelledStatus = -1;

		TimerCallableEntry entry = entrySet.schedule(null, this, 1000, TimeUnit.MILLISECONDS, null);

		assertTrue(entry.isScheduled());

		assertFalse(entry.future().isCancelled());
		assertFalse(entry.future().isDone());
		assertNull(entry.future().attachment());

		assertTrue(entry.isScheduled());
		assertEquals(-1, this.cancelledStatus);

		long now = System.currentTimeMillis();
		now += 1000;

		assertTrue(entry == entrySet.checkTimeoutEntry(now));

		assertFalse(entry.isScheduled());
		assertFalse(entry.future().isCancelled());
		assertFalse(entry.future().isDone());

		entry.getRunnable().run();

		assertFalse(entry.future().cancel());

		assertFalse(entry.future().isCancelled());
		assertTrue(entry.future().isDone());

		assertEquals(0, this.cancelledStatus);
	}

	public final void testTimerCallableEntry3() {
		final TimerEntrySet entrySet = this.entrySet;
		this.cancelledStatus = -1;

		TimerCallableEntry entry = entrySet.schedule(null, this, 1000, TimeUnit.MILLISECONDS, null);

		assertTrue(entry.isScheduled());

		assertFalse(entry.future().isCancelled());
		assertFalse(entry.future().isDone());
		assertNull(entry.future().attachment());

		assertTrue(entry.isScheduled());
		assertEquals(-1, this.cancelledStatus);

		long now = System.currentTimeMillis();
		now += 1000;

		assertTrue(entry == entrySet.checkTimeoutEntry(now));

		assertFalse(entry.isScheduled());
		assertFalse(entry.future().isCancelled());
		assertFalse(entry.future().isDone());

		assertTrue(entry.future().cancel());

		entry.getRunnable().run();

		assertTrue(entry.future().isCancelled());
		assertTrue(entry.future().isDone());

		assertEquals(-1, this.cancelledStatus);
	}

	public final void testTimerCallableEntry4() {
		final TimerEntrySet entrySet = this.entrySet;
		this.cancelledStatus = -1;

		TimerCallableEntry entry = entrySet.schedule(null, this, 0, TimeUnit.MILLISECONDS, null);

		assertTrue(entry.isScheduled());

		assertFalse(entry.future().isCancelled());
		assertFalse(entry.future().isDone());
		assertNull(entry.future().attachment());

		entrySet.shuttingDown();

		assertFalse(entry.future().isCancelled());
		assertTrue(entry.future().isDone());

		assertTrue(entry.isScheduled());
		assertEquals(-1, this.cancelledStatus);

		entrySet.clear();

		assertFalse(entry.isScheduled());
		assertEquals(1, this.cancelledStatus);
	}

}
