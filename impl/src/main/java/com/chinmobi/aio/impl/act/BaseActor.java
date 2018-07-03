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

import java.util.Iterator;
import java.util.concurrent.atomic.AtomicReference;

import com.chinmobi.aio.act.AIOActResult;
import com.chinmobi.aio.impl.Constants;
import com.chinmobi.aio.impl.nio.Session;
import com.chinmobi.aio.impl.util.IterableLinkedQueue;

/**
 * @author <a href="mailto:yuzhaoping1970@gmail.com">Zhaoping Yu</a>
 *
 */
public class BaseActor<T extends AIOActResult> {

	static final int ENABLE_TRACE = 1;
	private final StringBuilder traceBuffer;


	protected final Session session;

	private final AtomicReference<BaseActRequest<T>> currentRequest;

	private final IterableLinkedQueue<BaseActRequest<T>> requests;
	private final Object requestsLock;


	protected BaseActor(final Session session) {
		super();
		this.session = session;

		this.currentRequest = new AtomicReference<BaseActRequest<T>>();

		this.requests = new IterableLinkedQueue<BaseActRequest<T>>();
		this.requestsLock = new Object();

		this.traceBuffer = (ENABLE_TRACE != 0) ? new StringBuilder() : null;
	}


	private final Object requestsLock() {
		return this.requestsLock;
	}

	public void reset() {
		if (ENABLE_TRACE != 0) { this.traceBuffer.delete(0, this.traceBuffer.length()); }
	}

	public final boolean contains(final BaseActRequest<T> request) {
		if (request == this.currentRequest.get()) {
			return true;
		}

		return (request.belongsQueue() == this.requests);
	}

	public final boolean hasRequest() {
		return (currentRequest(0) != null);
	}

	final boolean isCurrentRequest(final BaseActRequest<T> request) {
		return (currentRequest(0) == request);
	}

	private final BaseActRequest<T> currentRequest(final int modCount) {
		BaseActRequest<T> request = this.currentRequest.get();

		if (request != null) {
			if (!request.future().isDone()) {
				if (modCount != 0) request.setTimerExpectedModCount(modCount);
				return request;
			}
		} else {
			return null;
		}

		final Iterator<BaseActRequest<T>> iter = this.requests.iterator();
		while (iter.hasNext()) {
			request = iter.next();

			if (request != null) {
				if (!request.future().isDone()) {
					if (modCount != 0) request.setTimerExpectedModCount(modCount);
					return request;
				}
			}
		}

		return null;
	}

	public final BaseActRequest<T> currentRequest() {
		BaseActRequest<T> request = null;

		for (;;) {
			if (request == null) {
				request = this.currentRequest.get();
			} else {
				request = scheduleNext(request);
			}

			if (request != null) {
				try {
					if (request.isDone()) {
						request.internalRelease();
						continue;
					}
				} catch (Throwable ex) { // for future.initiate()
					request.failed(ex);
					continue;
				}
			}

			break;
		}

		return request;
	}

	protected final BaseActRequest<T> nextRequest(final BaseActRequest<T> curr) {
		return scheduleNext(curr);
	}

	protected final void addRequest(final BaseActRequest<T> request) {
		if (ENABLE_TRACE != 0) traceAddRequest();

		this.requests.add(request);
		request.setActor(this);

		scheduleBegin();
	}

	private final void scheduleBegin() {
		final BaseActRequest<T> curr = this.currentRequest.get();
		if (curr == null) {
			scheduleNext(curr);
		}
	}

	private final BaseActRequest<T> scheduleNext(final BaseActRequest<T> curr) {
		final BaseActRequest<T> next = this.requests.peek();

		if (this.currentRequest.compareAndSet(curr, next)) {
			if (next != null) {
				next.dequeue();

				if (ENABLE_TRACE != 0) synchronized (this.requestsLock()) { this.traceBuffer.append('A'); }
			}
		}

		return this.currentRequest.get();
	}

	private final BaseActRequest<T> scheduleEnd() {
		return this.currentRequest.getAndSet(null);
	}


	final boolean removeRequest(final BaseActRequest<T> request) {
		boolean removed = false;

		final BaseActRequest<T> req = this.currentRequest.get();
		if (req != null) {
			if (req == request) {
				scheduleNext(req);
				removed = true;
			} else {
				removed = (request.dequeue() > 0);
			}
		}

		if (removed) {
			scheduleCurrentRequestTimer();
		}
		return removed;
	}

	final void scheduleCurrentRequestTimer() {
		int modCount = 0;
		BaseActRequest<T> req = null;

		synchronized (this.session.requestLock()) {
			if (this.session.isOpen()) {
				req = currentRequest(0);

				if (req != null) {
					modCount = this.session.generateTimerModCount();
					req.setTimerExpectedModCount(modCount);
				}
			}
		}

		if (modCount != 0 && req != null) {
			req.scheduleTimer(modCount);
		}
	}


	public final void setTimerExpectedModCount(final int modCount) {
		currentRequest(modCount);
	}

	public final void scheduleTimer(final int modCount) {
		final BaseActRequest<T> req = currentRequest(0);
		if (req != null) {
			req.scheduleTimer(modCount);
		}
	}

	public final void cancelTimer(final int modCount) {
		final BaseActRequest<T> req = currentRequest(0);
		if (req != null) {
			req.cancelTimer(modCount);
		}
	}

	// For test
	public final void handleSessionSelected() {
		handleSessionSelected(0);
	}

	public final void handleSessionSelected(final int modCount) {
		if (ENABLE_TRACE != 0) synchronized (this.requestsLock()) {
			if (this.currentRequest.get() != null) this.traceBuffer.append('#').append('S');
		}

		cancelTimer(modCount);
	}

	public int handleSessionReady(final boolean isReadyOps) {
		return 0;
	}

	public final void handleSessionCancelled() {
		if (ENABLE_TRACE != 0) synchronized (this.requestsLock()) {
			if (this.currentRequest.get() != null) this.traceBuffer.append('#').append('C');
		}
	}

	public final void handleSessionClosed(final Throwable cause) {
		if (ENABLE_TRACE != 0) synchronized (this.requestsLock()) {
			if (this.currentRequest.get() != null) this.traceBuffer.append('#').append('E');
		}

		terminate(cause);
	}


	protected void terminated() {
	}

	private final void terminate(final Throwable cause) {
		final BaseActRequest<T> request = scheduleEnd();

		terminated();

		if (request != null) {
			if (cause != null) {
				request.failed(cause);
			} else {
				request.close();
			}
		}

		BaseActRequest<T> req;
		while ((req = this.requests.poll()) != null) {
			req.close();
		}
	}


	private final void traceAddRequest() {
		if (ENABLE_TRACE != 0)
			synchronized (this.requestsLock()) {

			if (this.currentRequest.get() == null) {
				final int length = this.traceBuffer.length();
				if (length > 1024) {
					this.traceBuffer.delete(0, length);
				} else
				if (length > 0) {
					this.traceBuffer.append(' ');
				}
			}

			this.traceBuffer.append('{');
		}
	}

	final void trace(final char traceCode) {
		trace(false, traceCode);
	}

	final void trace(final boolean appendSpace, final char traceCode) {
		if (ENABLE_TRACE != 0) synchronized (this.requestsLock()) {
			if (appendSpace) {
				this.traceBuffer.append(' ');
			}
			this.traceBuffer.append(traceCode);
		}
	}

	final void trace(final boolean appendSpace, final char traceCode, final int value) {
		if (ENABLE_TRACE != 0) synchronized (this.requestsLock()) {
			if (appendSpace) {
				this.traceBuffer.append(' ');
			}
			this.traceBuffer.append(traceCode).append(value);
		}
	}

	final void traceValue(final long value) {
		if (ENABLE_TRACE != 0) synchronized (this.requestsLock()) {
			this.traceBuffer.append('[').append(value).append(']');
		}
	}

	public final void printTraceString(final char io,
			final StringBuilder builder) {
		if (ENABLE_TRACE != 0) synchronized (this.requestsLock()) {
			if (this.traceBuffer.length() <= 0) return;

			builder.append(Constants.CRLF);
			builder.append(io).append('\t').append(this.traceBuffer);
			builder.append(Constants.CRLF);
		}
	}

}
