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
import java.net.ServerSocket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.ClosedByInterruptException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.DatagramChannel;
import java.nio.channels.GatheringByteChannel;
import java.nio.channels.IllegalBlockingModeException;
import java.nio.channels.NotYetBoundException;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.ScatteringByteChannel;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.WritableByteChannel;

import com.chinmobi.aio.AIOInetEndpoint;
import com.chinmobi.aio.impl.channels.ssl.SSLChannelsFactory;
import com.chinmobi.aio.impl.nio.AcceptableChannel;
import com.chinmobi.aio.impl.nio.AcceptedTransport;
import com.chinmobi.aio.impl.nio.AcceptorHandler;
import com.chinmobi.aio.impl.nio.EventHandler;
import com.chinmobi.aio.impl.nio.HandshakeHandler;
import com.chinmobi.aio.impl.nio.SessionContext;
import com.chinmobi.aio.impl.nio.TransportChannel;
import com.chinmobi.aio.scheme.AIOSecurityScheme;
import com.chinmobi.aio.scheme.AIOSocketScheme;

/**
 * @author <a href="mailto:yuzhaoping1970@gmail.com">Zhaoping Yu</a>
 *
 */
public final class SocketAcceptableChannel implements AcceptableChannel {

	private final AIOSocketScheme scheme;

	private final ServerSocketChannel serverChannel;


	SocketAcceptableChannel(final AIOSocketScheme scheme) throws IOException {
		this.scheme = scheme;

		this.serverChannel = ServerSocketChannel.open();

		try {
			this.serverChannel.configureBlocking(false);
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
		final ServerSocket serverSocket = this.serverChannel.socket();

		try {
			this.scheme.prepareBind(serverSocket);
		} catch (SocketException ex) {
			throw ex;
		}

		try {
			serverSocket.bind(addr.getSocketAddress(), this.scheme.getServerBacklog());
		} catch (IOException ex) {
			throw ex;
		} catch (SecurityException ex) {
			throw ex;
		} catch (IllegalArgumentException ex) {
			throw ex;
		}

		try {
			this.scheme.socketReady(serverSocket);
		} catch (SocketException ex) {
			throw ex;
		}

		return false;
	}

	public final SelectableChannel selectableChannel() {
		return this.serverChannel;
	}

	public final int acceptOps() {
		return SelectionKey.OP_ACCEPT;
	}

	public final boolean isAcceptable(final SelectionKey key) {
		try {
			return key.isAcceptable();
		} catch (CancelledKeyException ex) {
			throw ex;
		}
	}

	public final AcceptedTransport accept(final AcceptorHandler acceptorHandler) throws IOException {

		final SocketChannel socketChannel;

		try {
			socketChannel = this.serverChannel.accept();
		} catch (ClosedByInterruptException ex) {
			throw ex;
		} catch (AsynchronousCloseException ex) {
			throw ex;
		} catch (ClosedChannelException ex) {
			throw ex;
		} catch (IOException ex) {
			throw ex;
		} catch (NotYetBoundException ex) {
			throw ex;
		} catch (SecurityException ex) {
			throw ex;
		}

		if (socketChannel == null) {
			return null;
		}

		try {
			this.scheme.socketReady(socketChannel.socket());
		} catch (SocketException ex) {
			throw ex;
		}

		return new Transport(socketChannel, this.scheme.getSecurityScheme());
	}

	public final void close() {
		try {
			this.serverChannel.socket().close();
		} catch (IOException ignore) {
		}

		try {
			this.serverChannel.close();
		} catch (IOException ignore) {
		}
	}


	private static final class Transport implements AcceptedTransport, TransportChannel {

		private final SocketChannel socketChannel;
		private final AIOSecurityScheme securityScheme;


		Transport(final SocketChannel socketChannel, final AIOSecurityScheme securityScheme) {
			this.socketChannel = socketChannel;
			this.securityScheme = securityScheme;
		}


		public final void execute(final AcceptorHandler acceptorHandler,
				SessionContext sessionContext, final EventHandler.Executor executor)
				throws IOException {
			sessionContext = sessionContext.dispatch();
			if (this.securityScheme != null) {
				final TransportChannel sslTransport = createSSLTransportChannel(acceptorHandler);

				sessionContext.sessionCreator().createSession(sslTransport, SelectionKey.OP_READ, executor);
			} else {
				sessionContext.sessionCreator().createSession(this, SelectionKey.OP_READ, executor);
			}
		}

		private final TransportChannel createSSLTransportChannel(final AcceptorHandler acceptorHandler) {
			try {
				return SSLChannelsFactory.createTransportChannel(this.securityScheme, this, false);
			} catch (RuntimeException ex) {
				close();
				acceptorHandler.fail(ex);
				throw ex;
			}
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
