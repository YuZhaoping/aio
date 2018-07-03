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

import java.util.concurrent.TimeUnit;

import com.chinmobi.aio.AIOClosedSessionException;
import com.chinmobi.aio.AIOFuture;
import com.chinmobi.aio.AIOFutureCallback;
import com.chinmobi.aio.AIOInetEndpoint;
import com.chinmobi.aio.AIONotActiveException;
import com.chinmobi.aio.AIOReactor;
import com.chinmobi.aio.AIOServiceHandler;
import com.chinmobi.aio.AIOSession;
import com.chinmobi.aio.act.AIOInputActResult;
import com.chinmobi.aio.act.AIOInputActStrategy;
import com.chinmobi.aio.act.AIOOutputActResult;
import com.chinmobi.aio.act.AIOReadableActEntry;
import com.chinmobi.aio.act.AIOWritableActEntry;
import com.chinmobi.aio.scheme.AIOTransportScheme;
import com.chinmobi.aio.service.AIOConnection;

/**
 * @author <a href="mailto:yuzhaoping1970@gmail.com">Zhaoping Yu</a>
 *
 */
public final class Connection extends Service
	implements AIOConnection, AIOInetEndpoint.Observer, AIOServiceHandler.Factory {

	private final ServiceContext context;

	private final ReleaseCallback releaseCallback;

	private final AIOTransportScheme scheme;

	private final AIOInetEndpoint remoteEndpoint;

	private final Worker worker;

	private AIOInetEndpoint localEndpoint;

	private boolean enableRetry;

	private long connectTimeout;

	private Callback connectCallback;


	Connection(final ServiceContext context,
			final AIOReactor reactor, final AIOTransportScheme scheme, final ReleaseCallback releaseCallback) {
		super(reactor);

		this.context = context;

		this.scheme = scheme;

		this.releaseCallback = releaseCallback;

		this.remoteEndpoint = new AIOInetEndpoint(0, (AIOInetEndpoint.Observer)this);

		this.worker = new Worker(this);

		this.connectTimeout = 3000;
	}


	/* (non-Javadoc)
	 * @see com.chinmobi.aio.act.AIOConnection#release()
	 */
	public final void release() {
		super.doRelease();

		this.connectCallback = null;

		if (this.releaseCallback != null) {
			synchronized (this.handler) {
				this.releaseCallback.aioConnectionReleased(this);
			}
		}
	}

	/* (non-Javadoc)
	 * @see com.chinmobi.aio.act.AIOConnection#transportScheme()
	 */
	public final AIOTransportScheme transportScheme() {
		return this.scheme;
	}

	/* (non-Javadoc)
	 * @see com.chinmobi.aio.act.AIOConnection#remoteEndpoint()
	 */
	public final AIOInetEndpoint remoteEndpoint() {
		return this.remoteEndpoint;
	}

	/* (non-Javadoc)
	 * @see com.chinmobi.aio.act.AIOConnection#setLocalEndpoint(AIOInetEndpoint localEndpoint)
	 */
	public final void setLocalEndpoint(final AIOInetEndpoint localEndpoint) {
		this.localEndpoint = localEndpoint;
	}

	/*
	 * (non-Javadoc)
	 * @see com.chinmobi.aio.service.AIOConnection#getLocalEndpoint()
	 */
	public final AIOInetEndpoint getLocalEndpoint() {
		return this.localEndpoint;
	}

	@Override
	public final void close() {
		this.worker.cancel();
		super.close();
	}

	/* (non-Javadoc)
	 * @see com.chinmobi.aio.act.AIOConnection#setConnectCallback(...)
	 */
	public final void setConnectCallback(final Callback callback) {
		this.connectCallback = callback;
	}

	/* (non-Javadoc)
	 * @see com.chinmobi.aio.act.AIOConnection#getConnectCallback()
	 */
	public final Callback getConnectCallback() {
		return this.connectCallback;
	}

	/* (non-Javadoc)
	 * @see com.chinmobi.aio.act.AIOConnection#setConnectTimeout(long timeout, TimeUnit unit)
	 */
	public final void setConnectTimeout(final long timeout, final TimeUnit unit) {
		this.connectTimeout = unit.toMillis(timeout);
	}

	/* (non-Javadoc)
	 * @see com.chinmobi.aio.act.AIOConnection#enableRetry(...)
	 */
	public final void enableRetry(final boolean on) {
		this.enableRetry = on;
	}

	/* (non-Javadoc)
	 * @see com.chinmobi.aio.act.AIOConnection#connect()
	 */
	public final boolean connect() throws AIONotActiveException, InterruptedException {
		return this.worker.connect(this.connectCallback);
	}

	/* (non-Javadoc)
	 * @see com.chinmobi.aio.act.AIOConnection#connect(...)
	 */
	public final boolean connect(final Callback callback) throws AIONotActiveException, InterruptedException {
		return this.worker.connect(callback);
	}

	/* (non-Javadoc)
	 * @see com.chinmobi.aio.act.AIOConnection#waitForConnected()
	 */
	public final boolean waitForConnected() throws InterruptedException {
		return this.worker.waitForConnected();
	}

	/* (non-Javadoc)
	 * @see com.chinmobi.aio.act.AIOConnection#cancel()
	 */
	public final void cancel() {
		this.worker.cancel();
	}

	/* (non-Javadoc)
	 * @see com.chinmobi.aio.AIOInetEndpoint.Observer#onAIOEndpointChanged(...)
	 */
	public final void onAIOEndpointChanged(final AIOInetEndpoint endpoint, final boolean isSocketAddress) {
		if (!isSocketAddress) {
			close();
		}
	}

	/* (non-Javadoc)
	 * @see com.chinmobi.aio.AIOServiceHandler.Factory#createAIOServiceHandler(AIOSession session)
	 */
	public final AIOServiceHandler createAIOServiceHandler(final AIOSession session) {
		this.handler.attachToSession(session);
		return this.handler;
	}


	@Override
	public final AIOFuture<AIOInputActResult> read(final AIOWritableActEntry target,
			final ReadCallback callback, final AIOInputActStrategy strategy,
			final long timeout, final TimeUnit unit) throws AIOClosedSessionException, AIONotActiveException {
		boolean interrupted = false;
		for (;;) {
			try {
				final AIOFuture<AIOInputActResult> future = tryRead(target, callback, strategy, timeout, unit);

				if (interrupted) {
					Thread.currentThread().interrupt();
				}

				return future;
			} catch (InterruptedException ex) {
				interrupted = true;
			}
		}
	}

	private final AIOFuture<AIOInputActResult> tryRead(final AIOWritableActEntry target,
			final ReadCallback callback, final AIOInputActStrategy strategy,
			final long timeout, final TimeUnit unit)
					throws AIOClosedSessionException, AIONotActiveException, InterruptedException {
		for (;;) {
			try {
				if (isOpen()) {
					return doRead(target, callback, strategy, timeout, unit);
				} else if (!this.enableRetry) {
					throw new AIOClosedSessionException("Not open.");
				}
			} catch (AIOClosedSessionException ex) {
				if (!this.enableRetry) {
					throw ex;
				}
			}

			final FutureWrapper wrapper = createFutureWrapper();

			wrapper.set(target, callback, strategy, unit.toMillis(timeout));

			try {
				if (this.worker.connect(wrapper.connectionCallback(), wrapper)) {
					wrapper.internalRelease();

					return doRead(target, callback, strategy, timeout, unit);
				}
			} catch (AIONotActiveException ex) {
				wrapper.internalRelease();
				throw ex;
			} catch (InterruptedException ex) {
				wrapper.internalRelease();
				throw ex;
			} catch (AIOClosedSessionException ex) {
				wrapper.internalRelease();
				continue;
			}

			return wrapper.inputActFuture();
		}
	}


	@Override
	public final AIOFuture<AIOOutputActResult> write(final AIOReadableActEntry source,
			final WriteCallback callback,
			final long timeout, final TimeUnit unit) throws AIOClosedSessionException, AIONotActiveException {
		boolean interrupted = false;
		for (;;) {
			try {
				final AIOFuture<AIOOutputActResult> future = tryWrite(source, callback, timeout, unit);

				if (interrupted) {
					Thread.currentThread().interrupt();
				}

				return future;
			} catch (InterruptedException ex) {
				interrupted = true;
			}
		}
	}

	private final AIOFuture<AIOOutputActResult> tryWrite(final AIOReadableActEntry source,
			final WriteCallback callback,
			final long timeout, final TimeUnit unit)
					throws AIOClosedSessionException, AIONotActiveException, InterruptedException {
		for (;;) {
			try {
				if (isOpen()) {
					return doWrite(source, callback, timeout, unit);
				} else if (!this.enableRetry) {
					throw new AIOClosedSessionException("Not open.");
				}
			} catch (AIOClosedSessionException ex) {
				if (!this.enableRetry) {
					throw ex;
				}
			}

			final FutureWrapper wrapper = createFutureWrapper();

			wrapper.set(source, callback, unit.toMillis(timeout));

			try {
				if (this.worker.connect(wrapper.connectionCallback(), wrapper)) {
					wrapper.internalRelease();

					return doWrite(source, callback, timeout, unit);
				}
			} catch (AIONotActiveException ex) {
				wrapper.internalRelease();
				throw ex;
			} catch (InterruptedException ex) {
				wrapper.internalRelease();
				throw ex;
			} catch (AIOClosedSessionException ex) {
				wrapper.internalRelease();
				continue;
			}

			return wrapper.outputActFuture();
		}
	}


	final FutureWrapper createFutureWrapper() {
		return futureWrapperFactory().createFuture(this);
	}

	final void releaseFutureWrapper(final FutureWrapper future) {
		futureWrapperFactory().releaseFuture(future);
	}

	private final FutureWrapper.Factory futureWrapperFactory() {
		return (FutureWrapper.Factory)this.context.futureWrapperFactory();
	}

	final int cancel(final AIOFuture<AIOSession> future) {
		return this.worker.cancel(future);
	}

	@Override
	protected final boolean handleSessionClosed(final Throwable cause) {
		if (this.releaseCallback != null) {
			synchronized (this.handler) {
				this.releaseCallback.aioConnectionClosed(this);
			}
			return true;
		}
		return false;
	}

	@Override
	protected final String internalName() {
		return "Connect";
	}

	private final AIOFuture<AIOSession> doConnect() throws AIONotActiveException {
		return this.reactor().getConnector().connect(
				this.scheme,
				this.remoteEndpoint, this.localEndpoint,
				(AIOServiceHandler.Factory)this,
				(AIOFutureCallback<AIOSession>)this.worker,
				this.connectTimeout, TimeUnit.MILLISECONDS,
				null);
	}


	private static final class Worker implements AIOFutureCallback<AIOSession> {

		private final Connection connection;

		private AIOFuture<AIOSession> future;
		private Callback callback;


		Worker(final Connection connection) {
			this.connection = connection;
		}


		final int cancel(final AIOFuture<AIOSession> future) {
			synchronized (this) {
				if (future == this.future) {
					return cancel();
				}
			}
			return -1;
		}

		final int cancel() {
			int cancelled = -1;
			synchronized (this) {
				if (this.future != null) {
					cancelled = this.future.cancel(true) ? 1 : 0;
					this.future = null;

					this.callback = null;

					notifyAll();
				}
			}
			return cancelled;
		}

		final boolean connect(final Callback callback) throws AIONotActiveException, InterruptedException {
			return connect(callback, null);
		}

		final boolean connect(final Callback callback, final FutureWrapper wrapper)
				throws AIONotActiveException, InterruptedException {
			synchronized (this) {
				while (this.future != null) {
					wait();
				}

				if (this.connection.isOpen()) {
					return true;
				}

				this.callback = callback;

				this.future = this.connection.doConnect();

				if (wrapper != null) {
					wrapper.setConnectFuture(this.future);
				}

				return false;
			}
		}

		final boolean waitForConnected() throws InterruptedException {
			synchronized (this) {
				if (this.connection.isOpen()) {
					return true;
				}

				while (this.future != null) {
					wait();
				}

				return this.connection.isOpen();
			}
		}

		private final Callback finish() {
			final Callback callback;

			synchronized (this) {
				this.future = null;

				callback = this.callback;
				this.callback = null;

				notifyAll();
			}

			return callback;
		}

		public final void initiate(final AIOFuture<AIOSession> future,
				final Object attachment, final AIOSession result) {
			if (this.callback != null) {
				this.callback.aioConnectInitiate(this.connection);
			}
		}

		public final void accomplished(final AIOFuture<AIOSession> future,
				final Object attachment, final AIOSession result) {
			final Callback callback = finish();

			if (callback != null) {
				callback.aioConnectAccomplished(this.connection);
			}

			future.release();
		}

		public final void timeout(final AIOFuture<AIOSession> future,
				final Object attachment, final AIOSession result) {
			final Callback callback = finish();

			if (callback != null) {
				callback.aioConnectTimeout(this.connection);
			}

			future.release();
		}

		public final void failed(final AIOFuture<AIOSession> future,
				final Object attachment, final AIOSession result, final Throwable cause) {
			final Callback callback = finish();

			if (callback != null) {
				callback.aioConnectFailed(this.connection, cause);
			}

			future.release();
		}

		public final void cancelled(final AIOFuture<AIOSession> future,
				final Object attachment, final AIOSession result) {
			final Callback callback = finish();

			if (callback != null) {
				callback.aioConnectCancelled(this.connection);
			}

			future.release();
		}

	}

}
