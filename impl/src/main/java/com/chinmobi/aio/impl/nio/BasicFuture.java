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
package com.chinmobi.aio.impl.nio;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.chinmobi.aio.AIOFuture;
import com.chinmobi.aio.AIOFutureCallback;
import com.chinmobi.aio.AIOFutureCancellable;
import com.chinmobi.aio.AIOFutureStatus;

/**
 * @author <a href="mailto:yuzhaoping1970@gmail.com">Zhaoping Yu</a>
 *
 */
public class BasicFuture<T> implements AIOFuture<T> {

	private final Object lock;

	private ExceptionHandler exceptionHandler;

	private int waitCount;

	private AIOFutureCallback<T> callback;

	private int status;

	private Throwable cause;

	private T result;

	private FutureReleaseCallback releaseCallback;
	private AIOFutureCancellable cancellable;

	private Object attachment;


	public BasicFuture(final Object lock, final ExceptionHandler exceptionHandler) {
		this.lock = lock;
		this.exceptionHandler = exceptionHandler;
	}

	public BasicFuture(final ExceptionHandler exceptionHandler) {
		this(new Object(), exceptionHandler);
	}


	public final Object lock() {
		return this.lock;
	}

	public final ExceptionHandler exceptionHandler() {
		return this.exceptionHandler;
	}

	public final void setExceptionHandler(final ExceptionHandler exceptionHandler) {
		this.exceptionHandler = exceptionHandler;
	}

	protected void set(final FutureReleaseCallback releaseCallback,
			final AIOFutureCancellable cancellable,
			final AIOFutureCallback<T> callback, final Object attachment) {

		set(releaseCallback, cancellable, callback, attachment, null);
	}

	protected void set(final FutureReleaseCallback releaseCallback,
			final AIOFutureCancellable cancellable,
			final AIOFutureCallback<T> callback, final Object attachment, final T result) {
		this.waitCount = 0;

		this.releaseCallback = releaseCallback;
		this.cancellable = cancellable;

		this.callback = callback;
		this.attachment = attachment;

		this.status = AIOFutureStatus.CASE_UNKNOWN;

		this.cause = null;
		this.result = result;
	}

	protected void internalRelease() {
		final FutureReleaseCallback callback;

		boolean interrupted = false;

		synchronized (this.lock) {
			while (this.waitCount > 0) {
				try {
					this.lock.wait();
				} catch (InterruptedException ex) {
					interrupted = true;
				}
			}

			this.result = null;
			this.cause = null;

			this.attachment = null;

			callback = this.releaseCallback;
			this.releaseCallback = null;
		}

		if (callback != null) {
			try {
				callback.futureReleased();
			} catch (Throwable ignore) {
				handleUncaughtException(ignore);
			}
		}

		if (interrupted) {
			Thread.currentThread().interrupt();
		}
	}

	protected boolean initiate() {
		synchronized (this.lock) {
			if (this.status != AIOFutureStatus.CASE_UNKNOWN) {
				return false;
			}

			if (this.callback == null) {
				return true;
			}

			try {
				this.callback.initiate(this, this.attachment, this.result);
			} catch (RuntimeException ex) {
				throw ex;
			}

			if (this.status != AIOFutureStatus.CASE_UNKNOWN) {
				return false;
			}

			return true;
		}
	}


	private final T checkResult() throws ExecutionException {
		if (this.cause != null) {
			throw new BasicExecutionException(this.cause);
		}
		return this.result;
	}

	private final void handleUncaughtException(Throwable cause) {
		final ExceptionHandler handler = this.exceptionHandler;
		if (handler != null) {
			handler.handleUncaughtException(cause);
		}
	}

	/* (non-Javadoc)
	 * @see com.chinmobi.aio.AIOFuture#release()
	 */
	public final void release() {
		synchronized (this.lock) {
			switch (this.status) {
			case AIOFutureStatus.CASE_UNKNOWN:
				cancel(true);
				return;

			case AIOFutureStatus.CASE_USER_CANCELLED:
				return;

			default:
			}
		}
		internalRelease();
	}

	/* (non-Javadoc)
	 * @see java.util.concurrent.Future#cancel(boolean mayInterruptIfRunning)
	 */
	public final boolean cancel(final boolean mayInterruptIfRunning) {
		synchronized (this.lock) {
			if (this.status != AIOFutureStatus.CASE_UNKNOWN) {
				return false;
			}

			this.status = AIOFutureStatus.CASE_USER_CANCELLED;

			final AIOFutureCancellable cancellable = this.cancellable;
			this.cancellable = null;

			this.callback = null;

			if (cancellable != null) {
				cancellable.cancel(mayInterruptIfRunning);
			}

			if (this.waitCount > 0) {
				this.lock.notifyAll();
			}
		}
		return true;
	}

	/* (non-Javadoc)
	 * @see java.util.concurrent.Future#get()
	 */
	public final T get() throws InterruptedException, ExecutionException {
		synchronized (this.lock) {
			while (this.status == AIOFutureStatus.CASE_UNKNOWN) {
				++this.waitCount;
				try {
					this.lock.wait();
				} finally {
					--this.waitCount;
				}
			}
			this.lock.notifyAll();
			return checkResult();
		}
	}

	/* (non-Javadoc)
	 * @see java.util.concurrent.Future#get(long timeout, TimeUnit unit)
	 */
	public final T get(final long timeout, final TimeUnit unit)
			throws InterruptedException, ExecutionException, TimeoutException {

		final long msecs = unit.toMillis(timeout);
		final long startTime = (msecs <= 0) ? 0 : System.currentTimeMillis();
		long waitTime = msecs;

		synchronized (this.lock) {
			for (;;) {
				if (this.status != AIOFutureStatus.CASE_UNKNOWN) {
					this.lock.notifyAll();
					return checkResult();
				} else
				if (waitTime <= 0) {
					this.lock.notifyAll();
					throw new TimeoutException();
				}

				++this.waitCount;
				try {
					this.lock.wait(waitTime);
				} finally {
					--this.waitCount;
				}
				waitTime = msecs - (System.currentTimeMillis() - startTime);
			}
		}
	}

	/* (non-Javadoc)
	 * @see java.util.concurrent.Future#isCancelled()
	 */
	public final boolean isCancelled() {
		synchronized (this.lock) {
			return (this.status  < 0);
		}
	}

	/* (non-Javadoc)
	 * @see java.util.concurrent.Future#isDone()
	 */
	public final boolean isDone() {
		synchronized (this.lock) {
			return (this.status != AIOFutureStatus.CASE_UNKNOWN);
		}
	}

	/* (non-Javadoc)
	 * @see com.chinmobi.aio.AIOFuture#status()
	 */
	public final int status() {
		synchronized (this.lock) {
			return this.status;
		}
	}

	/* (non-Javadoc)
	 * @see com.chinmobi.aio.AIOFuture#attachment()
	 */
	public final Object attachment() {
		return this.attachment;
	}


	protected boolean cancelled() {
		return cancelled(AIOFutureStatus.CASE_CANCELLED);
	}

	private final boolean cancelled(final int atCase) {
		AIOFutureCallback<T> callBack = null;
		T result = null;
		Object attachment = null;

		synchronized (this.lock) {
			if (this.status != AIOFutureStatus.CASE_UNKNOWN) {
				return false;
			}

			this.status = atCase;

			result = this.result;
			attachment = this.attachment;

			callBack = this.callback;
			this.callback = null;

			this.cancellable = null;

			if (this.waitCount > 0) {
				this.lock.notifyAll();
			}
		}

		if (callBack != null) {
			try {
				callBack.cancelled(this, attachment, result);
			} catch (Throwable ignore) {
				handleUncaughtException(ignore);
			}
		}
		return true;
	}

	protected boolean accomplished(final T result, final FutureDoAccomplishCallback<T> doCallback) {
		AIOFutureCallback<T> callBack = null;
		Object attachment = null;

		synchronized (this.lock) {
			if (this.status != AIOFutureStatus.CASE_UNKNOWN) {
				return false;
			}

			this.status = AIOFutureStatus.CASE_ACCOMPLISHED;
			this.result = result;

			attachment = this.attachment;

			callBack = this.callback;
			this.callback = null;

			this.cancellable = null;

			if (doCallback != null) {
				doCallback.futureDoAccomplish(result);
			}

			if (this.waitCount > 0) {
				this.lock.notifyAll();
			}
		}

		if (callBack != null) {
			try {
				callBack.accomplished(this, attachment, result);
			} catch (Throwable ignore) {
				handleUncaughtException(ignore);
			}
		}
		return true;
	}

	protected boolean timeout() {
		AIOFutureCallback<T> callBack = null;
		T result = null;
		Object attachment = null;

		synchronized (this.lock) {
			if (this.status != AIOFutureStatus.CASE_UNKNOWN) {
				return false;
			}

			this.status = AIOFutureStatus.CASE_TIMEOUT;

			result = this.result;
			attachment = this.attachment;

			callBack = this.callback;
			this.callback = null;

			this.cancellable = null;

			if (this.waitCount > 0) {
				this.lock.notifyAll();
			}
		}

		if (callBack != null) {
			try {
				callBack.timeout(this, attachment, result);
			} catch (Throwable ignore) {
				handleUncaughtException(ignore);
			}
		}
		return true;
	}

	protected boolean failed(final Throwable cause) {
		AIOFutureCallback<T> callBack = null;
		T result = null;
		Object attachment = null;

		synchronized (this.lock) {
			if (this.status != AIOFutureStatus.CASE_UNKNOWN) {
				return false;
			}

			this.status = AIOFutureStatus.CASE_FAILED;
			this.cause = cause;

			result = this.result;
			attachment = this.attachment;

			callBack = this.callback;
			this.callback = null;

			this.cancellable = null;

			if (this.waitCount > 0) {
				this.lock.notifyAll();
			}
		}

		if (callBack != null) {
			try {
				callBack.failed(this, attachment, result, cause);
			} catch (Throwable ignore) {
				handleUncaughtException(ignore);
			}
		}
		return true;
	}

}
