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
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.channels.AlreadyConnectedException;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.ClosedByInterruptException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ConnectionPendingException;
import java.nio.channels.DatagramChannel;
import java.nio.channels.GatheringByteChannel;
import java.nio.channels.IllegalBlockingModeException;
import java.nio.channels.NoConnectionPendingException;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.ScatteringByteChannel;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.nio.channels.UnresolvedAddressException;
import java.nio.channels.UnsupportedAddressTypeException;
import java.nio.channels.WritableByteChannel;

import com.chinmobi.aio.AIOInetEndpoint;
import com.chinmobi.aio.impl.channels.ssl.SSLChannelsFactory;
import com.chinmobi.aio.impl.nio.ConnectableChannel;
import com.chinmobi.aio.impl.nio.EventHandler;
import com.chinmobi.aio.impl.nio.HandshakeHandler;
import com.chinmobi.aio.impl.nio.Session;
import com.chinmobi.aio.impl.nio.SessionContext;
import com.chinmobi.aio.impl.nio.TransportChannel;
import com.chinmobi.aio.scheme.AIOSocketScheme;

/**
 * @author <a href="mailto:yuzhaoping1970@gmail.com">Zhaoping Yu</a>
 *
 */
public final class SocketConnectableChannel implements ConnectableChannel {

	private final AIOSocketScheme scheme;

	private final Transport transport;


	SocketConnectableChannel(final AIOSocketScheme scheme) throws IOException {
		this.scheme = scheme;

		this.transport = new Transport(SocketChannel.open());
		try {
			this.transport.socketChannel().configureBlocking(false);
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


	public final void bind(final AIOInetEndpoint localAddr) throws IOException {
		final Socket socket = this.transport.socketChannel().socket();

		try {
			this.scheme.prepareBind(socket);
		} catch (SocketException ex) {
			throw ex;
		}

		try {
			socket.bind(localAddr.getSocketAddress());
		} catch (IOException ex) {
			throw ex;
		} catch (IllegalArgumentException ex) {
			throw ex;
		}
	}

	public final SelectableChannel selectableChannel() {
		return this.transport.selectableChannel();
	}

	public final int connectOps() {
		return SelectionKey.OP_CONNECT;
	}

	public final boolean connect(final AIOInetEndpoint remoteAddr) throws IOException {
		try {
			if (this.transport.socketChannel().connect(remoteAddr.getSocketAddress())) {
				connected();
				return true;
			} else {
				return false;
			}
		} catch (ClosedByInterruptException ex) {
			throw ex;
		} catch (AsynchronousCloseException ex) {
			throw ex;
		} catch (ClosedChannelException ex) {
			throw ex;
		} catch (IOException ex) {
			throw ex;
		} catch (AlreadyConnectedException ex) {
			throw ex;
		} catch (ConnectionPendingException ex) {
			throw ex;
		} catch (UnresolvedAddressException ex) {
			throw ex;
		} catch (UnsupportedAddressTypeException ex) {
			throw ex;
		} catch (SecurityException ex) {
			throw ex;
		}
	}

	public final boolean isConnectable(final SelectionKey key) {
		return key.isConnectable();
	}

	public final boolean isConnected() {
		return this.transport.socketChannel().isConnected();
	}

	public final boolean finishConnect() throws IOException {
		try {
			if (this.transport.socketChannel().finishConnect()) {
				connected();
				return true;
			} else {
				return false;
			}
		} catch (ClosedByInterruptException ex) {
			throw ex;
		} catch (AsynchronousCloseException ex) {
			throw ex;
		} catch (ClosedChannelException ex) {
			throw ex;
		} catch (IOException ex) {
			throw ex;
		} catch (NoConnectionPendingException ex) {
			throw ex;
		}
	}

	private final void connected() throws IOException {
		final Socket socket = this.transport.socketChannel().socket();

		try {
			this.scheme.socketReady(socket);
		} catch (SocketException ex) {
			throw ex;
		}
	}

	public final void execute(final boolean alreadyRegistered, final EventHandler requestHandler,
			final SessionContext sessionContext, final EventHandler.Executor executor) throws IOException {
		if (alreadyRegistered) {
			execute(requestHandler, sessionContext, executor);
			return;
		}

		try {
			final Session session;
			if (this.scheme.getSecurityScheme() != null) {
				final TransportChannel sslTransport = createSSLTransportChannel(null);

				session = sessionContext.sessionCreator().createSession(sslTransport, SelectionKey.OP_READ, (executor != null));
			} else {
				session = sessionContext.sessionCreator().createSession(this.transport, SelectionKey.OP_READ, (executor != null));
			}

			if (executor != null) {
				final int status = session.getEventHandler().execute(executor, session);
				if (status <= 0) {
					requestHandler.close();
				}
			}
		} catch (IOException ex) {
			requestHandler.fail(ex);
			throw ex;
		} catch (RuntimeException ex) {
			requestHandler.fail(ex);
			throw ex;
		}
	}

	private final void execute(final EventHandler source,
			final SessionContext sessionContext, final EventHandler.Executor executor) throws IOException {
		final Session session;
		if (this.scheme.getSecurityScheme() != null) {
			final TransportChannel sslTransport = createSSLTransportChannel(source);

			session = sessionContext.sessionCreator().createSession(source, sslTransport, SelectionKey.OP_READ, (executor != null));
		} else {
			session = sessionContext.sessionCreator().createSession(source, this.transport, SelectionKey.OP_READ, (executor != null));
		}

		if (executor != null) {
			final int status = session.getEventHandler().execute(executor, session);
			if (status <= 0) {
				source.close();
			}
		}
	}

	private final TransportChannel createSSLTransportChannel(final EventHandler source) {
		try {
			return SSLChannelsFactory.createTransportChannel(this.scheme.getSecurityScheme(), this.transport, true);
		} catch (RuntimeException ex) {
			close();
			if (source != null) source.fail(ex);
			throw ex;
		}
	}

	public final void close() {
		this.transport.close();
	}


	private static final class Transport implements TransportChannel {

		private final SocketChannel socketChannel;


		Transport(final SocketChannel socketChannel) {
			this.socketChannel = socketChannel;
		}


		final SocketChannel socketChannel() {
			return this.socketChannel;
		}


		public final int open() throws IOException {
			// Nothing to do.
			return 0;
		}

		public final HandshakeHandler getHandshakeHandler() {
			return null;
		}

		public final SelectableChannel selectableChannel() {
			return this.socketChannel;
		}

		public final ReadableByteChannel readableChannel() {
			return this.socketChannel;
		}

		public final ScatteringByteChannel scatteringChannel() {
			return this.socketChannel;
		}

		public final WritableByteChannel writableChannel() {
			return this.socketChannel;
		}

		public final GatheringByteChannel gatheringChannel() {
			return this.socketChannel;
		}

		public final DatagramChannel datagramChannel() {
			return null;
		}

		public final SocketAddress remoteSocketAddress() {
			return this.socketChannel.socket().getRemoteSocketAddress();
		}

		public final SocketAddress localSocketAddress() {
			return this.socketChannel.socket().getLocalSocketAddress();
		}

		public final boolean isSupportedInput() {
			return true;
		}

		public final boolean isSupportedOutput() {
			return true;
		}

		public final boolean shutdownInput() {
			try {
				this.socketChannel.socket().shutdownInput();
			} catch (IOException ignore) {
			}
			return true;
		}

		public final boolean shutdownOutput() {
			try {
				this.socketChannel.socket().shutdownOutput();
			} catch (IOException ignore) {
			}
			return true;
		}

		public final void close() {
			try {
				this.socketChannel.socket().close();
			} catch (IOException ignore) {
			}

			try {
				this.socketChannel.close();
			} catch (IOException ignore) {
			}
		}

	}

}
