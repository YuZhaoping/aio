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
package com.chinmobi.aio.impl.channels.ssl;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.DatagramChannel;
import java.nio.channels.GatheringByteChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.ScatteringByteChannel;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.WritableByteChannel;
import java.security.GeneralSecurityException;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLEngineResult.HandshakeStatus;
import javax.net.ssl.SSLException;

import com.chinmobi.aio.impl.nio.HandshakeContext;
import com.chinmobi.aio.impl.nio.HandshakeHandler;
import com.chinmobi.aio.impl.nio.TransportChannel;
import com.chinmobi.aio.scheme.AIOSecurityScheme;

/**
 * @author <a href="mailto:yuzhaoping1970@gmail.com">Zhaoping Yu</a>
 *
 */
public final class SSLTransportChannel implements TransportChannel, HandshakeHandler {

	static final int STATUS_PENDING_SHUTDOWN = -4;
	static final int STATUS_SHUTTING_DOWN = -2;
	static final int STATUS_CLOSING = -1;
	static final int STATUS_CLOSED = 0;
	static final int STATUS_OPENED = 1;
	static final int STATUS_HANDSHAKED = 2;

	private final AIOSecurityScheme scheme;

	private final TransportChannel lowerChannel;

	private final boolean clientMode;

	private SSLEngine sslEngine;

	private final SSLReadableChannel readableChannel;
	private final SSLWritableChannel writableChannel;

	private HandshakeContext context;

	private int status;


	public SSLTransportChannel(final AIOSecurityScheme scheme, final TransportChannel lowerChannel,
			final boolean clientMode) {
		this.scheme = scheme;
		this.lowerChannel = lowerChannel;
		this.clientMode = clientMode;

		this.readableChannel = new SSLReadableChannel(this);
		this.writableChannel = new SSLWritableChannel(this);

		this.status = STATUS_CLOSED;
	}


	final SSLEngine sslEngine() {
		return this.sslEngine;
	}

	final TransportChannel lowerChannel() {
		return this.lowerChannel;
	}

	final int status() {
		return this.status;
	}

	public final int open() throws IOException {
		try {
			return doOpen();
		} catch (SSLException ex) {
			close();
			throw ex;
		} catch (RuntimeException ex) {
			close();
			throw ex;
		}
	}

	private final synchronized int doOpen() throws SSLException,
			UnsupportedOperationException, IllegalStateException,
			IllegalArgumentException,
			IOException {

		if (this.status != STATUS_CLOSED) {
			return 0;
		}

		int interestOps = this.lowerChannel.open();

		final SSLContext sslContext;
		try {
			sslContext = this.scheme.getSSLContext();
		} catch (GeneralSecurityException ex) {
			throw new SSLException(ex);
		}

		final SSLEngine sslEngine;

		final SocketAddress peerAddress = this.lowerChannel.remoteSocketAddress();
		if (peerAddress != null && peerAddress instanceof InetSocketAddress) {
			final InetSocketAddress peer = (InetSocketAddress)peerAddress;
			sslEngine = sslContext.createSSLEngine(peer.getHostName(), peer.getPort());
		} else {
			sslEngine = sslContext.createSSLEngine();
		}

		this.sslEngine = sslEngine;

		sslEngine.setUseClientMode(this.clientMode);

		this.scheme.initalizeSSLEngine(sslEngine);

		this.readableChannel.init();
		this.writableChannel.init();

		sslEngine.beginHandshake();

		this.status = STATUS_OPENED;

		interestOps |= SelectionKey.OP_READ | SelectionKey.OP_WRITE;
		return interestOps;
	}

	public final boolean isSupportedInput() {
		return this.lowerChannel.isSupportedInput();
	}

	public final boolean isSupportedOutput() {
		return this.lowerChannel.isSupportedOutput();
	}

	public final boolean shutdownInput() {
		if (this.readableChannel.testAndSetShuttingDown()) {
			return false;
		}
		return this.lowerChannel.shutdownInput();
	}

	public final boolean shutdownOutput() {
		if (this.writableChannel.testAndSetShuttingDown()) {
			return false;
		}
		return this.lowerChannel.shutdownOutput();
	}

	public final HandshakeHandler getHandshakeHandler() {
		return this;
	}

	public final void setHandshakeContext(final HandshakeContext context) {
		this.context = context;
	}

	public final int handshake(final int readyOps) throws IOException {
		if ((readyOps & SelectionKey.OP_WRITE) != 0) {
			this.writableChannel.flush();
		} else
		if ((readyOps & SelectionKey.OP_READ) == 0) {
			return HANDSHAKE_STATUS_UNCOMPLETED;
		}

		if (this.status > STATUS_CLOSED) {
			return doHandshake();
		} else
		if (this.status < STATUS_CLOSED) {
			return shuttingDown();
		}
		return HANDSHAKE_STATUS_FINISHED;
	}

	public final SelectableChannel selectableChannel() {
		return this.lowerChannel.selectableChannel();
	}

	public final ReadableByteChannel readableChannel() {
		return this.readableChannel;
	}

	public final ScatteringByteChannel scatteringChannel() {
		return this.readableChannel;
	}

	public final WritableByteChannel writableChannel() {
		return this.writableChannel;
	}

	public final GatheringByteChannel gatheringChannel() {
		return this.writableChannel;
	}

	public final DatagramChannel datagramChannel() {
		return this.lowerChannel.datagramChannel();
	}

	public final SocketAddress remoteSocketAddress() {
		return this.lowerChannel.remoteSocketAddress();
	}

	public final SocketAddress localSocketAddress() {
		return this.lowerChannel.localSocketAddress();
	}

	public final synchronized void close() {

		this.status = STATUS_CLOSED;

		closeSSLEngine();

		this.lowerChannel.close();
	}

	private final void closeSSLEngine() {
		final SSLEngine sslEngine = this.sslEngine;
		if (sslEngine != null) {
			this.sslEngine = null;

			try {
				this.scheme.closedSSLEngine(sslEngine);
			} catch (Throwable ignore) {
				handleUncaughtException(ignore);
			}
		}
	}

	final void toShuttingDown() {
		if (this.status != STATUS_CLOSED) {
			switch (this.status) {
			case STATUS_OPENED:
				this.status = STATUS_CLOSING;
				setInputOutputEvent();

			case STATUS_CLOSING:
			case STATUS_PENDING_SHUTDOWN:
			case STATUS_SHUTTING_DOWN:
				return;

			//case STATUS_HANDSHAKED:
			default:
				this.status = STATUS_PENDING_SHUTDOWN;
				setInputOutputEvent();
			}

			final SSLEngine sslEngine = this.sslEngine;
			if (sslEngine != null) {
				try {
					this.scheme.shuttingDownSSLEngine(sslEngine);
				} catch (Throwable ignore) {
					handleUncaughtException(ignore);
				}
			}
		}
	}

	private final boolean pendingShutDown() {
		final SSLEngine sslEngine = this.sslEngine;
		if ((sslEngine != null) && (this.status == STATUS_PENDING_SHUTDOWN)) {
			while (this.writableChannel.hasAppDataRemaining()) {
				try {
					final SSLEngineResult result = this.writableChannel.wrap(null);
					switch (result.getStatus()) {
					case BUFFER_OVERFLOW:
						return true;

					case BUFFER_UNDERFLOW:

					case CLOSED:
						this.writableChannel.clearAppDataRemaining();
						break;

					//case OK:
					default:
						if (this.writableChannel.getLastWrites() < 0) {
							return true;
						}
						break;
					}
				} catch (SSLException ex) {
					this.writableChannel.clearAppDataRemaining();
					handleUncaughtException(ex);
				} catch (IOException ex) {
					onOutputException();
					handleUncaughtException(ex);
					return false;
				}
			}

			this.status = STATUS_SHUTTING_DOWN;

			sslEngine.closeOutbound();
		}
		return false;
	}

	private final synchronized int shuttingDown() /*throws SSLException, IOException*/ {
		if (pendingShutDown()) {
			return HANDSHAKE_STATUS_UNCOMPLETED;
		}

		final SSLEngine sslEngine = (this.status != STATUS_CLOSING) ? this.sslEngine : null;

		SSLEngineResult result = null;
		int shuttingDownStatus = (sslEngine != null) ? 0 : 0x03;

		boolean toContinue = (sslEngine != null);
		while (toContinue) {
			toContinue = false;

			if (!sslEngine.isOutboundDone()) {
				try {
					result = this.writableChannel.wrap(null);

					switch (result.getStatus()) {
					case BUFFER_OVERFLOW:
						break;

					case BUFFER_UNDERFLOW:
						break;

					case CLOSED:
						shuttingDownStatus |= 0x01;
						break;

					//case OK:
					default:
						if (this.writableChannel.getLastWrites() >= 0) {
							toContinue = true;
						}
						break;
					}
				} catch (SSLException ex) {
					shuttingDownStatus |= 0x03;

					handleUncaughtException(ex);
				} catch (IOException ex) {
					shuttingDownStatus |= 0x03;
					this.writableChannel.clearNetDataRemaining();

					handleUncaughtException(ex);
				}
			} else {
				shuttingDownStatus |= 0x01;
			}

			if (!sslEngine.isInboundDone()) {
				try {
					if (this.readableChannel.readEncryptedData() < 0) {
						closeInbound();
						shuttingDownStatus |= 0x02;
						continue;
					}

					result = this.readableChannel.unwrap(null);

					switch (result.getStatus()) {
					case BUFFER_OVERFLOW:
						break;

					case BUFFER_UNDERFLOW:
						if (this.readableChannel.getLastReads() < 0) {
							closeInbound();
							shuttingDownStatus |= 0x02;
						}
						break;

					case CLOSED:
						shuttingDownStatus |= 0x02;
						break;

					//case OK:
					default:
						break;
					}
				} catch (SSLException ex) {
					shuttingDownStatus |= 0x02;

					handleUncaughtException(ex);
				} catch (IOException ex) {
					closeInbound();
					shuttingDownStatus |= 0x02;

					handleUncaughtException(ex);
				}

			} else {
				shuttingDownStatus |= 0x02;
			}
		}

		if ((sslEngine != null) &&
			this.writableChannel.hasNetDataRemaining()) {
			setOutputEvent();
			return HANDSHAKE_STATUS_UNCOMPLETED;
		}

		if (shuttingDownStatus == 0x03) {

			closeSSLEngine();

			return HANDSHAKE_STATUS_CLOSED;
		} else {
			if (!sslEngine.isOutboundDone()) {
				setOutputEvent();
			}
			return HANDSHAKE_STATUS_UNCOMPLETED;
		}
	}

	final void closeInbound() {
		final SSLEngine sslEngine = this.sslEngine;
		if (sslEngine != null) {
			try {
				sslEngine.closeInbound();
			} catch (SSLException ignore) {
				handleUncaughtException(ignore);
			}
		}
	}

	final void onOutputException() {
		if (this.status == STATUS_HANDSHAKED) {
			final SSLEngine sslEngine = this.sslEngine;
			if (sslEngine != null) {
				try {
					sslEngine.closeOutbound();
				} catch (Throwable ignore) {
					handleUncaughtException(ignore);
				}
			}
		}

		toShuttingDown();

		this.status = STATUS_CLOSING;

		this.writableChannel.clearAppDataRemaining();
		this.writableChannel.clearNetDataRemaining();
	}

	final synchronized int doHandshake() throws SSLException, IOException {
		final SSLEngine sslEngine = this.sslEngine;

		SSLEngineResult result = null;
		int handshakeStatus = HANDSHAKE_STATUS_UNCOMPLETED;

		while (handshakeStatus > 0) {
			switch (sslEngine.getHandshakeStatus()) {
			case NEED_TASK:
				try {
					doRunTask(sslEngine);
				} catch (SSLException ex) {
					toShuttingDown();
					handleUncaughtException(ex);
					return HANDSHAKE_STATUS_UNCOMPLETED;
				}
				break;

			case NEED_WRAP:
				try {
					result = this.writableChannel.wrap(null);

					switch (result.getStatus()) {
					case BUFFER_OVERFLOW:
						return handshakeStatus;

					case BUFFER_UNDERFLOW:
						//break;
						return handshakeStatus;

					case CLOSED:
						toShuttingDown();
						return HANDSHAKE_STATUS_UNCOMPLETED;

					//case OK:
					default:
						if (this.writableChannel.getLastWrites() < 0) {
							return HANDSHAKE_STATUS_UNCOMPLETED;
						}
					}
				/*} catch (SSLException ex) {
					toShuttingDown();
					handleUncaughtException(ex);
					return HANDSHAKE_STATUS_UNCOMPLETED;*/
				} catch (IOException ex) {
					onOutputException();
					throw ex;
					//handleUncaughtException(ex);
					//return HANDSHAKE_STATUS_CLOSED;
				}
				break;

			case NEED_UNWRAP:
				try {
					if (this.readableChannel.readEncryptedData() < 0) {
						toShuttingDown();
						return HANDSHAKE_STATUS_UNCOMPLETED;
					}

					result = this.readableChannel.unwrap(null);

					switch (result.getStatus()) {
					case BUFFER_OVERFLOW:
						//break;
						return handshakeStatus;

					case BUFFER_UNDERFLOW:
						if (this.readableChannel.getLastReads() < 0) {
							toShuttingDown();
						}
						return HANDSHAKE_STATUS_UNCOMPLETED;

					case CLOSED:
						toShuttingDown();
						return HANDSHAKE_STATUS_UNCOMPLETED;

					//case OK:
					default:
					}
				/*} catch (SSLException ex) {
					toShuttingDown();
					handleUncaughtException(ex);
					return HANDSHAKE_STATUS_UNCOMPLETED;*/
				} catch (IOException ex) {
					toShuttingDown();

					throw ex;
					//handleUncaughtException(ex);
					//return HANDSHAKE_STATUS_UNCOMPLETED;
				}
				break;

			case FINISHED:
				break;

			//case NOT_HANDSHAKING:
			default:
				handshakeStatus = HANDSHAKE_STATUS_FINISHED;
				break;
			}
		}

		if (result != null &&
			result.getHandshakeStatus() == HandshakeStatus.FINISHED) {
			this.status = STATUS_HANDSHAKED;

			try {
				this.scheme.verifySSLSessionHandshaked(sslEngine.getSession());
			} catch (SSLException ex) {
				toShuttingDown();
				handleUncaughtException(ex);
				return HANDSHAKE_STATUS_UNCOMPLETED;
			}
		}

		return handshakeStatus;
	}

	private static final void doRunTask(final SSLEngine sslEngine) throws SSLException {
		Runnable task = sslEngine.getDelegatedTask();
		while (task != null) {
			try {
				task.run();
			} catch (RuntimeException ex) {
				Throwable cause = ex.getCause();
				if (cause == null) {
					cause = ex;
				}
				throw new SSLException(cause);
			}

			task = sslEngine.getDelegatedTask();
		}
	}

	private final void setInputOutputEvent() {
		final HandshakeContext context = this.context;
		if (context != null) {
			context.setInputEvent();
			context.setOutputEvent();
		}
	}

	final void setOutputEvent() {
		final HandshakeContext context = this.context;
		if (context != null) {
			context.setOutputEvent();
		}
	}

	final void handleUncaughtException(final Throwable ex) {
		final HandshakeContext context = this.context;
		if (context != null) {
			context.handleUncaughtException(ex);
		}
	}

	@Override
	public final String toString() {
		final StringBuilder builder = new StringBuilder();

		builder.append("SSLTransport[Scheme: ");
		builder.append(this.scheme.toString());
		builder.append(']');

		return builder.toString();
	}

}
