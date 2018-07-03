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

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.UnknownHostException;
//import java.nio.channels.AlreadyConnectedException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;

import com.chinmobi.aio.AIOClosedSessionException;
import com.chinmobi.aio.AIOFutureCallback;
import com.chinmobi.aio.AIOFutureCancellable;
import com.chinmobi.aio.AIOFutureStatus;
import com.chinmobi.aio.AIOInetEndpoint;
import com.chinmobi.aio.AIONotActiveException;
import com.chinmobi.aio.AIOServiceHandler;
import com.chinmobi.aio.AIOSession;
import com.chinmobi.aio.impl.channels.ChannelsFactory;
import com.chinmobi.aio.impl.util.IllegalQueueNodeStateException;
import com.chinmobi.aio.scheme.AIOTransportScheme;

/**
 * @author <a href="mailto:yuzhaoping1970@gmail.com">Zhaoping Yu</a>
 *
 */
public final class ConnectRequest implements EventHandler.Executor, Runnable,
		AIOFutureCancellable, FutureReleaseCallback, FutureDoAccomplishCallback<AIOSession>,
			ExceptionHandler {

	private final Connector connector;

	private final BasicFuture<AIOSession> future;

	private final Handler eventHandler;

	private AIOTransportScheme scheme;
	private AIOInetEndpoint remoteEndpoint;
	private AIOInetEndpoint localEndpoint;
	private AIOServiceHandler.Factory serviceFactory;

	private SessionContext sessionContext;


	ConnectRequest(final Connector connector) {
		super();
		this.connector = connector;

		this.future = new BasicFuture<AIOSession>((ExceptionHandler)this);

		this.eventHandler = new Handler(this);
	}


	final void set(final AIOTransportScheme scheme, final AIOInetEndpoint remote, final AIOInetEndpoint local,
			final AIOServiceHandler.Factory serviceFactory,
			final AIOFutureCallback<AIOSession> callback, final long timeout,
			final Object attachment) {
		this.future.set((FutureReleaseCallback)this, (AIOFutureCancellable)this, callback, attachment);

		this.scheme = scheme;
		this.remoteEndpoint = remote;
		this.localEndpoint = local;
		this.serviceFactory = serviceFactory;

		this.eventHandler.reset();
		this.eventHandler.setTimeout(timeout);
	}

	final void setSessionContext(final SessionContext sessionContext) {
		this.sessionContext = sessionContext;
	}

	final ConnectRequest.Handler connectHandler() {
		return this.eventHandler;
	}

	final BasicFuture<AIOSession> future() {
		return this.future;
	}

	final void release() {
		this.future.internalRelease();
	}

	private final SessionContext sessionContext() {
		return this.sessionContext;
	}


	/* (non-Javadoc)
	 * @see java.lang.Runnable#run()
	 */
	public final void run() {
		try {
			if (this.eventHandler.isActive() && this.future.initiate()) {
				connect();
			} else {
				this.eventHandler.close();
			}
		} catch (Throwable ex) { // for future.initiate()
			this.eventHandler.fail(ex);
		}
	}

	private final void connect() {
		final AIOTransportScheme scheme = this.scheme;
		final AIOInetEndpoint remoteEndpoint = this.remoteEndpoint;
		final AIOInetEndpoint localEndpoint = this.localEndpoint;

		if (scheme.isAddressResolvable()) {
			try {
				resolve(remoteEndpoint);

				if (localEndpoint != null) {
					resolve(localEndpoint);
				}
			} catch (UnknownHostException ex) {
				failed(ex);
				return;
			} catch (RuntimeException ex) {	// SecurityException, IllegalArgumentException
				failed(ex);
				return;
			}
		}

		final ConnectableChannel connectableChannel;
		try {
			connectableChannel = ChannelsFactory.createConnectableChannel(scheme);
			this.eventHandler.connectableChannel = connectableChannel;
		} catch (IOException ex) { // ClosedChannelException, IOException
			failed(ex);
			return;
		} catch (RuntimeException ex) {	// IllegalArgumentException, IllegalBlockingModeException
			failed(ex);
			return;
		}

		final boolean connected;
		try {
			if (localEndpoint != null) {
				connectableChannel.bind(localEndpoint);
			}

			connected = connectableChannel.connect(remoteEndpoint);
		} catch (ClosedChannelException ex) { 	// ClosedChannelException,
												// AsynchronousCloseException, ClosedByInterruptException
			failed(ex);
			return;
		} catch (IOException ex) { // SocketException, IOException
			failed(ex);
			return;
		//} catch (AlreadyConnectedException ex) {
		} catch (RuntimeException ex) { // IllegalArgumentException, SecurityException
										// ConnectionPendingException, UnresolvedAddressException,
										// UnsupportedAddressTypeException
			failed(ex);
			return;
		}

		try {
			if (!connected) {
				sessionContext().demultiplexer().registerChannel(connectableChannel.selectableChannel(), connectableChannel.connectOps(),
						(EventHandler.Factory)this.eventHandler, connectableChannel, false);
			} else {
				this.eventHandler.connectableChannel = null;
				connectableChannel.execute(false, this.eventHandler, sessionContext(), (EventHandler.Executor)this);
			}
		} catch (ClosedChannelException done) {
		} catch (IOException done) {
		} catch (AIONotActiveException done) {
		} catch (IllegalQueueNodeStateException done) {
		} catch (RuntimeException done) {
			// IllegalBlockingModeException, IllegalSelectorException
			// CancelledKeyException, IllegalArgumentException
		}
	}

	/* (non-Javadoc)
	 * @see com.chinmobi.aio.AIOFutureCancellable#cancel(boolean mayInterruptIfRunning)
	 */
	public final boolean cancel(final boolean mayInterruptIfRunning) {
		if (this.eventHandler.cancel() > 0) {
			sessionContext().demultiplexer().wakeup();
		}
		return true;
	}

	/* (non-Javadoc)
	 * @see com.chinmobi.aio.impl.nio.FutureReleaseCallback#futureReleased()
	 */
	public final void futureReleased() {
		this.scheme = null;
		this.remoteEndpoint = null;
		this.localEndpoint = null;
		this.serviceFactory = null;

		this.sessionContext = null;

		this.connector.releaseConnectRequest(this);
	}

	/* (non-Javadoc)
	 * @see com.chinmobi.aio.impl.nio.FutureDoAccomplishCallback#futureDoAccomplish(T result)
	 */
	public final void futureDoAccomplish(final AIOSession session) {
		final AIOServiceHandler.Factory serviceFactory = this.serviceFactory;
		if (serviceFactory != null) {
			final AIOServiceHandler service = serviceFactory.createAIOServiceHandler(session);
			((ExtendedSession)session).setServiceHandler(service);
		}
	}

	/* (non-Javadoc)
	 * @see com.chinmobi.aio.impl.nio.EventHandler.Executor#handlerDoExecute(Object object)
	 */
	public final int handlerDoExecute(final Object object) {
		final ExtendedSession session = (ExtendedSession)object;

		return accomplished(session);
	}

	private static void resolve(final AIOInetEndpoint endpoint) throws UnknownHostException {
		SocketAddress socketAddr = endpoint.getSocketAddress();

		if (socketAddr != null) {
			if (socketAddr instanceof InetSocketAddress) {
				final InetSocketAddress isa = (InetSocketAddress)socketAddr;
				if (!isa.isUnresolved()) {
					return;
				}
			} else {
				return;
			}
		}
		final String hostName = endpoint.getHostName();
		if (hostName != null) {
			final InetAddress addr = InetAddress.getByName(hostName);
			socketAddr = new InetSocketAddress(addr, endpoint.getPort());
		} else {
			socketAddr = new InetSocketAddress(endpoint.getPort());
		}

		endpoint.set(socketAddr);
	}

	private final int doConnect(final ConnectableChannel connectableChannel) {
		try {
			connectableChannel.finishConnect();

			if (!connectableChannel.isConnected()) {
				throw new ClosedChannelException();
			}
		} catch (ClosedChannelException ex) {	// ClosedChannelException,
												// AsynchronousCloseException, ClosedByInterruptException
			failed(ex);
			return -1;
		} catch (IOException ex) { // SocketException, IOException
			failed(ex);
			return -1;
		} catch (RuntimeException ex) {	// NoConnectionPendingException
			failed(ex);
			return -1;
		} catch (Throwable error) {
			failed(error);
			return -1;
		}

		try {
			this.eventHandler.connectableChannel = null;
			connectableChannel.execute(true, this.eventHandler, sessionContext(), (EventHandler.Executor)this);
		} catch (ClosedChannelException done) {
		} catch (IOException done) {
		} catch (AIONotActiveException done) {
		} catch (IllegalQueueNodeStateException done) {
		} catch (RuntimeException done) {
			// IllegalBlockingModeException, IllegalSelectorException
			// CancelledKeyException, IllegalArgumentException
		}

		return -1;
	}

	private final void failed(final Throwable cause) {
		this.eventHandler.fail(cause);
	}

	private final int accomplished(final ExtendedSession session) {
		this.eventHandler.release();

		final boolean futureDone = this.future.accomplished(session, (FutureDoAccomplishCallback<AIOSession>)this);

		if (!futureDone) {
			session.close();
			release();
		} else {
			final AIOServiceHandler service = session.getServiceHandler();
			if (service != null) {
				try {
					session.handleAIOSessionOpened(session);
				} catch (AIOClosedSessionException ex) {
					session.close();
				}
			}
		}
		return 0;
	}

	private final void terminated(final int futureStatus, final Throwable cause) {
		boolean futureDone = false;

		switch (futureStatus) {
		case AIOFutureStatus.CASE_TIMEOUT:
			futureDone = this.future.timeout();
			break;
		case AIOFutureStatus.CASE_FAILED:
			futureDone = this.future.failed(cause);
			break;
		default:
			if (cause != null) {
				futureDone = this.future.failed(cause);
			} else {
				futureDone = this.future.cancelled();
			}
		}

		if (!futureDone) {
			release();
		}
	}

	/* (non-Javadoc)
	 * @see com.chinmobi.aio.impl.nio.ExceptionHandler#handleUncaughtException(Throwable ex)
	 */
	public final void handleUncaughtException(Throwable ex) {
		final SessionContext sessionContext = sessionContext();
		if (sessionContext != null) {
			if (ex != null) {
				ex = new ThrowableWrapper(toString(), ex);
			}
			sessionContext.handleUncaughtException(ex);
		}
	}

	@Override
	public final String toString() {
		return this.eventHandler.toString();
	}


	static final class Handler extends EventHandler implements EventHandler.Factory {

		private final ConnectRequest request;

		private ConnectableChannel connectableChannel;


		Handler(final ConnectRequest request) {
			super();
			this.request = request;
		}

		final ConnectRequest connectRequest() {
			return this.request;
		}

		@Override
		public final Runnable getRunnable() {
			return this.request;
		}

		/* (non-Javadoc)
		 * @see com.chinmobi.aio.impl.nio.EventHandler.Factory#createEventHandler(...)
		 */
		public final EventHandler createEventHandler(final Object token) {
			return this;
		}

		@Override
		public final SelectableChannel selectableChannel() {
			final ConnectableChannel channel = this.connectableChannel;
			if (channel != null) {
				return channel.selectableChannel();
			}
			return null;
		}

		@Override
		protected final void onClearSelectionKey() {
			this.connectableChannel = null;
		}

		@Override
		protected final void onCloseChannel() {
			final ConnectableChannel channel = this.connectableChannel;
			if (channel != null) {
				channel.close();
			}
		}

		@Override
		protected final int process(final SelectionKey key) {
			final ConnectableChannel channel = this.connectableChannel;
			if (channel != null && channel.isConnectable(key)) {
				return this.request.doConnect(channel);
			}
			return 0;
		}

		@Override
		protected final int onTimeout() {
			doClose(EventHandlerCallback.CASE_TIMEOUT);
			this.request.terminated(AIOFutureStatus.CASE_TIMEOUT, null);
			return -1;
		}

		@Override
		protected final void onFailed(final Throwable cause) {
			this.request.terminated(AIOFutureStatus.CASE_FAILED, cause);
		}

		@Override
		protected final void onClosed() {
			this.request.terminated(AIOFutureStatus.CASE_CANCELLED, null);
		}

		@Override
		protected final void onReleased() {
			// Nothing to do.
		}

		@Override
		public final String toString() {
			final StringBuilder builder = new StringBuilder();

			builder.append("Connector [");

			final AIOTransportScheme scheme = this.request.scheme;
			builder.append("scheme: ").append((scheme != null) ? scheme.name() : "null");

			final AIOInetEndpoint remoteEndpoint = this.request.remoteEndpoint;
			if (remoteEndpoint != null) {
				builder.append(", remote: ").append(remoteEndpoint.getHostName());
				builder.append(":").append(remoteEndpoint.getPort());
			} else {
				builder.append(", remote: ").append("null");
			}

			final AIOInetEndpoint localEndpoint = this.request.localEndpoint;
			if (localEndpoint != null) {
				builder.append(", local: ").append(localEndpoint.getHostName());
				builder.append(":").append(localEndpoint.getPort());
			}

			builder.append("]");

			super.printTraceString(builder);

			return builder.toString();
		}

	}

}
