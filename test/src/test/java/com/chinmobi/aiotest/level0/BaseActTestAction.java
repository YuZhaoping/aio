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

import com.chinmobi.aio.AIOFuture;
import com.chinmobi.aio.act.AIOActResult;
import com.chinmobi.aio.impl.act.BaseActRequest;
import com.chinmobi.aio.impl.util.TimerEntry;

/**
 * @author <a href="mailto:yuzhaoping1970@gmail.com">Zhaoping Yu</a>
 *
 */
public abstract class BaseActTestAction<T extends AIOActResult> extends AbstractActTestAction<T> {

	protected BaseActTestAction() {
		super();
	}


	/*
	 * Test methods
	 */

	public final void testAddRequest0() {
		try {
			assertTrue(this.timerEntries.isEmpty());

			BufferActEntry entry = new BufferActEntry();

			BaseActRequest<T> request = createRequest(entry);

			assertNotNull(request);

			AIOFuture<T> future = request.future();

			assertNotNull(future);

			assertFalse(future.isCancelled());
			assertFalse(future.isDone());

			assertTrue(request.isTimerScheduled());
			assertFalse(this.timerEntries.isEmpty());

			// ---------------------------------------------
			assertTrue(sessionActor().contains(request));
			assertFalse(request.isQueued());

			assertTrue(entry == request.entry());
			assertEquals(entry.position(), request.position());
			assertEquals(0, request.completedCount());
			assertFalse(request.endOfInput());

			// ---------------------------------------------
			this.session.close(true);

			assertFalse(request.isTimerScheduled());
			assertTrue(this.timerEntries.isEmpty());

			assertFalse(future.isCancelled());
			assertFalse(future.isDone());

			assertEquals(0, this.helper.cancelledCount);

			assertFalse(containsFree(request));

			this.session.getEventHandler().close();

			assertTrue(future.isCancelled());
			assertTrue(future.isDone());

			assertEquals(1, this.helper.cancelledCount);

			assertTrue(containsFree(request));

		} catch (Exception ex) {
			fail(ex);
		}
	}

	public final void testAddRequest1() {
		try {
			BufferActEntry entry = new BufferActEntry();

			BaseActRequest<T> request = createRequest(entry);

			assertNotNull(request);

			AIOFuture<T> future = request.future();

			assertNotNull(future);

			assertFalse(future.isCancelled());
			assertFalse(future.isDone());

			assertTrue(request.isTimerScheduled());

			// ---------------------------------------------
			assertTrue(sessionActor().contains(request));
			assertFalse(request.isQueued());

			// ---------------------------------------------
			assertFalse(containsFree(request));

			request.close();

			assertFalse(request.isTimerScheduled());

			assertFalse(sessionActor().contains(request));

			assertTrue(future.isCancelled());
			assertTrue(future.isDone());

			assertEquals(1, this.helper.cancelledCount);

			assertTrue(containsFree(request));

		} catch (Exception ex) {
			fail(ex);
		}
	}

	public final void testAddRequest2() {
		try {
			BaseActRequest<T> request = createRequest(new BufferActEntry());

			// ---------------------------------------------
			assertNotNull(request);
			assertTrue(request.isTimerScheduled());

			assertTrue(sessionActor().contains(request));
			assertFalse(request.isQueued());


			BaseActRequest<T> request1 = createRequest(new BufferActEntry());

			assertNotNull(request1);
			assertFalse(request1.isTimerScheduled());

			assertTrue(sessionActor().contains(request1));
			assertTrue(request1.isQueued());


			BaseActRequest<T> request2 = createRequest(new BufferActEntry());

			assertNotNull(request2);
			assertFalse(request2.isTimerScheduled());

			assertTrue(sessionActor().contains(request2));
			assertTrue(request2.isQueued());

			// ---------------------------------------------
			assertFalse(containsFree(request1));

			request1.close();

			assertFalse(sessionActor().contains(request1));

			assertEquals(1, this.helper.cancelledCount);

			assertTrue(request.isTimerScheduled());
			assertFalse(request2.isTimerScheduled());

			assertTrue(containsFree(request1));

			// ---------------------------------------------
			assertFalse(containsFree(request));

			request.close();

			assertFalse(sessionActor().contains(request));

			assertEquals(2, this.helper.cancelledCount);

			assertTrue(request2.isTimerScheduled());

			assertTrue(containsFree(request));

			// ---------------------------------------------
			assertFalse(containsFree(request2));

			request2.close();

			assertFalse(sessionActor().contains(request2));

			assertEquals(3, this.helper.cancelledCount);

			assertTrue(containsFree(request2));

		} catch (Exception ex) {
			fail(ex);
		}
	}

	public final void testAddRequest3() {
		try {
			BaseActRequest<T> request = createRequest(new BufferActEntry());

			// ---------------------------------------------
			assertNotNull(request);
			assertTrue(request.isTimerScheduled());

			assertTrue(sessionActor().contains(request));
			assertFalse(request.isQueued());


			BaseActRequest<T> request1 = createRequest(new BufferActEntry());

			assertNotNull(request1);
			assertFalse(request1.isTimerScheduled());

			assertTrue(sessionActor().contains(request1));
			assertTrue(request1.isQueued());


			BaseActRequest<T> request2 = createRequest(new BufferActEntry());

			assertNotNull(request2);
			assertFalse(request2.isTimerScheduled());

			assertTrue(sessionActor().contains(request2));
			assertTrue(request2.isQueued());

			// ---------------------------------------------
			this.session.close(true);

			assertFalse(request.isTimerScheduled());
			assertTrue(this.timerEntries.isEmpty());

			assertFalse(containsFree(request));
			assertFalse(containsFree(request1));
			assertFalse(containsFree(request2));

			this.session.getEventHandler().close();

			assertEquals(3, this.helper.cancelledCount);

			assertTrue(containsFree(request));
			assertTrue(containsFree(request1));
			assertTrue(containsFree(request2));

		} catch (Exception ex) {
			fail(ex);
		}
	}

	public final void testCancelRequest0() {
		try {
			BaseActRequest<T> request = createRequest(new BufferActEntry());

			assertNotNull(request);

			AIOFuture<T> future = request.future();

			assertNotNull(future);

			assertFalse(future.isCancelled());
			assertFalse(future.isDone());

			assertTrue(request.isTimerScheduled());
			assertFalse(this.timerEntries.isEmpty());

			assertTrue(sessionActor().contains(request));
			assertFalse(request.isQueued());

			// ---------------------------------------------

			assertTrue(future.cancel(true));

			assertTrue(future.isCancelled());
			assertTrue(future.isDone());

			assertEquals(0, this.helper.cancelledCount);

			assertFalse(request.isTimerScheduled());
			assertTrue(this.timerEntries.isEmpty());

			assertTrue(sessionActor().contains(request));
			assertFalse(request.isQueued());

			// ---------------------------------------------
			assertFalse(containsFree(request));

			this.session.getEventHandler().close();

			assertFalse(sessionActor().contains(request));

			assertEquals(0, this.helper.cancelledCount);

			assertTrue(containsFree(request));

		} catch (Exception ex) {
			fail(ex);
		}
	}

	public final void testCancelRequest1() {
		try {
			BaseActRequest<T> request = createRequest(new BufferActEntry());

			assertNotNull(request);

			assertTrue(request.isTimerScheduled());
			assertFalse(this.timerEntries.isEmpty());

			assertTrue(sessionActor().contains(request));
			assertFalse(request.isQueued());

			// ---------------------------------------------

			assertTrue(request.future().cancel(true));

			assertEquals(0, this.helper.cancelledCount);

			assertFalse(request.isTimerScheduled());
			assertTrue(this.timerEntries.isEmpty());

			assertTrue(sessionActor().contains(request));
			assertFalse(request.isQueued());


			BaseActRequest<T> request1 = createRequest(new BufferActEntry());

			assertTrue(request1.isTimerScheduled());
			assertFalse(this.timerEntries.isEmpty());

			assertTrue(sessionActor().contains(request1));
			assertTrue(request1.isQueued());


			assertTrue(request1.future().cancel(true));

			assertEquals(0, this.helper.cancelledCount);

			assertFalse(request1.isTimerScheduled());
			assertTrue(this.timerEntries.isEmpty());

			assertTrue(sessionActor().contains(request1));
			assertTrue(request1.isQueued());

			// ---------------------------------------------
			assertFalse(containsFree(request));
			assertFalse(containsFree(request1));

			this.session.getEventHandler().close();

			assertFalse(sessionActor().contains(request));
			assertFalse(sessionActor().contains(request1));

			assertEquals(0, this.helper.cancelledCount);

			assertTrue(containsFree(request));
			assertTrue(containsFree(request1));

		} catch (Exception ex) {
			fail(ex);
		}
	}

	public final void testCancelRequest2() {
		try {
			BaseActRequest<T> request = createRequest(new BufferActEntry());

			assertNotNull(request);

			assertTrue(request.isTimerScheduled());
			assertFalse(this.timerEntries.isEmpty());

			assertTrue(sessionActor().contains(request));
			assertFalse(request.isQueued());

			// ---------------------------------------------
			BaseActRequest<T> request1 = createRequest(new BufferActEntry());

			assertFalse(request1.isTimerScheduled());
			assertFalse(this.timerEntries.isEmpty());

			assertTrue(sessionActor().contains(request1));
			assertTrue(request1.isQueued());


			BaseActRequest<T> request2 = createRequest(new BufferActEntry());

			assertFalse(request2.isTimerScheduled());
			assertFalse(this.timerEntries.isEmpty());

			assertTrue(sessionActor().contains(request2));
			assertTrue(request2.isQueued());

			// ---------------------------------------------
			assertTrue(request1.future().cancel(true));

			assertEquals(0, this.helper.cancelledCount);

			assertTrue(request.isTimerScheduled());
			assertFalse(this.timerEntries.isEmpty());

			assertTrue(sessionActor().contains(request1));
			assertTrue(request1.isQueued());

			// ---------------------------------------------
			assertTrue(request.future().cancel(true));

			assertEquals(0, this.helper.cancelledCount);

			assertFalse(request.isTimerScheduled());
			assertFalse(this.timerEntries.isEmpty());

			assertTrue(sessionActor().contains(request));
			assertFalse(request.isQueued());

			assertTrue(request2.isTimerScheduled());

			// ---------------------------------------------
			assertTrue(request2.future().cancel(true));

			assertFalse(request2.isTimerScheduled());
			assertTrue(this.timerEntries.isEmpty());

			assertTrue(sessionActor().contains(request2));
			assertTrue(request2.isQueued());

			// ---------------------------------------------
			assertFalse(containsFree(request));
			assertFalse(containsFree(request1));
			assertFalse(containsFree(request2));

			this.session.getEventHandler().close();

			assertFalse(sessionActor().contains(request));
			assertFalse(sessionActor().contains(request1));
			assertFalse(sessionActor().contains(request2));

			assertEquals(0, this.helper.cancelledCount);

			assertTrue(containsFree(request));
			assertTrue(containsFree(request1));
			assertTrue(containsFree(request2));

		} catch (Exception ex) {
			fail(ex);
		}
	}

	public final void testSessionSelected() {
		try {
			BaseActRequest<T> request = createRequest(new BufferActEntry());

			assertNotNull(request);

			assertTrue(request.isTimerScheduled());
			assertFalse(this.timerEntries.isEmpty());

			assertTrue(sessionActor().contains(request));
			assertFalse(request.isQueued());

			// ---------------------------------------------
			BaseActRequest<T> request1 = createRequest(new BufferActEntry());

			assertFalse(request1.isTimerScheduled());
			assertFalse(this.timerEntries.isEmpty());

			assertTrue(sessionActor().contains(request1));
			assertTrue(request1.isQueued());

			// ---------------------------------------------
			sessionActor().handleSessionSelected();

			assertFalse(request.isTimerScheduled());
			assertTrue(this.timerEntries.isEmpty());

			assertTrue(sessionActor().contains(request));
			assertFalse(request.isQueued());

			assertFalse(request1.isTimerScheduled());

			// ---------------------------------------------
			BaseActRequest<T> request2 = createRequest(new BufferActEntry());

			assertFalse(request2.isTimerScheduled());
			assertTrue(this.timerEntries.isEmpty());

			assertTrue(sessionActor().contains(request2));
			assertTrue(request2.isQueued());

			// ---------------------------------------------
			sessionActor().scheduleTimer(0);
			assertTrue(request.isTimerScheduled());
			assertFalse(this.timerEntries.isEmpty());

			assertTrue(request.future().cancel(true));

			assertFalse(request.isTimerScheduled());
			assertTrue(request1.isTimerScheduled());
			assertFalse(request2.isTimerScheduled());
			assertFalse(this.timerEntries.isEmpty());

			// ---------------------------------------------
			assertFalse(containsFree(request));
			assertFalse(containsFree(request1));
			assertFalse(containsFree(request2));

			this.session.getEventHandler().close();

			assertFalse(sessionActor().contains(request));
			assertFalse(sessionActor().contains(request1));
			assertFalse(sessionActor().contains(request2));

			assertTrue(containsFree(request));
			assertTrue(containsFree(request1));
			assertTrue(containsFree(request2));

		} catch (Exception ex) {
			fail(ex);
		}
	}

	public final void testGetCurrentRequest0() {
		try {
			BaseActRequest<T> request = createRequest(new BufferActEntry());

			assertNotNull(request);

			assertTrue(sessionActor().contains(request));
			assertFalse(request.isQueued());

			assertEquals(0, this.helper.initiateCount);

			assertTrue(request == sessionActor().currentRequest());

			assertEquals(1, this.helper.initiateCount);

			assertTrue(request == sessionActor().currentRequest());

			assertEquals(1, this.helper.initiateCount);

			request.future().cancel(true);

			assertNull(sessionActor().currentRequest());

			assertEquals(1, this.helper.initiateCount);

			assertTrue(containsFree(request));

		} catch (Exception ex) {
			fail(ex);
		}
	}

	public final void testGetCurrentRequest1() {
		try {
			BaseActRequest<T> request = createRequest(new BufferActEntry());

			assertNotNull(request);

			assertTrue(sessionActor().contains(request));
			assertFalse(request.isQueued());

			BaseActRequest<T> request1 = createRequest(new BufferActEntry());

			assertTrue(sessionActor().contains(request1));
			assertTrue(request1.isQueued());

			BaseActRequest<T> request2 = createRequest(new BufferActEntry());

			assertTrue(sessionActor().contains(request2));
			assertTrue(request2.isQueued());

			// ---------------------------------------------
			request.future().cancel(true);

			assertTrue(sessionActor().contains(request));

			request1.future().cancel(true);

			assertTrue(sessionActor().contains(request1));

			assertEquals(0, this.helper.initiateCount);

			assertTrue(request2 == sessionActor().currentRequest());

			assertEquals(1, this.helper.initiateCount);

			assertTrue(containsFree(request));
			assertTrue(containsFree(request1));

			// ---------------------------------------------
			request2.future().cancel(true);

			assertNull(sessionActor().currentRequest());

			assertEquals(1, this.helper.initiateCount);

			assertTrue(containsFree(request2));

		} catch (Exception ex) {
			fail(ex);
		}
	}

	public final void testRequestTimeout0() {
		try {
			BaseActRequest<T> request = createRequest(new BufferActEntry());

			assertNotNull(request);

			assertTrue(request.isTimerScheduled());
			assertFalse(this.timerEntries.isEmpty());

			assertTrue(sessionActor().contains(request));
			assertFalse(request.isQueued());

			assertEquals(0, this.helper.timeoutCount);

			// ---------------------------------------------
			final long now = System.currentTimeMillis() + 1000;

			assertTrue(this.timerEntries.hasTimeoutEntry(now));

			runAllTimeouts(now);

			assertEquals(1, this.helper.timeoutCount);

			assertFalse(request.isTimerScheduled());

			assertFalse(sessionActor().contains(request));

			assertTrue(containsFree(request));

		} catch (Exception ex) {
			fail(ex);
		}
	}

	public final void testRequestTimeout1() {
		try {
			BaseActRequest<T> request = createRequest(new BufferActEntry());

			assertNotNull(request);

			assertTrue(request.isTimerScheduled());
			assertFalse(this.timerEntries.isEmpty());

			assertTrue(sessionActor().contains(request));
			assertFalse(request.isQueued());


			BaseActRequest<T> request1 = createRequest(new BufferActEntry());

			assertFalse(request1.isTimerScheduled());
			assertFalse(this.timerEntries.isEmpty());

			assertTrue(sessionActor().contains(request1));
			assertTrue(request1.isQueued());

			assertEquals(0, this.helper.timeoutCount);

			// ---------------------------------------------
			final long now = System.currentTimeMillis() + 1000;

			assertTrue(this.timerEntries.hasTimeoutEntry(now));

			runAllTimeouts(now);

			assertEquals(1, this.helper.timeoutCount);

			assertFalse(request.isTimerScheduled());

			assertFalse(sessionActor().contains(request));

			assertTrue(containsFree(request));


			assertTrue(request1.isTimerScheduled());
			assertFalse(this.timerEntries.isEmpty());

			assertTrue(sessionActor().contains(request1));
			assertFalse(request1.isQueued());

			// ---------------------------------------------
			request1.close();

		} catch (Exception ex) {
			fail(ex);
		}
	}

	public final void testRequestFailed() {
		try {
			BaseActRequest<T> request = createRequest(new BufferActEntry());

			assertNotNull(request);

			assertTrue(sessionActor().contains(request));
			assertFalse(request.isQueued());

			assertEquals(0, this.helper.failedCount);

			this.helper.testCase = -1;

			assertNull(sessionActor().currentRequest());

			assertEquals(1, this.helper.failedCount);

			assertTrue(containsFree(request));

		} catch (Exception ex) {
			fail(ex);
		}
	}

	private final void runAllTimeouts(final long now) {
		final boolean[] hasNext = new boolean[1];

		TimerEntry entry = this.timerEntries.checkTimeoutEntry(now, hasNext);
		while (entry != null) {
			entry.getRunnable().run();

			if (hasNext[0]) {
				entry = this.timerEntries.checkTimeoutEntry(now, hasNext);
			} else {
				break;
			}
		}
	}

}
