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

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import com.chinmobi.aio.AIOFuture;
import com.chinmobi.aio.AIOFutureCallback;
import com.chinmobi.aio.AIOFutureCancellable;
import com.chinmobi.aio.impl.nio.BasicFuture;
import com.chinmobi.aio.impl.nio.ExceptionHandler;
import com.chinmobi.aio.impl.nio.FutureDoAccomplishCallback;
import com.chinmobi.aio.impl.nio.FutureReleaseCallback;
import com.chinmobi.app.action.ActionContext;
import com.chinmobi.testapp.BaseTestAction;

/**
 * @author <a href="mailto:yuzhaoping1970@gmail.com">Zhaoping Yu</a>
 *
 */
public final class FutureTestAction extends BaseTestAction
	implements ExceptionHandler, FutureReleaseCallback, AIOFutureCancellable,
		FutureDoAccomplishCallback<Object> {


	private static final class Future extends BasicFuture<Object> {

		Future(final ExceptionHandler handler) {
			super(handler);
		}


		@Override
		protected final void set(final FutureReleaseCallback releaseCallback,
				final AIOFutureCancellable cancellable,
				final AIOFutureCallback<Object> callback, final Object attachment) {
			super.set(releaseCallback, cancellable, callback, attachment);
		}

		@Override
		protected final void internalRelease() {
			super.internalRelease();
		}

		@Override
		protected final boolean initiate() {
			return super.initiate();
		}

		@Override
		protected final boolean cancelled() {
			return super.cancelled();
		}

		@Override
		protected final boolean accomplished(final Object result, final FutureDoAccomplishCallback<Object> doCallback) {
			return super.accomplished(result, doCallback);
		}

		@Override
		protected final boolean timeout() {
			return super.timeout();
		}

		@Override
		protected final boolean failed(final Throwable cause) {
			return super.failed(cause);
		}

	}

	private final class FutureCallback implements AIOFutureCallback<Object> {

		public final void initiate(final AIOFuture<Object> future,
				final Object attachment, final Object result) {
			FutureTestAction.this.initiateCount++;

			FutureTestAction.this.callbackAttachment = attachment;
			FutureTestAction.this.callbackResult = result;

			switch (FutureTestAction.this.testCase) {
			case -1:
				future.cancel(true);
				break;

			default:
				break;
			}
		}

		public final void accomplished(final AIOFuture<Object> future,
				final Object attachment, final Object result) {
			FutureTestAction.this.accomplishedCount++;

			FutureTestAction.this.callbackAttachment = attachment;
			FutureTestAction.this.callbackResult = result;

			if (1 == FutureTestAction.this.testCase) {
				future.release();
			}
		}

		public final void timeout(final AIOFuture<Object> future,
				final Object attachment, final Object result) {
			FutureTestAction.this.timeoutCount++;

			FutureTestAction.this.callbackAttachment = attachment;
			FutureTestAction.this.callbackResult = result;

			if (1 == FutureTestAction.this.testCase) {
				future.release();
			}
		}

		public final void failed(final AIOFuture<Object> future,
				final Object attachment, final Object result, final Throwable cause) {
			FutureTestAction.this.failedCount++;

			FutureTestAction.this.callbackAttachment = attachment;
			FutureTestAction.this.callbackResult = result;
			FutureTestAction.this.callbackCause = cause;

			if (1 == FutureTestAction.this.testCase) {
				future.release();
			}
		}

		public final void cancelled(final AIOFuture<Object> future,
				final Object attachment, final Object result) {
			FutureTestAction.this.cancelledCount++;

			FutureTestAction.this.callbackAttachment = attachment;
			FutureTestAction.this.callbackResult = result;

			if (1 == FutureTestAction.this.testCase) {
				future.release();
			}
		}

	}


	private int testCase;

	private int initiateCount;
	private int accomplishedCount;
	private int timeoutCount;
	private int failedCount;
	private int cancelledCount;

	private int releasedCount;

	private Object callbackAttachment;
	private Object callbackResult;
	private Throwable callbackCause;


	public FutureTestAction() {
		super();
	}


	@Override
	public final void init(final ActionContext context) {
		super.init(context);
	}

	@Override
	public final void destroy() {
		super.destroy();
	}

	@Override
	protected final void setUp(final String methodName) throws Exception {
		this.testCase = 0;

		this.initiateCount = 0;
		this.accomplishedCount = 0;
		this.timeoutCount = 0;
		this.failedCount = 0;
		this.cancelledCount = 0;

		this.releasedCount = 0;

		this.callbackAttachment = null;
		this.callbackResult = null;
		this.callbackCause = null;
	}

	@Override
	protected final void tearDown() throws Exception {
	}

	public final void handleUncaughtException(final Throwable ex) {
	}

	public final void futureReleased() {
		this.releasedCount++;
	}

	public final boolean cancel(final boolean mayInterruptIfRunning) {
		this.cancelledCount++;
		return true;
	}

	public final void futureDoAccomplish(final Object result) {
	}

	private final Future createFuture(final AIOFutureCallback<Object> callback, final Object attachment) {
		final Future future = new Future(this);

		future.set(this, this, callback, attachment);

		return future;
	}

	/*
	 * Test methods
	 */

	public final void testInitiate() {
		final Object attachment = new Object();
		Future future = createFuture(new FutureCallback(), attachment);

		assertTrue(attachment == future.attachment());

		assertTrue(future.initiate());
		assertEquals(1, this.initiateCount);

		assertTrue(this.callbackAttachment == attachment);
		assertNull(this.callbackResult);
		assertNull(this.callbackCause);

		// -------------------------------------------------
		future = createFuture(null, null);

		assertTrue(future.initiate());

		assertEquals(1, this.initiateCount);

		// -------------------------------------------------
		future = createFuture(new FutureCallback(), null);

		this.testCase = -1;

		assertFalse(future.initiate());

		assertEquals(1, this.cancelledCount);

		assertEquals(2, this.initiateCount);

		// -------------------------------------------------
		future = createFuture(new FutureCallback(), null);

		this.testCase = 0;
		future.cancel(true);

		assertEquals(2, this.cancelledCount);

		assertFalse(future.initiate());

		assertEquals(2, this.initiateCount);

		// -------------------------------------------------
		assertEquals(0, this.accomplishedCount);
		assertEquals(0, this.timeoutCount);
		assertEquals(0, this.failedCount);
	}

	public final void testRelease() {
		final Object attachment = new Object();
		Future future = createFuture(new FutureCallback(), attachment);

		assertTrue(attachment == future.attachment());

		assertFalse(future.isCancelled());
		assertFalse(future.isDone());

		future.release();

		assertTrue(future.isCancelled());
		assertTrue(future.isDone());

		assertEquals(1, this.cancelledCount);

		assertEquals(0, this.releasedCount);

		assertTrue(attachment == future.attachment());

		// -------------------------------------------------
		future.release();

		assertTrue(future.isCancelled());
		assertTrue(future.isDone());

		assertEquals(1, this.cancelledCount);

		assertEquals(0, this.releasedCount);

		assertTrue(attachment == future.attachment());

		// -------------------------------------------------
		future.internalRelease();

		assertTrue(future.isCancelled());
		assertTrue(future.isDone());

		assertEquals(1, this.cancelledCount);

		assertEquals(1, this.releasedCount);

		assertNull(future.attachment());

		// -------------------------------------------------
		this.cancelledCount = 0;
		this.releasedCount = 0;

		future = createFuture(new FutureCallback(), attachment);

		future.cancel(true);

		assertTrue(future.isCancelled());
		assertTrue(future.isDone());

		assertEquals(1, this.cancelledCount);

		future.release();

		assertTrue(future.isCancelled());
		assertTrue(future.isDone());

		assertEquals(1, this.cancelledCount);

		assertEquals(0, this.releasedCount);

		assertTrue(attachment == future.attachment());

		// =================================================
		this.cancelledCount = 0;
		this.releasedCount = 0;

		future = createFuture(new FutureCallback(), attachment);

		future.cancelled();

		assertTrue(future.isCancelled());
		assertTrue(future.isDone());

		assertEquals(1, this.cancelledCount);

		assertEquals(0, this.releasedCount);

		assertTrue(attachment == future.attachment());

		future.release();

		assertEquals(1, this.releasedCount);

		assertNull(future.attachment());

		// -------------------------------------------------
		future.release();

		assertEquals(1, this.releasedCount);
		// -------------------------------------------------
	}

	public final void testCancel() {
		final Object attachment = new Object();
		Future future = createFuture(new FutureCallback(), attachment);

		assertTrue(attachment == future.attachment());

		assertFalse(future.isCancelled());
		assertFalse(future.isDone());

		assertTrue(future.cancel(true));

		assertTrue(future.isCancelled());
		assertTrue(future.isDone());

		assertEquals(1, this.cancelledCount);

		assertEquals(0, this.releasedCount);

		assertNull(this.callbackAttachment);
		assertNull(this.callbackResult);
		assertNull(this.callbackCause);

		assertTrue(attachment == future.attachment());

		// -------------------------------------------------
		assertFalse(future.cancel(true));

		assertEquals(1, this.cancelledCount);

		assertEquals(0, this.releasedCount);

		assertTrue(attachment == future.attachment());

		// -------------------------------------------------
		assertFalse(future.cancelled());
		assertFalse(future.accomplished(null, null));
		assertFalse(future.timeout());
		assertFalse(future.failed(null));

		assertNull(this.callbackAttachment);
		assertNull(this.callbackResult);
		assertNull(this.callbackCause);

		// -------------------------------------------------
		try {
			assertNull(future.get());
		} catch (Exception ex) {
			fail(ex);
		}

		try {
			assertNull(future.get(1000, TimeUnit.MILLISECONDS));
		} catch (Exception ex) {
			fail(ex);
		}

		// -------------------------------------------------
		future.release();

		assertEquals(1, this.cancelledCount);

		assertEquals(0, this.releasedCount);

		assertTrue(attachment == future.attachment());

		// -------------------------------------------------
		future.internalRelease();

		assertEquals(1, this.cancelledCount);

		assertEquals(1, this.releasedCount);

		assertNull(future.attachment());

		// -------------------------------------------------
	}

	public final void testCancelled() {
		final Object attachment = new Object();
		Future future = createFuture(new FutureCallback(), attachment);

		assertTrue(attachment == future.attachment());

		assertFalse(future.isCancelled());
		assertFalse(future.isDone());

		assertTrue(future.cancelled());

		assertTrue(future.isCancelled());
		assertTrue(future.isDone());

		assertEquals(1, this.cancelledCount);

		assertEquals(0, this.releasedCount);

		assertTrue(this.callbackAttachment == attachment);
		assertNull(this.callbackResult);
		assertNull(this.callbackCause);

		assertTrue(attachment == future.attachment());

		// -------------------------------------------------
		this.callbackAttachment = null;

		assertFalse(future.cancel(true));

		assertEquals(1, this.cancelledCount);

		assertEquals(0, this.releasedCount);

		assertNull(this.callbackAttachment);
		assertNull(this.callbackResult);
		assertNull(this.callbackCause);

		assertTrue(attachment == future.attachment());

		// -------------------------------------------------
		assertFalse(future.cancelled());
		assertFalse(future.accomplished(new Object(), null));
		assertFalse(future.timeout());
		assertFalse(future.failed(new Exception()));

		assertNull(this.callbackAttachment);
		assertNull(this.callbackResult);
		assertNull(this.callbackCause);

		// -------------------------------------------------
		try {
			assertNull(future.get());
		} catch (Exception ex) {
			fail(ex);
		}

		try {
			assertNull(future.get(1000, TimeUnit.MILLISECONDS));
		} catch (Exception ex) {
			fail(ex);
		}

		// -------------------------------------------------
		future.release();

		assertEquals(1, this.cancelledCount);

		assertEquals(1, this.releasedCount);

		assertNull(future.attachment());

		// -------------------------------------------------
		future.internalRelease();

		assertEquals(1, this.cancelledCount);

		assertEquals(1, this.releasedCount);
		// -------------------------------------------------
	}

	public final void testAccomplished() {
		final Object attachment = new Object();
		Future future = createFuture(new FutureCallback(), attachment);

		assertTrue(attachment == future.attachment());

		assertFalse(future.isCancelled());
		assertFalse(future.isDone());

		final Object result = new Object();

		assertTrue(future.accomplished(result, null));

		assertFalse(future.isCancelled());
		assertTrue(future.isDone());

		assertEquals(0, this.cancelledCount);
		assertEquals(1, this.accomplishedCount);
		assertEquals(0, this.timeoutCount);
		assertEquals(0, this.failedCount);
		assertEquals(0, this.releasedCount);

		assertTrue(this.callbackAttachment == attachment);
		assertTrue(this.callbackResult == result);
		assertNull(this.callbackCause);

		assertTrue(attachment == future.attachment());

		// -------------------------------------------------
		assertFalse(future.cancel(true));

		assertEquals(0, this.cancelledCount);
		assertEquals(1, this.accomplishedCount);
		assertEquals(0, this.releasedCount);

		assertTrue(this.callbackAttachment == attachment);
		assertTrue(this.callbackResult == result);
		assertNull(this.callbackCause);

		assertTrue(attachment == future.attachment());

		// -------------------------------------------------
		assertFalse(future.cancelled());
		assertFalse(future.accomplished(new Object(), null));
		assertFalse(future.timeout());
		assertFalse(future.failed(new Exception()));

		assertEquals(0, this.cancelledCount);
		assertEquals(1, this.accomplishedCount);
		assertEquals(0, this.timeoutCount);
		assertEquals(0, this.failedCount);
		assertEquals(0, this.releasedCount);

		assertTrue(this.callbackAttachment == attachment);
		assertTrue(this.callbackResult == result);
		assertNull(this.callbackCause);

		// -------------------------------------------------
		try {
			assertTrue(future.get() == result);
		} catch (Exception ex) {
			fail(ex);
		}

		try {
			assertTrue(future.get(1000, TimeUnit.MILLISECONDS) == result);
		} catch (Exception ex) {
			fail(ex);
		}

		// -------------------------------------------------
		future.release();

		assertEquals(0, this.cancelledCount);
		assertEquals(1, this.accomplishedCount);
		assertEquals(1, this.releasedCount);

		assertNull(future.attachment());

		try {
			assertNull(future.get());
		} catch (Exception ex) {
			fail(ex);
		}

		try {
			assertNull(future.get(1000, TimeUnit.MILLISECONDS));
		} catch (Exception ex) {
			fail(ex);
		}

		// -------------------------------------------------
		future.internalRelease();

		assertEquals(0, this.cancelledCount);
		assertEquals(1, this.accomplishedCount);
		assertEquals(1, this.releasedCount);
		// -------------------------------------------------
	}

	public final void testTimeout() {
		final Object attachment = new Object();
		Future future = createFuture(new FutureCallback(), attachment);

		assertTrue(attachment == future.attachment());

		assertFalse(future.isCancelled());
		assertFalse(future.isDone());

		assertTrue(future.timeout());

		assertFalse(future.isCancelled());
		assertTrue(future.isDone());

		assertEquals(0, this.cancelledCount);
		assertEquals(0, this.accomplishedCount);
		assertEquals(1, this.timeoutCount);
		assertEquals(0, this.failedCount);
		assertEquals(0, this.releasedCount);

		assertTrue(this.callbackAttachment == attachment);
		assertNull(this.callbackResult);
		assertNull(this.callbackCause);

		assertTrue(attachment == future.attachment());

		// -------------------------------------------------
		assertFalse(future.cancel(true));

		assertEquals(0, this.cancelledCount);
		assertEquals(1, this.timeoutCount);
		assertEquals(0, this.releasedCount);

		assertTrue(this.callbackAttachment == attachment);
		assertNull(this.callbackResult);
		assertNull(this.callbackCause);

		assertTrue(attachment == future.attachment());

		// -------------------------------------------------
		assertFalse(future.cancelled());
		assertFalse(future.accomplished(new Object(), null));
		assertFalse(future.timeout());
		assertFalse(future.failed(new Exception()));

		assertEquals(0, this.cancelledCount);
		assertEquals(0, this.accomplishedCount);
		assertEquals(1, this.timeoutCount);
		assertEquals(0, this.failedCount);
		assertEquals(0, this.releasedCount);

		assertTrue(this.callbackAttachment == attachment);
		assertNull(this.callbackResult);
		assertNull(this.callbackCause);

		// -------------------------------------------------
		try {
			assertNull(future.get());
		} catch (Exception ex) {
			fail(ex);
		}

		try {
			assertNull(future.get(1000, TimeUnit.MILLISECONDS));
		} catch (Exception ex) {
			fail(ex);
		}

		// -------------------------------------------------
		future.release();

		assertEquals(0, this.cancelledCount);
		assertEquals(1, this.timeoutCount);
		assertEquals(1, this.releasedCount);

		assertNull(future.attachment());

		// -------------------------------------------------
		future.internalRelease();

		assertEquals(0, this.cancelledCount);
		assertEquals(1, this.timeoutCount);
		assertEquals(1, this.releasedCount);
		// -------------------------------------------------
	}

	public final void testFailed() {
		final Object attachment = new Object();
		Future future = createFuture(new FutureCallback(), attachment);

		assertTrue(attachment == future.attachment());

		assertFalse(future.isCancelled());
		assertFalse(future.isDone());

		final Throwable cause = new Exception();
		assertTrue(future.failed(cause));

		assertFalse(future.isCancelled());
		assertTrue(future.isDone());

		assertEquals(0, this.cancelledCount);
		assertEquals(0, this.accomplishedCount);
		assertEquals(0, this.timeoutCount);
		assertEquals(1, this.failedCount);
		assertEquals(0, this.releasedCount);

		assertTrue(this.callbackAttachment == attachment);
		assertNull(this.callbackResult);
		assertTrue(this.callbackCause == cause);

		assertTrue(attachment == future.attachment());

		// -------------------------------------------------
		assertFalse(future.cancel(true));

		assertEquals(0, this.cancelledCount);
		assertEquals(1, this.failedCount);
		assertEquals(0, this.releasedCount);

		assertTrue(this.callbackAttachment == attachment);
		assertNull(this.callbackResult);
		assertTrue(this.callbackCause == cause);

		assertTrue(attachment == future.attachment());

		// -------------------------------------------------
		assertFalse(future.cancelled());
		assertFalse(future.accomplished(new Object(), null));
		assertFalse(future.timeout());
		assertFalse(future.failed(new Exception()));

		assertEquals(0, this.cancelledCount);
		assertEquals(0, this.accomplishedCount);
		assertEquals(0, this.timeoutCount);
		assertEquals(1, this.failedCount);
		assertEquals(0, this.releasedCount);

		assertTrue(this.callbackAttachment == attachment);
		assertNull(this.callbackResult);
		assertTrue(this.callbackCause == cause);

		// -------------------------------------------------
		Throwable firedCause = null;
		try {
			assertNull(future.get());
		} catch (ExecutionException ex) {
			firedCause = ex.getCause();
		} catch (Exception ex) {
			fail(ex);
		}

		assertTrue(firedCause == cause);

		firedCause = null;
		try {
			assertNull(future.get(1000, TimeUnit.MILLISECONDS));
		} catch (ExecutionException ex) {
			firedCause = ex.getCause();
		} catch (Exception ex) {
			fail(ex);
		}

		assertTrue(firedCause == cause);

		// -------------------------------------------------
		future.release();

		assertEquals(0, this.cancelledCount);
		assertEquals(1, this.failedCount);
		assertEquals(1, this.releasedCount);

		assertNull(future.attachment());

		try {
			assertNull(future.get());
		} catch (Exception ex) {
			fail(ex);
		}

		try {
			assertNull(future.get(1000, TimeUnit.MILLISECONDS));
		} catch (Exception ex) {
			fail(ex);
		}

		// -------------------------------------------------
		future.internalRelease();

		assertEquals(0, this.cancelledCount);
		assertEquals(1, this.failedCount);
		assertEquals(1, this.releasedCount);
		// -------------------------------------------------
	}

}
