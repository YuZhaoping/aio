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
import com.chinmobi.aio.act.AIOInputActResult;
import com.chinmobi.aio.act.AIOInputActStrategy;
import com.chinmobi.aio.act.AIOInputActor;
import com.chinmobi.aio.act.AIOInputLegacy;
import com.chinmobi.aio.act.AIOInputLegacyRuntimeException;
import com.chinmobi.aio.act.AIOWritableActEntry;
import com.chinmobi.aio.impl.nio.Session;
import com.chinmobi.aio.impl.nio.TransportChannel;

/**
 * @author <a href="mailto:yuzhaoping1970@gmail.com">Zhaoping Yu</a>
 *
 */
public final class InputActor extends BaseActor<AIOInputActResult> implements AIOInputActor {

	private LegacyStack legacyStack;


	public InputActor(final Session session) {
		super(session);
	}


	/* (non-Javadoc)
	 * @see com.chinmobi.aio.AIOInputActor#read(...)
	 */
	public final AIOFuture<AIOInputActResult> read(final int sessionId,
			final AIOWritableActEntry target, final AIOFutureCallback<AIOInputActResult> callback, final long timeout, final TimeUnit unit,
			final AIOInputActStrategy strategy, final Object attachment)
					throws AIOClosedSessionException, IllegalArgumentException {
		return addRequest(sessionId,
				target, callback, timeout, unit,
				strategy, attachment).future();
	}

	public final InputActRequest addRequest(final int sessionId,
			final AIOWritableActEntry target, final AIOFutureCallback<AIOInputActResult> callback, final long timeout, final TimeUnit unit,
			final AIOInputActStrategy strategy, final Object attachment)
					throws AIOClosedSessionException, IllegalArgumentException {

		final long msecs = unit.toMillis(timeout);

		final InputActRequest request = actRequestFactory().createInputActRequest();

		try {
			request.set(target, callback, msecs, attachment);
			request.setStrategy(strategy);
		} catch (IllegalArgumentException ex) {
			request.internalRelease();
			throw ex;
		}

		return runRequest(sessionId, request, msecs);
	}

	private final ActRequestFactory actRequestFactory() {
		return this.session.context().actRequestFactory();
	}

	private final InputActRequest runRequest(final int sessionId, final InputActRequest request, final long timeout)
			throws AIOClosedSessionException {

		int result = 0;
		int modCount = 0;

		synchronized (this.session.requestLock()) {
			if (this.session.id() == sessionId && this.session.isOpen()) {
				addRequest(request);

				if (timeout > 0 && isCurrentRequest(request)) {
					modCount = this.session.generateTimerModCount();
					request.setTimerExpectedModCount(modCount);
				}

				this.session.onAddInputActRequest();

				result = 1;
			}
		}

		if (result > 0) {
			//if (!hasLegacy()) {
			//}
			if (!this.session.setInputEvent(true) && modCount != 0) {
				request.scheduleTimer(modCount);
			}
			return request;
		} else {
			request.internalRelease();
			throw new AIOClosedSessionException("Session ID: " + sessionId);
		}
	}


	@Override
	public final int handleSessionReady(final boolean isReadyOps) {
		BaseActRequest<AIOInputActResult> request = currentRequest();

		final TransportChannel transportChannel = this.session.getTransportChannel();
		if (transportChannel == null) {
			this.session.getEventHandler().close();
			return -1;
		}

		while (request != null) {
			if (ENABLE_TRACE != 0) this.trace(true, 'X');

			if (handleLegacy(request)) {
				request = currentRequest();
				continue;
			} else if (!isReadyOps) {
				break;
			}

			try {
				final int status = request.inputReady(this.session, transportChannel);
				if (ENABLE_TRACE != 0) {
					this.trace(true, '=', status);
					this.traceValue(request.completedCount());
				}

				switch (status) {
				case BaseActRequest.STATUS_DO_NOTHING:
				case BaseActRequest.STATUS_TO_TERMINATE:
					nextRequest(request);
					request.accomplished();
					break;

				case BaseActRequest.STATUS_END_OF_INPUT:
					nextRequest(request);
					this.session.shutdownInput(true);
					request.accomplished();
					break;

				case BaseActRequest.STATUS_NO_MATCHED_ENTRY:
				case BaseActRequest.STATUS_NULL_CHANNEL:
					return this.session.isInputShutdown() ? 1 : 0;

				default:
					return 1;
				}
			} catch (AIOActEntryRuntimeException ex) {
				nextRequest(request);
				request.failed(ex);
			} catch (Exception ex) { // IOException, RuntimeException
				nextRequest(request);
				this.session.shutdownInput(true);
				request.failed(ex);
			}

			if (!this.session.isOpen()) {
				this.session.getEventHandler().close();
				return -1;
			}

			request = currentRequest();
		}

		return this.session.isInputShutdown() ? 1 : 0;
	}

	/* (non-Javadoc)
	 * @see com.chinmobi.aio.AIOInputActor#getCurrentResult()
	 */
	public final AIOInputActResult getCurrentResult() {
		final BaseActRequest<AIOInputActResult> request = currentRequest();
		if (request != null) {
			return request.inputResult();
		} else {
			return null;
		}
	}

	/* (non-Javadoc)
	 * @see com.chinmobi.aio.AIOInputActor#pushLegacy(...)
	 */
	public int pushLegacy(final int sessionId,
			final AIOInputLegacy legacy, final boolean endOfInput)
					throws AIOClosedSessionException, IllegalArgumentException {
		int index = -1;
		synchronized (this.session.requestLock()) {
			if (this.session.id() == sessionId && this.session.isOpen()) {

				final LegacyChannel legacyChannel = legacyChannelFactory().createLegacyChannel();

				try {
					legacyChannel.set(legacy, (endOfInput && !hasLegacy()));
				} catch (IllegalArgumentException ex) {
					legacyChannel.reset();
					legacyChannel.release();

					throw ex;
				}

				legacyChannel.setExceptionHandler(this.session);

				if (this.legacyStack == null) {
					this.legacyStack = new LegacyStack();
				}

				index = this.legacyStack.pushLegacyChannel(legacyChannel);

			} else {
				throw new AIOClosedSessionException("Session ID: " + sessionId);
			}
		}

		//this.session.clearInputEvent();

		if (index == 0) {
			this.session.activate();
		}

		return index;
	}

	/* (non-Javadoc)
	 * @see com.chinmobi.aio.AIOInputActor#topIndexOfLegacy(int sessionId)
	 */
	public final int topIndexOfLegacy(final int sessionId) throws AIOClosedSessionException {
		synchronized (this.session.requestLock()) {
			if (this.session.id() == sessionId && this.session.isOpen()) {
				if (this.legacyStack != null) {
					return this.legacyStack.topIndex();
				} else {
					return -1;
				}
			} else {
				throw new AIOClosedSessionException("Session ID: " + sessionId);
			}
		}
	}

	/* (non-Javadoc)
	 * @see com.chinmobi.aio.AIOInputActor#popLegacy(int sessionId)
	 */
	public final AIOInputLegacy popLegacy(final int sessionId) throws AIOClosedSessionException {
		synchronized (this.session.requestLock()) {
			if (this.session.id() == sessionId && this.session.isOpen()) {
				if (this.legacyStack != null) {
					final LegacyChannel legacyChannel = this.legacyStack.popLegacyChannel();
					if (legacyChannel != null) {
						final AIOInputLegacy legacy = legacyChannel.legacy();

						legacyChannel.reset();
						legacyChannel.release();

						//if (!hasLegacy()) {
						//	this.session.setInputEvent();
						//}

						return legacy;
					}
				}

				return null;
			} else {
				throw new AIOClosedSessionException("Session ID: " + sessionId);
			}
		}
	}

	@Override
	protected final void terminated() {
		releaseAllLegacy();

		super.terminated();
	}


	private final LegacyChannelFactory legacyChannelFactory() {
		return actRequestFactory().legacyChannelFactory();
	}

	private final boolean hasLegacy() {
		return (this.legacyStack != null && this.legacyStack.topIndex() >= 0);
	}

	private final boolean handleLegacy(BaseActRequest<AIOInputActResult> request) {
		LegacyChannel legacyChannel = determineLegacy();

		if (legacyChannel == null) {
			return false;
		}

		while (request != null && legacyChannel != null) {
			if (ENABLE_TRACE != 0) this.trace(true, 'x');

			try {
				final int status = request.inputReady(this.session, legacyChannel);
				if (ENABLE_TRACE != 0) {
					this.trace(true, '=', status);
					this.traceValue(request.completedCount());
				}

				switch (status) {
				case BaseActRequest.STATUS_TO_TERMINATE:
				case BaseActRequest.STATUS_END_OF_INPUT:
					request.setEndOfInput(legacyChannel.endOfInput());

				case BaseActRequest.STATUS_DO_NOTHING:
					nextRequest(request);
					request.accomplished();
					break;

				default:
					break;
				}
			} catch (AIOActEntryRuntimeException ex) {
				nextRequest(request);
				request.failed(ex);
			} catch (AIOInputLegacyRuntimeException ex) {
				nextRequest(request);
				request.failed(ex);
			} catch (Exception ex) { // IOException, RuntimeException
				throw new AIOInputLegacyRuntimeException(ex);
			}

			legacyChannel = determineLegacy();
			request = currentRequest();
		}

		return true;
	}

	private final LegacyChannel determineLegacy() {
		synchronized (this.session.requestLock()) {
			if (hasLegacy()) {
				LegacyChannel legacyChannel = this.legacyStack.topLegacyChannel();

				//boolean endOfInput = false;

				while (legacyChannel != null) {
					if (legacyChannel.remaining() > 0) {
						return legacyChannel;
					}

					this.legacyStack.popLegacyChannel();

					//endOfInput = legacyChannel.endOfInput();
					legacyChannel.release();

					legacyChannel = this.legacyStack.topLegacyChannel();
				}

				//if (!endOfInput) {
				//	this.session.setInputEvent();
				//}
			}
		}
		return null;
	}

	private final void releaseAllLegacy() {
		synchronized (this.session.requestLock()) {
			LegacyChannel legacyChannel = null;

			if (hasLegacy()) {
				legacyChannel = this.legacyStack.removeAll();
			}

			LegacyChannel next;
			while (legacyChannel != null) {
				next = legacyChannel.next;

				legacyChannel.release();

				legacyChannel = next;
			}
		}
	}


	private static final class LegacyStack {

		private LegacyChannel topLegacyChannel;
		private int topIndex;


		LegacyStack() {
			this.topIndex = -1;
		}


		final int pushLegacyChannel(final LegacyChannel legacyChannel) {
			legacyChannel.next = this.topLegacyChannel;
			this.topLegacyChannel = legacyChannel;

			++this.topIndex;

			return this.topIndex;
		}

		final LegacyChannel popLegacyChannel() {
			final LegacyChannel legacyChannel = this.topLegacyChannel;

			if (legacyChannel != null) {
				this.topLegacyChannel = legacyChannel.next;
				legacyChannel.next = null;

				--this.topIndex;
			}

			return legacyChannel;
		}

		final int topIndex() {
			return this.topIndex;
		}

		final LegacyChannel topLegacyChannel() {
			return this.topLegacyChannel;
		}

		final LegacyChannel removeAll() {
			final LegacyChannel legacyChannel = this.topLegacyChannel;

			this.topLegacyChannel = null;
			this.topIndex = -1;

			return legacyChannel;
		}

	}

}
