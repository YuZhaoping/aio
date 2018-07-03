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
import java.util.concurrent.atomic.AtomicReference;

import com.chinmobi.aio.AIOClosedSessionException;
import com.chinmobi.aio.AIOFuture;
import com.chinmobi.aio.AIOFutureCancellable;
import com.chinmobi.aio.AIOSession;
import com.chinmobi.aio.act.AIOActEntry;
import com.chinmobi.aio.act.AIOActResult;
import com.chinmobi.aio.act.AIOInputActResult;
import com.chinmobi.aio.act.AIOInputActStrategy;
import com.chinmobi.aio.act.AIOOutputActResult;
import com.chinmobi.aio.act.AIOReadableActEntry;
import com.chinmobi.aio.act.AIOWritableActEntry;
import com.chinmobi.aio.impl.nio.BasicFuture;
import com.chinmobi.aio.impl.nio.ExceptionHandler;
import com.chinmobi.aio.impl.nio.FutureDoAccomplishCallback;
import com.chinmobi.aio.impl.nio.FutureReleaseCallback;
import com.chinmobi.aio.service.AIOConnection;
import com.chinmobi.aio.service.AIOService;

/**
 * @author <a href="mailto:yuzhaoping1970@gmail.com">Zhaoping Yu</a>
 *
 */
public final class FutureWrapper implements ExceptionHandler, FutureReleaseCallback, AIOFutureCancellable {

	private FutureWrapper next;

	private final ConnectionCallback connectionCallback;

	private final ReadCallback readCallback;
	private final WriteCallback writeCallback;

	private Connection connection;

	private AIOFuture<AIOSession> connFuture;

	private AIOActEntry entry;

	private long position;

	private long completedCount;
	private boolean endOfInput;

	private int readWriteFlag;

	private long timeout;


	FutureWrapper() {
		this.connectionCallback = new ConnectionCallback(this);

		this.readCallback = new ReadCallback(this);
		this.writeCallback = new WriteCallback(this);
	}


	private final void setConnection(final Connection connection) {
		this.connection = connection;

		this.connFuture = null;

		this.readWriteFlag = -1;

		this.readCallback.wrappedFuture = null;
		this.writeCallback.wrappedFuture = null;

		this.completedCount = 0;
		this.endOfInput = false;
	}

	final AIOConnection.Callback connectionCallback() {
		return this.connectionCallback;
	}

	final void setConnectFuture(final AIOFuture<AIOSession> connFuture) {
		this.connFuture = connFuture;
	}

	final AIOFuture<AIOInputActResult> inputActFuture() {
		return this.readCallback;
	}

	final AIOFuture<AIOOutputActResult> outputActFuture() {
		return this.writeCallback;
	}

	final void set(final AIOActEntry entry, final AIOService.ReadCallback callback,
			final AIOInputActStrategy strategy, final long timeout) {
		this.entry = entry;
		this.position = entry.position();

		this.readCallback.set(callback, strategy);

		this.readWriteFlag = 0;
		this.timeout = timeout;
	}

	final void set(final AIOActEntry entry, final AIOService.WriteCallback callback, final long timeout) {
		this.entry = entry;
		this.position = entry.position();

		this.writeCallback.set(callback);

		this.readWriteFlag = 1;
		this.timeout = timeout;
	}

	private final void setCompleted(final AIOActEntry entry, final long position, final long completedCount) {
		//this.entry = entry;

		this.position = position;
		this.completedCount = completedCount;
	}

	private final Connection reset() {
		this.entry = null;

		this.readCallback.reset();
		this.writeCallback.reset();

		this.readCallback.wrappedFuture = null;
		this.writeCallback.wrappedFuture = null;

		this.connFuture = null;

		final Connection conn = this.connection;
		this.connection = null;

		return conn;
	}


	private final AIOActEntry entry() {
		return this.entry;
	}

	private final long position() {
		return this.position;
	}

	private final long completedCount() {
		return this.completedCount;
	}

	private final boolean endOfInput() {
		return this.endOfInput;
	}


	final void internalRelease() {
		final Connection conn = reset();

		if (conn != null) {
			conn.releaseFutureWrapper(this);
		}
	}

	private final void doConnected(final AIOService service) {
		try {
			synchronized (this) {
				if (this.connFuture != null) {
					setConnectFuture(null);

					switch (this.readWriteFlag) {
					case 1: // write
						doWrite();
						return;

					default:
						doRead();
						return;
					}
				}
			}

			// user-cancelled
			doCancelled(service);

		} catch (Exception ex) {
			doFailed(service, ex);
		}
	}

	private final void doRead() throws AIOClosedSessionException {
		this.readCallback.wrappedFuture = this.connection.doRead(this.readCallback.entry(),
				this.readCallback,
				this.readCallback.strategy, this.timeout, TimeUnit.MILLISECONDS);
	}

	private final void doWrite() throws AIOClosedSessionException {
		this.writeCallback.wrappedFuture = this.connection.doWrite(this.writeCallback.entry(),
				this.writeCallback,
				this.timeout, TimeUnit.MILLISECONDS);
	}


	private final void doCancelled(final AIOService service) {
		synchronized (this) {
			setConnectFuture(null);
		}

		switch (this.readWriteFlag) {
		case 1: // write
			this.writeCallback.aioWriteCancelled(service,
					this.writeCallback.entry(), position(), completedCount());
			break;

		default:
			this.readCallback.aioReadCancelled(service,
					this.readCallback.entry(), position(), completedCount());
			break;
		}
	}

	private final void doTimeout(final AIOService service) {
		synchronized (this) {
			setConnectFuture(null);
		}

		switch (this.readWriteFlag) {
		case 1: // write
			this.writeCallback.aioWriteTimeout(service,
					this.writeCallback.entry(), position(), completedCount());
			break;

		default:
			this.readCallback.aioReadTimeout(service,
					this.readCallback.entry(), position(), completedCount());
			break;
		}
	}

	private final void doFailed(final AIOService service, final Throwable cause) {
		synchronized (this) {
			setConnectFuture(null);
		}

		switch (this.readWriteFlag) {
		case 1: // write
			this.writeCallback.aioWriteFailed(service,
					this.writeCallback.entry(), position(), completedCount(), cause);
			break;

		default:
			this.readCallback.aioReadFailed(service,
					this.readCallback.entry(), position(), completedCount(), cause);
			break;
		}
	}


	private static final class ConnectionCallback implements AIOConnection.Callback {

		private final FutureWrapper wrapper;


		private ConnectionCallback(final FutureWrapper wrapper) {
			this.wrapper = wrapper;
		}


		/* (non-Javadoc)
		 * @see com.chinmobi.aio.act.AIOConnection.Callback#aioConnectInitiate(...)
		 */
		public final void aioConnectInitiate(final AIOConnection connection) {
		}

		/* (non-Javadoc)
		 * @see com.chinmobi.aio.act.AIOConnection.Callback#aioConnectAccomplished(...)
		 */
		public final void aioConnectAccomplished(final AIOConnection connection) {
			this.wrapper.doConnected(connection);
		}

		/* (non-Javadoc)
		 * @see com.chinmobi.aio.act.AIOConnection.Callback#aioConnectTimeout(...)
		 */
		public final void aioConnectTimeout(final AIOConnection connection) {
			this.wrapper.doTimeout(connection);
		}

		/* (non-Javadoc)
		 * @see com.chinmobi.aio.act.AIOConnection.Callback#aioConnectFailed(...)
		 */
		public final void aioConnectFailed(final AIOConnection connection, final Throwable cause) {
			this.wrapper.doFailed(connection, cause);
		}

		/* (non-Javadoc)
		 * @see com.chinmobi.aio.act.AIOConnection.Callback#aioConnectCancelled(...)
		 */
		public final void aioConnectCancelled(final AIOConnection connection) {
			this.wrapper.doCancelled(connection);
		}

	}


	/*
	 * (non-Javadoc)
	 * @see com.chinmobi.aio.impl.nio.ExceptionHandler#handleUncaughtException(Throwable ex)
	 */
	public final void handleUncaughtException(final Throwable ex) {
	}

	/*
	 * (non-Javadoc)
	 * @see com.chinmobi.aio.impl.nio.FutureReleaseCallback#futureReleased()
	 */
	public final void futureReleased() {
		internalRelease();
	}

	/*
	 * (non-Javadoc)
	 * @see com.chinmobi.aio.AIOFutureCancellable#cancel(boolean)
	 */
	public final boolean cancel(final boolean mayInterruptIfRunning) {
		boolean connCancelled = false;

		if (this.connFuture != null) {
			connCancelled = this.connection.cancel(this.connFuture) > 0;
			this.connFuture = null;
		}

		switch (this.readWriteFlag) {
		case 1: // write
			this.writeCallback.doCancel(mayInterruptIfRunning, connCancelled);
			break;

		default:
			this.readCallback.doCancel(mayInterruptIfRunning, connCancelled);
			break;
		}

		return true;
	}


	private static class ActFutureBase<T extends AIOActResult> extends BasicFuture<T> {

		protected final FutureWrapper wrapper;

		protected AIOFuture<T> wrappedFuture;


		private ActFutureBase(final FutureWrapper wrapper) {
			super((Object)wrapper, (ExceptionHandler)wrapper);
			this.wrapper = wrapper;
		}

		final int doCancel(final boolean mayInterruptIfRunning, final boolean connCancelled) {
			int cancelled = -1;
			if (this.wrappedFuture != null) {
				cancelled = this.wrappedFuture.cancel(mayInterruptIfRunning) ? 1 : 0;
			}

			reset();

			if (connCancelled || cancelled > 0) {
				super.internalRelease();
			}

			return cancelled;
		}

		@Override
		protected final boolean cancelled() {
			return super.cancelled();
		}

		@Override
		protected final boolean accomplished(final T result, final FutureDoAccomplishCallback<T> doCallback) {
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

		protected void reset() {
			this.wrappedFuture = null;
		}

		/* (non-Javadoc)
		 * @see com.chinmobi.aio.AIOActResult#position()
		 */
		public final long position() {
			return this.wrapper.position();
		}

		/* (non-Javadoc)
		 * @see com.chinmobi.aio.AIOActResult#completedCount()
		 */
		public final long completedCount() {
			return this.wrapper.completedCount();
		}

		/* (non-Javadoc)
		 * @see com.chinmobi.aio.AIOInputActResult#endOfInput()
		 */
		public final boolean endOfInput() {
			return this.wrapper.endOfInput();
		}

	}

	private static final class ReadCallback extends ActFutureBase<AIOInputActResult>
		implements AIOService.ReadCallback, AIOInputActResult {

		private AIOService.ReadCallback readCallback;
		private AIOInputActStrategy strategy;


		private ReadCallback(final FutureWrapper wrapper) {
			super(wrapper);
		}


		private final void set(final AIOService.ReadCallback callback, final AIOInputActStrategy strategy) {
			super.set((FutureReleaseCallback)this.wrapper, (AIOFutureCancellable)this.wrapper,
					null, null, (AIOInputActResult)this);
			this.readCallback = callback;
			this.strategy = strategy;
		}

		@Override
		protected final void reset() {
			super.reset();
			this.readCallback = null;
			this.strategy = null;
		}

		/* (non-Javadoc)
		 * @see com.chinmobi.aio.AIOInputActResult#entry()
		 */
		public final AIOWritableActEntry entry() {
			return (AIOWritableActEntry)this.wrapper.entry();
		}

		public final void aioReadInitiate(final AIOService service, final AIOWritableActEntry target) {
			if (this.readCallback != null) {
				this.readCallback.aioReadInitiate(service, target);
			}
		}

		public final void aioReadAccomplished(final AIOService service, final AIOWritableActEntry target,
				final long position, final long completedCount, final boolean endOfInput) {
			final AIOService.ReadCallback callback;
			boolean userCancelled = false;

			synchronized (this.lock()) {
				callback = this.readCallback;

				reset();

				this.wrapper.setCompleted(target, position, completedCount);
				this.wrapper.endOfInput = endOfInput;

				userCancelled = !super.accomplished((AIOInputActResult)this, null);
			}

			if (callback != null) {
				callback.aioReadAccomplished(service, target, position, completedCount, endOfInput);

				super.release();
			} else
			if (userCancelled) {
				super.internalRelease();
			}
		}

		public final void aioReadTimeout(final AIOService service, final AIOWritableActEntry target,
				final long position, final long completedCount) {
			final AIOService.ReadCallback callback;
			boolean userCancelled = false;

			synchronized (this.lock()) {
				callback = this.readCallback;

				reset();

				this.wrapper.setCompleted(target, position, completedCount);

				userCancelled = !super.timeout();
			}

			if (callback != null) {
				callback.aioReadTimeout(service, target, position, completedCount);

				super.release();
			} else
			if (userCancelled) {
				super.internalRelease();
			}
		}

		public final void aioReadFailed(final AIOService service, final AIOWritableActEntry target,
				final long position, final long completedCount, final Throwable cause) {
			final AIOService.ReadCallback callback;
			boolean userCancelled = false;

			synchronized (this.lock()) {
				callback = this.readCallback;

				reset();

				this.wrapper.setCompleted(target, position, completedCount);

				userCancelled = !super.failed(cause);
			}

			if (callback != null) {
				callback.aioReadFailed(service, target, position, completedCount, cause);

				super.release();
			} else
			if (userCancelled) {
				super.internalRelease();
			}
		}

		public final void aioReadCancelled(final AIOService service, final AIOWritableActEntry target,
				final long position, final long completedCount) {
			final AIOService.ReadCallback callback;
			boolean userCancelled = false;

			synchronized (this.lock()) {
				callback = this.readCallback;

				reset();

				this.wrapper.setCompleted(target, position, completedCount);

				userCancelled = !super.cancelled();
			}

			if (callback != null) {
				callback.aioReadCancelled(service, target, position, completedCount);

				super.release();
			} else
			if (userCancelled) {
				super.internalRelease();
			}
		}

	}


	private static final class WriteCallback extends ActFutureBase<AIOOutputActResult>
		implements AIOService.WriteCallback, AIOOutputActResult {

		private AIOService.WriteCallback writeCallback;


		private WriteCallback(final FutureWrapper wrapper) {
			super(wrapper);
		}


		private final void set(final AIOService.WriteCallback callback) {
			super.set((FutureReleaseCallback)this.wrapper, (AIOFutureCancellable)this.wrapper,
					null, null, (AIOOutputActResult)this);
			this.writeCallback = callback;
		}

		@Override
		protected final void reset() {
			super.reset();
			this.writeCallback = null;
		}

		/* (non-Javadoc)
		 * @see com.chinmobi.aio.AIOOutputActResult#entry()
		 */
		public final AIOReadableActEntry entry() {
			return (AIOReadableActEntry)this.wrapper.entry();
		}

		public final void aioWriteInitiate(final AIOService service, final AIOReadableActEntry source) {
			if (this.writeCallback != null) {
				this.writeCallback.aioWriteInitiate(service, source);
			}
		}

		public final void aioWriteAccomplished(final AIOService service, final AIOReadableActEntry source,
				final long position, final long completedCount) {
			final AIOService.WriteCallback callback;
			boolean userCancelled = false;

			synchronized (this.lock()) {
				callback = this.writeCallback;

				reset();

				this.wrapper.setCompleted(source, position, completedCount);

				userCancelled = !super.accomplished((AIOOutputActResult)this, null);
			}

			if (callback != null) {
				callback.aioWriteAccomplished(service, source, position, completedCount);

				super.release();
			} else
			if (userCancelled) {
				super.internalRelease();
			}
		}

		public final void aioWriteTimeout(final AIOService service, final AIOReadableActEntry source,
				final long position, final long completedCount) {
			final AIOService.WriteCallback callback;
			boolean userCancelled = false;

			synchronized (this.lock()) {
				callback = this.writeCallback;

				reset();

				this.wrapper.setCompleted(source, position, completedCount);

				userCancelled = !super.timeout();
			}

			if (callback != null) {
				callback.aioWriteTimeout(service, source, position, completedCount);

				super.release();
			} else
			if (userCancelled) {
				super.internalRelease();
			}
		}

		public final void aioWriteFailed(final AIOService service, final AIOReadableActEntry source,
				final long position, final long completedCount, final Throwable cause) {
			final AIOService.WriteCallback callback;
			boolean userCancelled = false;

			synchronized (this.lock()) {
				callback = this.writeCallback;

				reset();

				this.wrapper.setCompleted(source, position, completedCount);

				userCancelled = !super.failed(cause);
			}

			if (callback != null) {
				callback.aioWriteFailed(service, source, position, completedCount, cause);

				super.release();
			} else
			if (userCancelled) {
				super.internalRelease();
			}
		}

		public final void aioWriteCancelled(final AIOService service, final AIOReadableActEntry source,
				final long position, final long completedCount) {
			final AIOService.WriteCallback callback;
			boolean userCancelled = false;

			synchronized (this.lock()) {
				callback = this.writeCallback;

				reset();

				this.wrapper.setCompleted(source, position, completedCount);

				userCancelled = !super.cancelled();
			}

			if (callback != null) {
				callback.aioWriteCancelled(service, source, position, completedCount);

				super.release();
			} else
			if (userCancelled) {
				super.internalRelease();
			}
		}

	}


	public static class Factory implements FutureWrapperFactory {

		private final AtomicReference<FutureWrapper> frees;


		public Factory() {
			this.frees = new AtomicReference<FutureWrapper>();
		}


		public final FutureWrapper createFuture(final Connection connection) {
			FutureWrapper future = pollFuture();
			if (future == null) {
				future = new FutureWrapper();
			}

			future.setConnection(connection);

			return future;
		}

		public final FutureWrapper pollFuture() {
			for (;;) {
				final FutureWrapper future = this.frees.get();

				if (future != null) {
					final FutureWrapper next = future.next;

					if (!this.frees.compareAndSet(future, next)) {
						continue;
					}

					future.next = null;
				}

				return future;
			}
		}

		public final void releaseFuture(final FutureWrapper future) {
			for (;;) {
				final FutureWrapper head = this.frees.get();

				future.next = head; // CAS piggyback
				if (!this.frees.compareAndSet(head, future)) {
					continue;
				}

				break;
			}
		}

	}

}
