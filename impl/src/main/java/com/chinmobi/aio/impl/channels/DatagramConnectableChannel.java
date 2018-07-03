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
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.ClosedByInterruptException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.DatagramChannel;
import java.nio.channels.GatheringByteChannel;
import java.nio.channels.IllegalBlockingModeException;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.ScatteringByteChannel;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.WritableByteChannel;

import com.chinmobi.aio.AIOInetEndpoint;
import com.chinmobi.aio.impl.nio.ConnectableChannel;
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
public final class DatagramConnectableChannel implements ConnectableChannel {

	private final AIODatagramScheme scheme;

	private final Transport transport;


	DatagramConnectableChannel(final AIODatagramScheme scheme) throws IOException {
		this.scheme = scheme;

		this.transport = new Transport(DatagramChannel.open());
		try {
			this.transport.datagramChannel().configureBlocking(false);
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
		final DatagramSocket socket = this.transport.datagramChannel().socket();

		try {
			this.scheme.prepareBind(socket);
		} catch (SocketException ex) {
			throw ex;
		}

		try {
			socket.bind(localAddr.getSocketAddress());
		} catch (SocketException ex) {
			throw ex;
		} catch (SecurityException ex) {
			throw ex;
		} catch (IllegalArgumentException ex) {
			throw ex;
		}
	}

	public final SelectableChannel selectableChannel() {
		return this.transport.selectableChannel();
	}

	public final int connectOps() {
		return SelectionKey.OP_READ;
	}

	public final boolean connect(final AIOInetEndpoint remoteAddr) throws IOException {
		try {
			this.transport.datagramChannel().connect(remoteAddr.getSocketAddress());
		} catch (ClosedByInterruptException ex) {
			throw ex;
		} catch (AsynchronousCloseException ex) {
			throw ex;
		} catch (ClosedChannelException ex) {
			throw ex;
		} catch (IOException ex) {
			throw ex;
		} catch (SecurityException ex) {
			throw ex;
		}
		connected();
		return true;
	}

	public final boolean isConnectable(final SelectionKey key) {
		return key.isReadable();
	}

	public final boolean isConnected() {
		return this.transport.datagramChannel().isConnected();
	}

	public final boolean finishConnect() throws IOException {
		return true;
	}

	private final void connected() throws IOException {
		final DatagramSocket socket = this.transport.datagramChannel().socket();

		try {
			this.scheme.socketReady(socket);
		} catch (SocketException ex) {
			throw ex;
		}
	}

	public final void execute(final boolean alreadyRegistered, final EventHandler requestHandler,
			final SessionContext sessionContext, final EventHandler.Executor executor) throws IOException {
		try {
			final Session session = sessionContext.sessionCreator().createSession(this.transport, SelectionKey.OP_READ, (executor != null));
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

	public final void close() {
		this.transport.close();
	}


	private static final class Transport implements TransportChannel {

		private final DatagramChannel datagramChannel;


		Transport(final DatagramChannel datagramChannel) {
			this.datagramChannel = datagramChannel;
		}


		public final int open() throws IOException {
			// Nothing to do.
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

		public final void close() {
			this.datagramChannel.socket().close();

			try {
				this.datagramChannel.close();
			} catch (IOException ignore) {
			}
		}

	}

}
