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
package com.chinmobi.aio.impl.channels;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.DatagramChannel;
import java.nio.channels.GatheringByteChannel;
import java.nio.channels.IllegalBlockingModeException;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.ScatteringByteChannel;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.WritableByteChannel;

import com.chinmobi.aio.AIOClosedSessionException;
import com.chinmobi.aio.AIOInetEndpoint;
import com.chinmobi.aio.AIOServiceHandler;
import com.chinmobi.aio.AIOSession;
import com.chinmobi.aio.impl.nio.AcceptableChannel;
import com.chinmobi.aio.impl.nio.AcceptedTransport;
import com.chinmobi.aio.impl.nio.AcceptorHandler;
import com.chinmobi.aio.impl.nio.EventHandler;
import com.chinmobi.aio.impl.nio.HandshakeHandler;
import com.chinmobi.aio.impl.nio.Session;
import com.chinmobi.aio.impl.nio.SessionContext;
import com.chinmobi.aio.impl.nio.TransportChannel;
import com.chinmobi.aio.scheme.AIODatagramScheme;

/**
 * @author <a href="mailto:yuzhaoping1970@gmail.com">Zhaoping Yu</a>
 *
 */
public class DatagramAcceptableChannel implements AcceptableChannel {

	private final AIODatagramScheme scheme;

	private final DatagramChannel datagramChannel;


	DatagramAcceptableChannel(final AIODatagramScheme scheme) throws IOException {
		this.scheme = scheme;

		this.datagramChannel = DatagramChannel.open();

		try {
			this.datagramChannel.configureBlocking(false);
		} catch (ClosedChannelException ex) {
			close();
			throw ex;
		} catch (IOException ex) {
			close();
			throw ex;
		} catch (IllegalBlockingModeException ex) {
			close();
			throw ex;
		}
	}


	public final boolean bind(final AIOInetEndpoint addr) throws IOException {
		final DatagramSocket socket = this.datagramChannel.socket();

		try {
			this.scheme.prepareBind(socket);
		} catch (SocketException ex) {
			throw ex;
		}

		try {
			socket.bind(addr.getSocketAddress());
		} catch (SocketException ex) {
			throw ex;
		} catch (SecurityException ex) {
			throw ex;
		} catch (IllegalArgumentException ex) {
			throw ex;
		}

		try {
			this.scheme.socketReady(socket);
		} catch (SocketException ex) {
			throw ex;
		}

		return true;
	}

	public final SelectableChannel selectableChannel() {
		return this.datagramChannel;
	}

	public final int acceptOps() {
		return 0;
	}

	public final boolean isAcceptable(final SelectionKey key) {
		try {
			return key.isReadable();
		} catch (CancelledKeyException ex) {
			throw ex;
		}
	}

	public final AcceptedTransport accept(final AcceptorHandler acceptorHandler) throws IOException {
		acceptorHandler.clearInterestOps(acceptOps(), true);

		return new Transport(acceptorHandler, this.datagramChannel);
	}

	public final void close() {
		this.datagramChannel.socket().close();

		try {
			this.datagramChannel.close();
		} catch (IOException ignore) {
		}
	}


	private static final class Transport implements AcceptedTransport, TransportChannel,
			AIOServiceHandler, EventHandler.Executor {

		private final AcceptorHandler acceptorHandler;

		private final DatagramChannel datagramChannel;

		private AIOServiceHandler serviceHandler;


		Transport(final AcceptorHandler acceptorHandler, final DatagramChannel datagramChannel) {
			this.acceptorHandler = acceptorHandler;
			this.datagramChannel = datagramChannel;
		}


		public final void execute(final AcceptorHandler acceptorHandler,
				final SessionContext sessionContext, final EventHandler.Executor executor)
				throws IOException {
			sessionContext.sessionCreator().createSession((EventHandler)this.acceptorHandler, (TransportChannel)this, SelectionKey.OP_READ,
					(EventHandler.Executor)this);
		}

		public final int open() throws IOException {
			// Nothing to do.
			return 0;
		}

		public final void close() {
			this.datagramChannel.socket().close();

			try {
				this.datagramChannel.close();
			} catch (IOException ignore) {
			}
		}

		public final boolean isSupportedInput() {
			return true;
		}

		public final boolean isSupportedOutput() {
			return true;
		}

		public final boolean shutdownInput() {
			// Nothing to do.
			return true;
		}

		public final boolean shutdownOutput() {
			// Nothing to do.
			return true;
		}


		public final int handlerDoExecute(final Object object) {
			final Session session = (Session)object;

			this.acceptorHandler.wrapHandler(session.getEventHandler());

			final AIOServiceHandler.Factory factory = this.acceptorHandler.getServiceFactory();
			if (factory != null) {
				this.serviceHandler = factory.createAIOServiceHandler(session);
				session.setServiceHandler(this);

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

		public final HandshakeHandler getHandshakeHandler() {
			return null;
		}

		public final SelectableChannel selectableChannel() {
			return this.datagramChannel;
		}

		public final ReadableByteChannel readableChannel() {
			return this.datagramChannel.isConnected() ? this.datagramChannel : null;
		}

		public final ScatteringByteChannel scatteringChannel() {
			return this.datagramChannel.isConnected() ? this.datagramChannel : null;
		}

		public final WritableByteChannel writableChannel() {
			return this.datagramChannel.isConnected() ? this.datagramChannel : null;
		}

		public final GatheringByteChannel gatheringChannel() {
			return this.datagramChannel.isConnected() ? this.datagramChannel : null;
		}

		public final DatagramChannel datagramChannel() {
			return this.datagramChannel;
		}

		public final SocketAddress remoteSocketAddress() {
			return this.datagramChannel.socket().getRemoteSocketAddress();
		}

		public final SocketAddress localSocketAddress() {
			return this.datagramChannel.socket().getLocalSocketAddress();
		}


		public final void handleAIOSessionOpened(final AIOSession session) throws AIOClosedSessionException {
			final AIOServiceHandler handler = this.serviceHandler;
			if (handler != null) {
				handler.handleAIOSessionOpened(session);
			}
		}

		public final boolean handleAIOSessionInputReady(final AIOSession session)
				throws IOException, AIOClosedSessionException {
			final AIOServiceHandler handler = this.serviceHandler;
			if (handler != null) {
				return handler.handleAIOSessionInputReady(session);
			}
			return false;
		}

		public final boolean handleAIOSessionOutputReady(final AIOSession session)
				throws IOException, AIOClosedSessionException {
			final AIOServiceHandler handler = this.serviceHandler;
			if (handler != null) {
				return handler.handleAIOSessionOutputReady(session);
			}
			return false;
		}

		public final void handleAIOSessionTimeout(final AIOSession session) throws AIOClosedSessionException {
			final AIOServiceHandler handler = this.serviceHandler;
			if (handler != null) {
				handler.handleAIOSessionTimeout(session);
			}
		}

		public final void handleAIOSessionClosed(final AIOSession session, final Throwable cause) {
			final AIOServiceHandler handler = this.serviceHandler;
			if (handler != null) {
				this.serviceHandler = null;
				try {
					handler.handleAIOSessionClosed(session, cause);
				} catch (Throwable ignore) {
				}
			}

			this.acceptorHandler.doStop(cause);
		}

		@Override
		public final String toString() {
			final AIOServiceHandler handler = this.serviceHandler;
			if (handler != null) {
				return handler.toString();
			} else {
				return "DatagramAcceptableService";
			}
		}

	}

}
