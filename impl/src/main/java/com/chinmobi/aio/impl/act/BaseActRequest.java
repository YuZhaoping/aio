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
package com.chinmobi.aio.impl.act;

import java.io.IOException;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;

import com.chinmobi.aio.AIOFutureCancellable;
import com.chinmobi.aio.act.AIOActEntry;
import com.chinmobi.aio.act.AIOActResult;
import com.chinmobi.aio.act.AIOInputActResult;
import com.chinmobi.aio.act.AIOOutputActResult;
import com.chinmobi.aio.impl.nio.FutureDoAccomplishCallback;
import com.chinmobi.aio.impl.nio.FutureReleaseCallback;
import com.chinmobi.aio.impl.nio.Session;
import com.chinmobi.aio.impl.nio.SessionContext;
import com.chinmobi.aio.impl.nio.TransportChannel;
import com.chinmobi.aio.impl.util.ConcurrentLinkedQueue;

/**
 * @author <a href="mailto:yuzhaoping1970@gmail.com">Zhaoping Yu</a>
 *
 */
public abstract class BaseActRequest<T extends AIOActResult>
	extends ConcurrentLinkedQueue.NodeCacheableEntry {

	static final int STATUS_DO_NOTHING = -1;
	static final int STATUS_TO_TERMINATE = 0;
	static final int STATUS_TO_CONTINUE = 1;
	static final int STATUS_END_OF_INPUT = 2;
	static final int STATUS_NO_MATCHED_ENTRY = 4;
	static final int STATUS_NULL_CHANNEL = 8;

	protected final ActFuture<T> future;

	private final FutureHelper<T> futureHelper;

	private final ActTimerEntry<T> timerEntry;

	protected BaseActor<T> actor;

	protected AIOActEntry entry;

	protected long position;
	protected long count;

	protected long completedCount;

	private int state;


	protected BaseActRequest(final SessionContext context) {
		super();

		this.future = new ActFuture<T>(context);
		this.futureHelper = new FutureHelper<T>(this);

		this.timerEntry = new ActTimerEntry<T>(this);
		context.registerTimerEntry(this.timerEntry);
	}


	public final ActFuture<T> future() {
		return this.future;
	}

	protected final void set(final AIOActEntry entry, final long position, final long count,
			final long timeout, final Object attachment) {
		this.entry = entry;

		this.position = position;
		this.count = count;

		this.timerEntry.setExpectedModCount(0);
		this.timerEntry.setTimeout(timeout);

		this.completedCount = 0;

		this.state = 0;
	}

	final void setActor(final BaseActor<T> actor) {
		this.actor = actor;
		this.future.setExceptionHandler(actor.session);
	}

	final void internalRelease() {
		this.future.internalRelease();
	}

	final boolean isDone() {
		if (this.state == 0) {
			if (BaseActor.ENABLE_TRACE != 0) {
				final BaseActor<T> actor = this.actor;
				if (actor != null) {
					actor.trace(true, 'I');
				}
			}
			this.state = 1;
			return !this.future.initiate();
		} else {
			return this.future.isDone();
		}
	}

	public final void close() {
		this.timerEntry.cancel();

		final BaseActor<T> actor = this.actor;
		if (actor != null) {
			if (BaseActor.ENABLE_TRACE != 0) actor.trace('E');
			actor.removeRequest(this);
		}

		if (!this.future.cancelled()) {
			internalRelease();
		}
	}


	protected final FutureReleaseCallback futureReleaseCallback() {
		return this.futureHelper;
	}

	protected final FutureDoAccomplishCallback<T> futureDoAccomplishCallback() {
		return this.futureHelper;
	}

	protected final AIOFutureCancellable futureCancellable() {
		return this.futureHelper;
	}

	private final boolean doCancel(final boolean mayInterruptIfRunning) {
		this.timerEntry.cancel();

		final BaseActor<T> actor = this.actor;
		if (actor != null) {
			if (BaseActor.ENABLE_TRACE != 0) actor.trace(true, 'C');
			actor.scheduleCurrentRequestTimer();
		}

		return true;
	}


	AIOInputActResult inputResult() {
		return null;
	}

	AIOOutputActResult outputResult() {
		return null;
	}

	/* (non-Javadoc)
	 * @see com.chinmobi.aio.AIOActResult#position()
	 */
	public final long position() {
		return this.position;
	}

	/* (non-Javadoc)
	 * @see com.chinmobi.aio.AIOActResult#completedCount()
	 */
	public final long completedCount() {
		return this.completedCount;
	}

	/* (non-Javadoc)
	 * @see com.chinmobi.aio.AIOActResult#entry()
	 */
	public AIOActEntry entry() {
		return this.entry;
	}

	/* (non-Javadoc)
	 * @see com.chinmobi.aio.AIOInputActResult#endOfInput()
	 */
	public boolean endOfInput() {
		return false;
	}

	void setEndOfInput(final boolean endOfInput) {
	}


	abstract void accomplished();


	final void timeout() {
		final BaseActor<T> actor = this.actor;
		if (actor != null) {
			if (BaseActor.ENABLE_TRACE != 0) actor.trace(true, 'T');
			actor.removeRequest(this);
		}

		if (!this.future.timeout()) {
			internalRelease();
		}
	}

	final void failed(final Throwable cause) {
		if (BaseActor.ENABLE_TRACE != 0) trace('F');

		if (!this.future.failed(cause)) {
			internalRelease();
		}
	}

	protected void released() {
		if (BaseActor.ENABLE_TRACE != 0) trace('}');

		this.future.setExceptionHandler(null);

		this.entry = null;
		this.actor = null;
	}


	int inputReady(final Session session, final TransportChannel transportChannel) throws IOException {
		return 0;
	}

	int inputReady(final Session session, final ReadableByteChannel inputChannel) throws IOException {
		return 0;
	}

	int outputReady(final Session session, final TransportChannel transportChannel) throws IOException {
		return 0;
	}

	int outputReady(final Session session, final WritableByteChannel outputChannel) throws IOException {
		return 0;
	}


	public final boolean isTimerScheduled() {
		return this.timerEntry.isScheduled();
	}

	final void scheduleTimer(final int modCount) {
		this.timerEntry.schedule(modCount);
	}

	final void cancelTimer(final int modCount) {
		this.timerEntry.cancel(modCount);
	}

	final void setTimerExpectedModCount(final int modCount) {
		this.timerEntry.setExpectedModCount(modCount);
	}

	final void onTimerEntryScheduled() {
		if (BaseActor.ENABLE_TRACE != 0) trace('^');
	}

	final void onTimerEntryCancelled() {
		if (BaseActor.ENABLE_TRACE != 0) trace('$');
	}

	final void onTimerEntryDiscarded() {
		if (BaseActor.ENABLE_TRACE != 0) trace('%');
	}


	private final void onAccomplished() {
		if (BaseActor.ENABLE_TRACE != 0) trace('.');
	}


	private final void trace(final char traceCode) {
		trace(false, traceCode);
	}

	private final void trace(final boolean appendSpace, final char traceCode) {
		if (BaseActor.ENABLE_TRACE != 0) {
			final BaseActor<T> actor = this.actor;
			if (actor != null) {
				actor.trace(appendSpace, traceCode);
			}
		}
	}


	private static final class FutureHelper<T extends AIOActResult>
		implements AIOFutureCancellable, FutureDoAccomplishCallback<T>,
			FutureReleaseCallback {

		private final BaseActRequest<T> request;


		FutureHelper(final BaseActRequest<T> request) {
			this.request = request;
		}


		/* (non-Javadoc)
		 * @see com.chinmobi.aio.AIOFutureCancellable#cancel(boolean mayInterruptIfRunning)
		 */
		public final boolean cancel(final boolean mayInterruptIfRunning) {
			return this.request.doCancel(mayInterruptIfRunning);
		}

		/*
		 * (non-Javadoc)
		 * @see com.chinmobi.aio.impl.nio.FutureDoAccomplishCallback#futureDoAccomplish(java.lang.Object)
		 */
		public final void futureDoAccomplish(final T result) {
			if (BaseActor.ENABLE_TRACE != 0) this.request.onAccomplished();
			try {
				this.request.entry.completed(this.request.position, this.request.completedCount);
			} catch (Throwable ignore) {
				this.request.future.exceptionHandler().handleUncaughtException(ignore);
			}
		}

		/* (non-Javadoc)
		 * @see com.chinmobi.aio.impl.nio.FutureReleaseCallback#futureReleased()
		 */
		public final void futureReleased() {
			this.request.released();
		}

	}

}
