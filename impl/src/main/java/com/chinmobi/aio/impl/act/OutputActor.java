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

import java.util.concurrent.TimeUnit;

import com.chinmobi.aio.AIOClosedSessionException;
import com.chinmobi.aio.AIOFuture;
import com.chinmobi.aio.AIOFutureCallback;
import com.chinmobi.aio.act.AIOActEntryRuntimeException;
import com.chinmobi.aio.act.AIOOutputActResult;
import com.chinmobi.aio.act.AIOOutputActor;
import com.chinmobi.aio.act.AIOReadableActEntry;
import com.chinmobi.aio.impl.nio.Session;
import com.chinmobi.aio.impl.nio.TransportChannel;

/**
 * @author <a href="mailto:yuzhaoping1970@gmail.com">Zhaoping Yu</a>
 *
 */
public final class OutputActor extends BaseActor<AIOOutputActResult> implements AIOOutputActor {


	public OutputActor(final Session session) {
		super(session);
	}


	/* (non-Javadoc)
	 * @see com.chinmobi.aio.AIOOutputActor#write(...)
	 */
	public final AIOFuture<AIOOutputActResult> write(final int sessionId,
			final AIOReadableActEntry source, final AIOFutureCallback<AIOOutputActResult> callback, final long timeout, final TimeUnit unit,
			final Object attachment) throws AIOClosedSessionException, IllegalArgumentException {
		return addRequest(sessionId,
				source, callback, timeout, unit,
				attachment).future();
	}

	public final OutputActRequest addRequest(final int sessionId,
			final AIOReadableActEntry source, final AIOFutureCallback<AIOOutputActResult> callback, final long timeout, final TimeUnit unit,
			final Object attachment) throws AIOClosedSessionException, IllegalArgumentException {

		final long msecs = unit.toMillis(timeout);

		final OutputActRequest request = actRequestFactory().createOutputActRequest();

		try {
			request.set(source, callback, msecs, attachment);
		} catch (IllegalArgumentException ex) {
			request.internalRelease();
			throw ex;
		}

		return runRequest(sessionId, request, msecs);
	}

	private final ActRequestFactory actRequestFactory() {
		return this.session.context().actRequestFactory();
	}

	private final OutputActRequest runRequest(final int sessionId, final OutputActRequest request, final long timeout)
			throws AIOClosedSessionException {

		int result = 0;
		int modCount = 0;

		synchronized (this.session.requestLock()) {
			if (this.session.id() == sessionId && this.session.isOpen()) {
				if (!this.session.isOutputShutdown()) {
					addRequest(request);

					if (timeout > 0 && isCurrentRequest(request)) {
						modCount = this.session.generateTimerModCount();
						request.setTimerExpectedModCount(modCount);
					}

					this.session.onAddOutputActRequest();

					result = 1;
				} else {
					result = -1;
				}
			}
		}

		if (result > 0) {
			if (!this.session.setOutputEvent(true) && modCount != 0) {
				request.scheduleTimer(modCount);
			}
			return request;
		}

		request.internalRelease();

		if (result < 0) {
			throw new UnsupportedOperationException("Output");
		} else {
			throw new AIOClosedSessionException("Session ID: " + sessionId);
		}
	}


	@Override
	public final int handleSessionReady(final boolean isReadyOps) {
		BaseActRequest<AIOOutputActResult> request = currentRequest();

		final TransportChannel transportChannel = this.session.getTransportChannel();
		if (transportChannel == null) {
			this.session.getEventHandler().close();
			return -1;
		}

		while (request != null) {
			if (ENABLE_TRACE != 0) this.trace(true, 'X');

			try {
				final int status = request.outputReady(this.session, transportChannel);
				if (ENABLE_TRACE != 0) {
					this.trace(true, '=', status);
					this.traceValue(request.completedCount());
				}

				switch (status) {
				case BaseActRequest.STATUS_DO_NOTHING:
				case BaseActRequest.STATUS_TO_TERMINATE:
					if (nextRequest(request) == null) {
						this.session.clearOutputEvent();
					}
					request.accomplished();
					break;

				case BaseActRequest.STATUS_NO_MATCHED_ENTRY:
				case BaseActRequest.STATUS_NULL_CHANNEL:
					this.session.setOutputEvent();
					return this.session.isOutputShutdown() ? 1 : 0;

				default:
					this.session.setOutputEvent();
					return 1;
				}
			} catch (AIOActEntryRuntimeException ex) {
				nextRequest(request);
				request.failed(ex);
			} catch (Exception ex) {	// IOException, RuntimeException
				nextRequest(request);
				this.session.shutdownOutput(true);
				request.failed(ex);
			}

			if (!this.session.isOpen()) {
				this.session.getEventHandler().close();
				return -1;
			} else if (this.session.isOutputShutdown()) {
				break;
			}

			request = currentRequest();
		}

		if (this.session.isShuttingDown()) {
			if (!this.session.shutdownOutput(false)) {
				return 1;
			} else if (this.session.isClosing()) {
				this.session.getEventHandler().close();
				return -1;
			}
		}

		return this.session.isOutputShutdown() ? 1 : 0;
	}

	/* (non-Javadoc)
	 * @see com.chinmobi.aio.AIOOutputActor#getCurrentResult()
	 */
	public final AIOOutputActResult getCurrentResult() {
		final BaseActRequest<AIOOutputActResult> request = currentRequest();
		if (request != null) {
			return request.outputResult();
		} else {
			return null;
		}
	}

}
