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
package com.chinmobi.aio.impl.service;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import com.chinmobi.aio.AIOClosedSessionException;
import com.chinmobi.aio.AIOFuture;
import com.chinmobi.aio.AIOFutureCallback;
import com.chinmobi.aio.AIOReactor;
import com.chinmobi.aio.AIOServiceHandler;
import com.chinmobi.aio.AIOSession;
import com.chinmobi.aio.act.AIOInputActResult;
import com.chinmobi.aio.act.AIOInputActStrategy;
import com.chinmobi.aio.act.AIOInputLegacy;
import com.chinmobi.aio.act.AIOOutputActResult;
import com.chinmobi.aio.act.AIOReadableActEntry;
import com.chinmobi.aio.act.AIOWritableActEntry;
import com.chinmobi.aio.impl.util.WrapperRuntimeException;
import com.chinmobi.aio.service.AIOService;
import com.chinmobi.aio.service.AIOServiceCallback;

/**
 * @author <a href="mailto:yuzhaoping1970@gmail.com">Zhaoping Yu</a>
 *
 */
public class Service implements AIOService {


	protected static final class Handler implements AIOServiceHandler {

		private final Service service;

		private final AIOReactor reactor;

		private AIOSession session;
		private int sessionId;

		private volatile AIOServiceCallback serviceCallback;


		private Handler(final Service service, final AIOReactor reactor) {
			this.service = service;
			this.reactor = reactor;
		}


		public final AIOReactor reactor() {
			return this.reactor;
		}

		public final AIOServiceCallback getServiceCallback() {
			return this.serviceCallback;
		}

		public final void setServiceCallback(final AIOServiceCallback callback) {
			this.serviceCallback = callback;
		}

		public final synchronized AIOSession session() {
			return this.session;
		}

		public final synchronized void setTimeout(final long timeout, final TimeUnit unit) {
			final AIOSession session = this.session;
			if (session != null) {
				session.setTimeout(timeout, unit);
			}
		}

		public final boolean isOpen() {
			synchronized (this) {
				final AIOSession session = this.session;
				if (session != null) {
					return session.isOpen();
				}
			}
			return false;
		}

		public final void close() {
			synchronized (this) {
				final AIOSession session = this.session;
				if (session != null) {
					session.close();
				}
			}
		}

		private final void release() {
			this.serviceCallback = null;
		}

		public final void attachToSession(final AIOSession session) {
			synchronized (this) {
				this.session = session;
				this.sessionId = session.id();
			}
		}

		private final int detachSession(final AIOSession session) {
			synchronized (this) {
				if (this.session == session && this.sessionId == session.id()) {
					this.session = null;
					return  1;
				} else if (this.session == null) {
					return -1;
				}
			}
			return 0;
		}

		/* (non-Javadoc)
		 * @see com.chinmobi.aio.AIOServiceHandler#handleAIOSessionOpened(...)
		 */
		public final void handleAIOSessionOpened(final AIOSession session) throws AIOClosedSessionException {
			final AIOServiceCallback callback = this.serviceCallback;
			if (callback != null) {
				callback.aioServiceOpened(this.service);
			} else if (!this.service.allowNullServiceCallback()) {
				throw new RuntimeException("Null service callback.");
			}
		}

		/* (non-Javadoc)
		 * @see com.chinmobi.aio.AIOServiceHandler#handleAIOSessionInputReady(...)
		 */
		public final boolean handleAIOSessionInputReady(final AIOSession session)
				throws IOException, AIOClosedSessionException {
			final AIOServiceCallback callback = this.serviceCallback;
			if (callback != null) {
				return callback.aioServiceInputReady(this.service);
			}
			return false;
		}

		/* (non-Javadoc)
		 * @see com.chinmobi.aio.AIOServiceHandler#handleAIOSessionOutputReady(...)
		 */
		public final boolean handleAIOSessionOutputReady(final AIOSession session)
				throws IOException, AIOClosedSessionException {
			final AIOServiceCallback callback = this.serviceCallback;
			if (callback != null) {
				return callback.aioServiceOutputReady(this.service);
			}
			return false;
		}

		/* (non-Javadoc)
		 * @see com.chinmobi.aio.AIOServiceHandler#handleAIOSessionTimeout(...)
		 */
		public final void handleAIOSessionTimeout(final AIOSession session) throws AIOClosedSessionException {
			final AIOServiceCallback callback = this.serviceCallback;
			if (callback != null) {
				callback.aioServiceTimeout(this.service);
			} else {
				close();
			}
		}

		/* (non-Javadoc)
		 * @see com.chinmobi.aio.AIOServiceHandler#handleAIOSessionClosed(...)
		 */
		public final void handleAIOSessionClosed(final AIOSession session, final Throwable cause) {
			if (detachSession(session) > 0) {
				AIOServiceCallback callback = this.serviceCallback;

				RuntimeException exception = null;
				try {
					if (this.service.handleSessionClosed(cause)) {
						callback = this.serviceCallback;
					}
				} catch (RuntimeException ex) {
					exception = ex;
				}

				if (callback != null) {
					callback.aioServiceClosed(this.service, session, cause);
				} else if (cause != null) {
					if (cause.getCause() != null) {
						throw new WrapperRuntimeException(cause.getCause());
					} else {
						throw new WrapperRuntimeException(cause);
					}
				}

				if (exception != null) {
					throw exception;
				}
			} else if (cause != null) {
				if (cause.getCause() != null) {
					throw new WrapperRuntimeException(cause.getCause());
				} else {
					throw new WrapperRuntimeException(cause);
				}
			}
		}

		@Override
		public final String toString() {
			final StringBuilder builder = new StringBuilder();

			builder.append("[Service: ");
			builder.append(this.service.internalName());
			final AIOServiceCallback callback = this.serviceCallback;
			if (callback != null) {
				builder.append(", Callback: [");
				builder.append(callback.toString());
				builder.append(']');
			}
			builder.append(']');

			return builder.toString();
		}

	}


	protected final Handler handler;

	private final InputActCallback inputActCallback;
	private final OutputActCallback outputActCallback;


	protected Service(final AIOReactor reactor) {
		this.handler = new Handler(this, reactor);

		this.inputActCallback = new InputActCallback(this);
		this.outputActCallback = new OutputActCallback(this);
	}


	public final AIOServiceHandler serviceHandler() {
		return this.handler;
	}

	/* (non-Javadoc)
	 * @see com.chinmobi.aio.act.AIOService#isOpen()
	 */
	public final boolean isOpen() {
		return this.handler.isOpen();
	}

	/* (non-Javadoc)
	 * @see com.chinmobi.aio.act.AIOService#close()
	 */
	public void close() {
		this.handler.close();
	}

	/* (non-Javadoc)
	 * @see com.chinmobi.aio.act.AIOService#session()
	 */
	public final AIOSession session() {
		return this.handler.session();
	}

	/* (non-Javadoc)
	 * @see com.chinmobi.aio.act.AIOService#setSessionTimeout(...)
	 */
	public final void setSessionTimeout(final long timeout, final TimeUnit unit) {
		this.handler.setTimeout(timeout, unit);
	}

	/* (non-Javadoc)
	 * @see com.chinmobi.aio.act.AIOService#getServiceCallback()
	 */
	public final AIOServiceCallback getServiceCallback() {
		return this.handler.getServiceCallback();
	}

	public final void setServiceCallback(final AIOServiceCallback callback) {
		this.handler.setServiceCallback(callback);
	}

	/* (non-Javadoc)
	 * @see com.chinmobi.aio.act.AIOService#read(...)
	 */
	public AIOFuture<AIOInputActResult> read(final AIOWritableActEntry target,
			final ReadCallback callback, final AIOInputActStrategy strategy,
			final long timeout, final TimeUnit unit)
					throws AIOClosedSessionException {
		return doRead(target, callback, strategy, timeout, unit);
	}

	final AIOFuture<AIOInputActResult> doRead(final AIOWritableActEntry target,
			final ReadCallback callback, final AIOInputActStrategy strategy,
			final long timeout, final TimeUnit unit)
					throws AIOClosedSessionException {
		final AIOSession session;
		final int sessionId;

		synchronized (this.handler) {
			session = this.handler.session;
			sessionId = this.handler.sessionId;
		}

		if (session != null) {
			if (callback != null) {
				return session.inputActor().read(sessionId,
						target,
						this.inputActCallback,
						timeout, unit,
						strategy,
						callback);
			} else {
				return session.inputActor().read(sessionId,
						target,
						null,
						timeout, unit,
						strategy,
						null);
			}
		} else {
			throw new AIOClosedSessionException("Null session.");
		}
	}

	/* (non-Javadoc)
	 * @see com.chinmobi.aio.act.AIOService#pushInputLegacy(...)
	 */
	public final int pushInputLegacy(final AIOInputLegacy legacy, final boolean endOfInput)
			throws AIOClosedSessionException {
		final AIOSession session;
		final int sessionId;

		synchronized (this.handler) {
			session = this.handler.session;
			sessionId = this.handler.sessionId;
		}

		if (session != null) {
			return session.inputActor().pushLegacy(sessionId, legacy, endOfInput);
		} else {
			throw new AIOClosedSessionException("Null session.");
		}
	}

	/* (non-Javadoc)
	 * @see com.chinmobi.aio.act.AIOService#topIndexOfInputLegacy()
	 */
	public final int topIndexOfInputLegacy() throws AIOClosedSessionException {
		final AIOSession session;
		final int sessionId;

		synchronized (this.handler) {
			session = this.handler.session;
			sessionId = this.handler.sessionId;
		}

		if (session != null) {
			return session.inputActor().topIndexOfLegacy(sessionId);
		} else {
			throw new AIOClosedSessionException("Null session.");
		}
	}

	/* (non-Javadoc)
	 * @see com.chinmobi.aio.act.AIOService#popInputLegacy()
	 */
	public final AIOInputLegacy popInputLegacy() throws AIOClosedSessionException {
		final AIOSession session;
		final int sessionId;

		synchronized (this.handler) {
			session = this.handler.session;
			sessionId = this.handler.sessionId;
		}

		if (session != null) {
			return session.inputActor().popLegacy(sessionId);
		} else {
			throw new AIOClosedSessionException("Null session.");
		}
	}

	/* (non-Javadoc)
	 * @see com.chinmobi.aio.act.AIOService#write(...)
	 */
	public AIOFuture<AIOOutputActResult> write(final AIOReadableActEntry source,
			final WriteCallback callback,
			final long timeout, final TimeUnit unit) throws AIOClosedSessionException {
		return doWrite(source, callback, timeout, unit);
	}

	final AIOFuture<AIOOutputActResult> doWrite(final AIOReadableActEntry source,
			final WriteCallback callback,
			final long timeout, final TimeUnit unit) throws AIOClosedSessionException {
		final AIOSession session;
		final int sessionId;

		synchronized (this.handler) {
			session = this.handler.session;
			sessionId = this.handler.sessionId;
		}

		if (session != null) {
			if (callback != null) {
				return session.outputActor().write(sessionId,
						source,
						this.outputActCallback,
						timeout, unit,
						callback);
			} else {
				return session.outputActor().write(sessionId,
						source,
						null,
						timeout, unit,
						null);
			}
		} else {
			throw new AIOClosedSessionException("Null session.");
		}
	}

	/* (non-Javadoc)
	 * @see com.chinmobi.aio.act.AIOService#reactor()
	 */
	public final AIOReactor reactor() {
		return this.handler.reactor();
	}

	@Override
	public final String toString() {
		final AIOSession session = this.handler.session();
		if (session != null) {
			return session.toString();
		}
		return this.handler.toString();
	}


	protected String internalName() {
		return null;
	}

	protected boolean allowNullServiceCallback() {
		return true;
	}

	protected boolean handleSessionClosed(final Throwable cause) {
		doRelease();
		return false;
	}

	protected void doRelease() {
		this.handler.release();
	}

	protected final Handler handler() {
		return this.handler;
	}


	static final class InputActCallback implements AIOFutureCallback<AIOInputActResult> {

		private final Service service;

		private InputActCallback(final Service service) {
			this.service = service;
		}

		public final void initiate(final AIOFuture<AIOInputActResult> future,
				final Object attachment, final AIOInputActResult result) {
			final ReadCallback callback = (ReadCallback)attachment;
			if (callback != null) {
				final AIOWritableActEntry entry = result.entry();
				callback.aioReadInitiate(this.service, entry);
			}
		}

		public final void accomplished(final AIOFuture<AIOInputActResult> future,
				final Object attachment, final AIOInputActResult result) {

			final ReadCallback callback = (ReadCallback)attachment;
			if (callback != null) {
				final AIOWritableActEntry entry = result.entry();

				final long position = result.position();
				final long completedCount = result.completedCount();
				final boolean endOfInput = result.endOfInput();

				callback.aioReadAccomplished(this.service, entry, position, completedCount, endOfInput);

				future.release();
			}
		}

		public final void timeout(final AIOFuture<AIOInputActResult> future,
				final Object attachment, final AIOInputActResult result) {

			final ReadCallback callback = (ReadCallback)attachment;
			if (callback != null) {
				final AIOWritableActEntry entry = result.entry();

				final long position = result.position();
				final long completedCount = result.completedCount();

				callback.aioReadTimeout(this.service, entry, position, completedCount);

				future.release();
			}
		}

		public final void failed(final AIOFuture<AIOInputActResult> future,
				final Object attachment, final AIOInputActResult result, final Throwable cause) {

			final ReadCallback callback = (ReadCallback)attachment;
			if (callback != null) {
				final AIOWritableActEntry entry = result.entry();

				final long position = result.position();
				final long completedCount = result.completedCount();

				callback.aioReadFailed(this.service, entry, position, completedCount, cause);

				future.release();
			}
		}

		public final void cancelled(final AIOFuture<AIOInputActResult> future,
				final Object attachment, final AIOInputActResult result) {

			final ReadCallback callback = (ReadCallback)attachment;
			if (callback != null) {
				final AIOWritableActEntry entry = result.entry();

				final long position = result.position();
				final long completedCount = result.completedCount();

				callback.aioReadCancelled(this.service, entry, position, completedCount);

				future.release();
			}
		}

	}

	static final class OutputActCallback implements AIOFutureCallback<AIOOutputActResult> {

		private final Service service;

		private OutputActCallback(final Service service) {
			this.service = service;
		}

		public final void initiate(final AIOFuture<AIOOutputActResult> future,
				final Object attachment, final AIOOutputActResult result) {
			final WriteCallback callback = (WriteCallback)attachment;
			if (callback != null) {
				final AIOReadableActEntry entry = result.entry();
				callback.aioWriteInitiate(this.service, entry);
			}
		}

		public final void accomplished(final AIOFuture<AIOOutputActResult> future,
				final Object attachment, final AIOOutputActResult result) {

			final WriteCallback callback = (WriteCallback)attachment;
			if (callback != null) {
				final AIOReadableActEntry entry = result.entry();

				final long position = result.position();
				final long completedCount = result.completedCount();

				callback.aioWriteAccomplished(this.service, entry, position, completedCount);

				future.release();
			}
		}

		public final void timeout(final AIOFuture<AIOOutputActResult> future,
				final Object attachment, final AIOOutputActResult result) {

			final WriteCallback callback = (WriteCallback)attachment;
			if (callback != null) {
				final AIOReadableActEntry entry = result.entry();

				final long position = result.position();
				final long completedCount = result.completedCount();

				callback.aioWriteTimeout(this.service, entry, position, completedCount);

				future.release();
			}
		}

		public final void failed(final AIOFuture<AIOOutputActResult> future,
				final Object attachment, final AIOOutputActResult result, final Throwable cause) {

			final WriteCallback callback = (WriteCallback)attachment;
			if (callback != null) {
				final AIOReadableActEntry entry = result.entry();

				final long position = result.position();
				final long completedCount = result.completedCount();

				callback.aioWriteFailed(this.service, entry, position, completedCount, cause);

				future.release();
			}
		}

		public final void cancelled(final AIOFuture<AIOOutputActResult> future,
				final Object attachment, final AIOOutputActResult result) {

			final WriteCallback callback = (WriteCallback)attachment;
			if (callback != null) {
				final AIOReadableActEntry entry = result.entry();

				final long position = result.position();
				final long completedCount = result.completedCount();

				callback.aioWriteCancelled(this.service, entry, position, completedCount);

				future.release();
			}
		}

	}

}
