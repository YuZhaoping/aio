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
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;

import com.chinmobi.aio.AIOAcceptor;
import com.chinmobi.aio.AIOClosedSessionException;
import com.chinmobi.aio.AIOInetEndpoint;
import com.chinmobi.aio.AIONotActiveException;
import com.chinmobi.aio.AIOServiceHandler;
import com.chinmobi.aio.impl.channels.ChannelsFactory;
import com.chinmobi.aio.impl.util.IllegalQueueNodeStateException;
import com.chinmobi.aio.scheme.AIOTransportScheme;

/**
 * @author <a href="mailto:yuzhaoping1970@gmail.com">Zhaoping Yu</a>
 *
 */
public final class Acceptor extends AcceptorSetImpl.Entry
	implements AIOAcceptor, EventHandler.Executor {

	private final AcceptorSetImpl acceptorSet;

	private final SessionContext sessionContext;

	private final AIOTransportScheme scheme;
	private final AIOInetEndpoint endpoint;

	private final Object lock;

	private Handler acceptHandler;

	private volatile AIOServiceHandler.Factory serviceFactory;
	private volatile Observer observer;


	Acceptor(final AcceptorSetImpl acceptorSet, final SessionContext sessionContext,
			final AIOTransportScheme scheme, final AIOInetEndpoint endpoint) {
		super();

		this.acceptorSet = acceptorSet;

		this.sessionContext = sessionContext;

		this.scheme = scheme;
		this.endpoint = endpoint;

		this.lock = new Object();
	}


	private final Object lock() {
		return this.lock;
	}

	final void free() {
		this.serviceFactory = null;

		this.acceptorSet.removeAcceptor(this);

		synchronized (this.lock()) {
			final Handler handler = this.acceptHandler;
			if (handler != null) {
				if (handler.doCancel() >= 0) {
					handler.close();
				}
			} else {
				this.observer = null;
			}
		}
	}

	final int cancel() {
		synchronized (this.lock()) {
			final Handler handler = this.acceptHandler;
			if (handler != null) {
				handler.doCancel();
				return -1;
			}
		}
		return 0;
	}

	/* (non-Javadoc)
	 * @see com.chinmobi.aio.AIOAcceptor#close()
	 */
	public final void close() {
		this.serviceFactory = null;

		this.acceptorSet.removeAcceptor(this);

		stopHandler();
	}

	/* (non-Javadoc)
	 * @see com.chinmobi.aio.AIOAcceptor#getTransportScheme()
	 */
	public final AIOTransportScheme getTransportScheme() {
		return this.scheme;
	}

	/* (non-Javadoc)
	 * @see com.chinmobi.aio.AIOAcceptor#getLocalEndpoint()
	 */
	public final AIOInetEndpoint getLocalEndpoint() {
		return this.endpoint;
	}

	/* (non-Javadoc)
	 * @see com.chinmobi.aio.AIOAcceptor#setObserver(Observer observer)
	 */
	public final void setObserver(final Observer observer) {
		this.observer = observer;
	}

	/*
	 * (non-Javadoc)
	 * @see com.chinmobi.aio.AIOAcceptor#getObserver()
	 */
	public final Observer getObserver() {
		return this.observer;
	}

	/* (non-Javadoc)
	 * @see com.chinmobi.aio.AIOAcceptor#isActive()
	 */
	public final boolean isActive() {
		synchronized (this.lock()) {
			return isHandlerActive();
		}
	}

	/* (non-Javadoc)
	 * @see com.chinmobi.aio.AIOAcceptor#start(...)
	 */
	public final void start(final AIOServiceHandler.Factory serviceFactory)
			throws AIONotActiveException {

		this.serviceFactory = serviceFactory;

		synchronized (this.lock()) {
			try {
				if (isQueued() && !isHandlerActive()) {
					final Handler handler = new Handler(this);
					this.acceptHandler = handler;

					handler.toPendingState();
					this.sessionContext.demultiplexer().putRunnable(handler.activeNode(), 'a', "RUN_ACCEPT", true);

				} else if (!isQueued()) {
					throw new IllegalStateException("The acceptor is closed.");
				}
			} catch (AIONotActiveException ex) {
				stop();
				throw ex;
			} catch (RuntimeException ex) { // IllegalQueueNodeStateException
				stop();
				throw ex;
			}
		}
	}

	/* (non-Javadoc)
	 * @see com.chinmobi.aio.AIOAcceptor#stop()
	 */
	public final void stop() {
		this.serviceFactory = null;

		stopHandler();
	}

	/*
	 * (non-Javadoc)
	 * @see com.chinmobi.aio.AIOAcceptor#getServiceHandlerFactory()
	 */
	public final AIOServiceHandler.Factory getServiceHandlerFactory() {
		return this.serviceFactory;
	}


	/* (non-Javadoc)
	 * @see com.chinmobi.aio.impl.nio.EventHandler.Executor#handlerDoExecute(Object object)
	 */
	public final int handlerDoExecute(final Object object) {
		final ExtendedSession session = (ExtendedSession)object;

		final AIOServiceHandler.Factory factory = this.serviceFactory;
		if (factory != null) {
			final AIOServiceHandler service = factory.createAIOServiceHandler(session);
			if (service == null) {
				throw new RuntimeException("Null created service.");
			}

			session.setServiceHandler(service);

			try {
				session.handleAIOSessionOpened(session);
			} catch (AIOClosedSessionException ex) {
				session.close();
			}
		} else {
			session.close();
		}

		return 0;
	}


	private final boolean isHandlerActive() {
		return (this.acceptHandler != null && this.acceptHandler.isActive());
	}

	private final void stopHandler() {
		synchronized (this.lock()) {
			final Handler handler = this.acceptHandler;
			if (handler != null) {
				if (handler.doCancel() > 0) {
					this.sessionContext.demultiplexer().wakeup();
				}
			}
		}
	}

	private final void run(final Handler handler) {
		if (!handler.isActive()) {
			synchronized (this.lock()) {
				if (this.acceptHandler == handler) {
					this.acceptHandler = null;
				}
			}
			handler.close();
			return;
		}

		final Observer observer = this.observer;
		if (observer != null) {
			try {
				observer.onAIOAcceptorStarted(this);
			} catch (Throwable ignore) {
				handleUncaughtException(ignore);
			}

			if (!handler.isActive()) {
				handler.close();
				return;
			}
		}

		if (this.scheme.isAddressResolvable()) {
			try {
				resolve(this.endpoint);
			} catch (UnknownHostException ex) {
				handler.fail(ex);
				return;
			} catch (RuntimeException ex) {	// SecurityException, IllegalArgumentException
				handler.fail(ex);
				return;
			} catch (Throwable error) {
				handler.fail(error);
				return;
			}
		}

		final AcceptableChannel acceptableChannel;
		try {
			acceptableChannel = ChannelsFactory.createAcceptableChannel(this.scheme);
			handler.acceptableChannel = acceptableChannel;
		} catch (IOException ex) {
			handler.fail(ex);
			return;
		} catch (RuntimeException ex) {	// IllegalArgumentException, IllegalBlockingModeException
			handler.fail(ex);
			return;
		} catch (Throwable error) {
			handler.fail(error);
			return;
		}

		run(handler, acceptableChannel);
	}

	private final void run(final Handler handler, final AcceptableChannel acceptableChannel) {
		if (!handler.isActive()) {
			handler.close();
			return;
		}

		final boolean accepted;
		try {
			accepted = acceptableChannel.bind(this.endpoint);
		} catch (IOException ex) { // SocketException, IOException
			handler.fail(ex);
			return;
		} catch (RuntimeException ex) {	// SecurityException, IllegalArgumentException
			handler.fail(ex);
			return;
		} catch (Throwable error) {
			handler.fail(error);
			return;
		}

		if (accepted) {
			if (notifyAccepting() && !handler.isActive()) {
				handler.close();
				return;
			}
		}

		try {
			this.sessionContext.demultiplexer().registerChannel(acceptableChannel.selectableChannel(), acceptableChannel.acceptOps(),
					(EventHandler.Factory)handler, acceptableChannel, false, !accepted);
			if (accepted) {
				doAccept(handler, false);
			} else {
				notifyAccepting();
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

	private final void runNext(final Handler handler) {
		synchronized (this.lock()) {
			try {
				if (handler.toPendingState()) {
					this.sessionContext.demultiplexer().putRunnable(handler.activeNode(), 'n', "RUN_NEXT_ACCEPT", true);
				}
			} catch (AIONotActiveException ex) {
				stop();
				throw ex;
			} catch (RuntimeException ex) { // IllegalQueueNodeStateException
				stop();
				throw ex;
			}
		}
	}

	private static void resolve(final AIOInetEndpoint endpoint) throws UnknownHostException {
		SocketAddress socketAddr = endpoint.getSocketAddress();

		if (socketAddr == null) {
			final String hostName = endpoint.getHostName();

			if (hostName != null) {
				final InetAddress addr = InetAddress.getByName(hostName);
				socketAddr = new InetSocketAddress(addr, endpoint.getPort());
			} else {
				socketAddr = new InetSocketAddress(endpoint.getPort());
			}

			endpoint.set(socketAddr);
		}
	}

	private final int doAccept(final Handler handler, final boolean enableAdvanceSelect) {

		final AcceptableChannel acceptableChannel = handler.acceptableChannel;
		if (acceptableChannel == null) {
			handler.close();
			return -1;
		}

		final AcceptedTransport transport;
		try {
			transport = acceptableChannel.accept(handler);
		} catch (ClosedChannelException ex) {	// ClosedChannelException,
												// AsynchronousCloseException, ClosedByInterruptException
			handler.fail(ex);
			return -1;
		} catch (IOException ex) { // SocketException, IOException
			handler.fail(ex);
			return -1;
		} catch (RuntimeException ex) {	// NotYetBoundException, SecurityException
			handler.fail(ex);
			return -1;
		} catch (Throwable error) {
			handler.fail(error);
			return -1;
		}

		if (transport != null) {
			if (enableAdvanceSelect && !handler.advanceSelect()) {
				transport.close();
				handler.close();
				return -1;
			}
		} else {
			return 0;
		}

		try {
			transport.execute(handler, this.sessionContext, (EventHandler.Executor)this);
		} catch (ClosedChannelException done) {
		} catch (IOException done) {
		} catch (AIONotActiveException done) {
		} catch (IllegalQueueNodeStateException done) {
		} catch (RuntimeException done) {
			// IllegalBlockingModeException, IllegalSelectorException
			// CancelledKeyException, IllegalArgumentException
		}
		return enableAdvanceSelect ? -1 : 0;
	}

	private final void stopped(final Handler handler, final Throwable cause) {
		handler.acceptableChannel = null;

		final Observer observer = this.observer;

		synchronized (this.lock()) {
			if (this.acceptHandler == handler) {
				this.acceptHandler = null;
				this.serviceFactory = null;

				if (!isQueued()) {
					this.observer = null;
				}
			} else {
				if (cause != null) {
					handleUncaughtException(cause);
				}
				return;
			}
		}

		if (observer != null) {
			try {
				observer.onAIOAcceptorStopped(this, handler, cause);
			} catch (Throwable ignore) {
				handleUncaughtException(ignore);
			}
		} else
		if (cause != null) {
			handleUncaughtException(cause);
		}
	}

	private final boolean notifyAccepting() {
		final Observer observer = this.observer;
		if (observer != null) {
			try {
				observer.onAIOAcceptorAccepting(this);
			} catch (Throwable ignore) {
				handleUncaughtException(ignore);
			}
			return true;
		}
		return false;
	}

	private final void handleUncaughtException(Throwable cause) {
		this.sessionContext.handleUncaughtException(cause);
	}

	@Override
	public final String toString() {
		final Handler handler;
		synchronized (this.lock()) {
			handler = this.acceptHandler;
		}

		if (handler != null) {
			return handler.toString();
		} else {
			final StringBuilder builder = new StringBuilder();
			printString(builder);
			return builder.toString();
		}
	}

	private final void printString(final StringBuilder builder) {
		builder.append("Acceptor [");
		builder.append("scheme: ").append(this.scheme.name());
		builder.append(", bind: ").append(this.endpoint.getHostName());
		builder.append(":").append(this.endpoint.getPort());
		builder.append("]");
	}


	private static final class Handler extends AcceptorHandler implements EventHandler.Factory, Runnable {

		private final Acceptor acceptor;

		private AcceptableChannel acceptableChannel;

		private EventHandler wrappedHandler;


		private Handler(final Acceptor acceptor) {
			super();
			this.acceptor = acceptor;
		}


		public final void wrapHandler(final EventHandler handler) {
			this.wrappedHandler = handler;
		}

		public final AIOServiceHandler.Factory getServiceFactory() {
			return this.acceptor.serviceFactory;
		}

		public final EventHandler.Executor getSessionCreatedExecutor() {
			return (EventHandler.Executor)this.acceptor;
		}

		public final void toAcceptNext() {
			this.acceptor.runNext(this);
		}

		public final void doStop(final Throwable cause) {
			this.acceptor.stopped(this, cause);
		}

		final int doCancel() {
			final EventHandler handler = this.wrappedHandler;
			if (handler != null) {
				return handler.cancel();
			}
			return cancel();
		}


		@Override
		public final Runnable getRunnable() {
			return this;
		}

		/* (non-Javadoc)
		 * @see com.chinmobi.aio.impl.nio.EventHandler.Factory#createEventHandler(...)
		 */
		public final EventHandler createEventHandler(final Object token) {
			return this;
		}

		/* (non-Javadoc)
		 * @see java.lang.Runnable#run()
		 */
		public final void run() {
			final AcceptableChannel channel = this.acceptableChannel;
			if (channel != null) {
				this.acceptor.run(this, channel);
			} else {
				this.acceptor.run(this);
			}
		}

		@Override
		public final SelectableChannel selectableChannel() {
			final AcceptableChannel channel = this.acceptableChannel;
			if (channel != null) {
				return channel.selectableChannel();
			}
			return null;
		}

		@Override
		protected final void onClearSelectionKey() {
		}

		@Override
		protected final void onCloseChannel() {
			final AcceptableChannel channel = this.acceptableChannel;
			if (channel != null) {
				channel.close();
			}
		}

		@Override
		protected final int process(final SelectionKey key) {
			final AcceptableChannel channel = this.acceptableChannel;
			if (channel != null && channel.isAcceptable(key)) {
				return this.acceptor.doAccept(this, true);
			}
			return 0;
		}

		@Override
		protected final void onFailed(final Throwable cause) {
			this.acceptor.stopped(this, cause);
		}

		@Override
		protected final void onClosed() {
			this.acceptor.stopped(this, null);
		}

		@Override
		public final String toString() {
			final StringBuilder builder = new StringBuilder();

			this.acceptor.printString(builder);

			super.printTraceString(builder);

			return builder.toString();
		}

	}

}
