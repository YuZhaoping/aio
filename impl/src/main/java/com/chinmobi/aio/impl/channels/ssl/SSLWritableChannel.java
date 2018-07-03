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
import java.nio.ByteBuffer;
import java.nio.ReadOnlyBufferException;
import java.nio.channels.GatheringByteChannel;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSession;

import com.chinmobi.aio.impl.nio.HandshakeHandler;
import com.chinmobi.aio.impl.nio.TransportChannel;

/**
 * @author <a href="mailto:yuzhaoping1970@gmail.com">Zhaoping Yu</a>
 *
 */
public final class SSLWritableChannel implements GatheringByteChannel {

	private final SSLTransportChannel transport;

	private ByteBuffer encryptedData;
	private final ByteBuffer[] plainDatas;

	private int lastWrites;


	SSLWritableChannel(final SSLTransportChannel transport) {
		this.transport = transport;
		this.plainDatas = new ByteBuffer[1];
	}


	final void init() {
		synchronized (this.transport) {
			doInit();
		}
	}

	private final void doInit() {
		final SSLSession sslSession = sslEngine().getSession();

		this.encryptedData = ByteBuffer.allocate(sslSession.getPacketBufferSize());
		this.plainDatas[0] = ByteBuffer.allocate(sslSession.getApplicationBufferSize());
	}

	private final SSLEngine sslEngine() {
		return this.transport.sslEngine();
	}

	private final TransportChannel lowerChannel() {
		return this.transport.lowerChannel();
	}

	final boolean testAndSetShuttingDown() {
		synchronized (this.transport) {
			switch (this.transport.status()) {
			case SSLTransportChannel.STATUS_OPENED:
				this.transport.toShuttingDown();

			case SSLTransportChannel.STATUS_CLOSED:
			case SSLTransportChannel.STATUS_CLOSING:
				return false;

			//case SSLTransportChannel.STATUS_PENDING_SHUTDOWN:
			//case SSLTransportChannel.STATUS_SHUTTING_DOWN:
			//case SSLTransportChannel.STATUS_HANDSHAKED:
			default:
				this.transport.toShuttingDown();
				return true;
			}
		}
	}

	/* (non-Javadoc)
	 * @see java.io.Closeable#close()
	 */
	public final void close() throws IOException {
		this.transport.shutdownOutput();
	}

	/* (non-Javadoc)
	 * @see java.nio.channels.Channel#isOpen()
	 */
	public final boolean isOpen() {
		synchronized (this.transport) {
			return (this.transport.status() > SSLTransportChannel.STATUS_CLOSED);
		}
	}

	/*
	 * (non-Javadoc)
	 * @see java.nio.channels.WritableByteChannel#write(ByteBuffer src)
	 */
	public final int write(final ByteBuffer src) throws IOException {
		synchronized (this.transport) {
			try {
				return doWrite(src);
			} catch (SSLException ex) {
				this.transport.toShuttingDown();
				this.transport.handleUncaughtException(ex);
				return 0;
			} catch (IOException ex) {
				this.transport.onOutputException();
				throw ex;
			}
		}
	}

	private final int doWrite(final ByteBuffer src) throws SSLException, IOException {
		final int position = src.position();

		while (src.hasRemaining()) {
			transferPlainData(src);

			final SSLEngineResult result = wrap(src);

			if (result != null) {
				switch (result.getStatus()) {
				case CLOSED:
					this.transport.toShuttingDown();

				case BUFFER_OVERFLOW:
					break;

				//case BUFFER_UNDERFLOW:
				//case OK:
				default:
					if (this.lastWrites >= 0) {
						continue;
					}
				}
			}

			break;
		}

		return src.position() - position;
	}

	/*
	 * (non-Javadoc)
	 * @see java.nio.channels.GatheringByteChannel#write(ByteBuffer[] srcs, int offset, int length)
	 */
	public final long write(final ByteBuffer[] srcs, final int srcsOffset, final int srcsLength)
			throws IOException {
		long count = 0;

		for (int i = srcsOffset; i < srcsLength; ++i) {
			final int writes = write(srcs[i]);

			if (writes > 0) {
				count += writes;
			} else {
				break;
			}
		}

		return count;
	}

	/*
	 * (non-Javadoc)
	 * @see java.nio.channels.GatheringByteChannel#write(ByteBuffer[] srcs)
	 */
	public final long write(final ByteBuffer[] srcs) throws IOException {
		return write(srcs, 0, srcs.length);
	}

	private final int transferPlainData(final ByteBuffer src) {
		final ByteBuffer plainData = this.plainDatas[0];

		int count = plainData.remaining();
		if (count > 0) {
			if (count > src.remaining()) {
				count = src.remaining();
			}

			if (count > 0) {
				final int limit = src.limit();
				src.limit(count);

				plainData.put(src);

				src.limit(limit);
			}
		}
		return count;
	}

	final boolean hasAppDataRemaining() {
		final ByteBuffer plainData = this.plainDatas[0];
		return (plainData.position() > 0);
	}

	final void clearAppDataRemaining() {
		final ByteBuffer plainData = this.plainDatas[0];
		plainData.clear();
	}

	final boolean hasNetDataRemaining() {
		return (this.encryptedData.position() > 0);
	}

	final void clearNetDataRemaining() {
		this.encryptedData.clear();
	}

	final SSLEngineResult wrap(final ByteBuffer appData) throws SSLException, IOException {
		for (;;) {
			final SSLEngineResult result = doWrap();

			if (appData != null) {
				switch (result.getHandshakeStatus()) {
				case NEED_TASK:
				case NEED_WRAP: case NEED_UNWRAP:
					if (this.transport.doHandshake() != HandshakeHandler.HANDSHAKE_STATUS_FINISHED) {
						return null;
					}
					continue;

				//case FINISHED:
				//case NOT_HANDSHAKING:
				default:
					break;
				}
			}

			switch (result.getStatus()) {
			case BUFFER_OVERFLOW:
				if (writeEncryptedData() > 0) {
					if (this.encryptedData.hasRemaining()) {
						continue;
					}
				}

				if (expandEncryptedDataBuffer()) {
					continue;
				}
				break;

			case BUFFER_UNDERFLOW:
				expandPlainDataBuffer();

				if (appData != null) {
					if (transferPlainData(appData) > 0) {
						continue;
					}
				}
				break;

			case CLOSED:
				break;

			//case OK:
			default:
				writeEncryptedData();
				break;
			}

			return result;
		}
	}

	private final SSLEngineResult doWrap() throws SSLException {
		final ByteBuffer plainData = this.plainDatas[0];

		plainData.flip();

		try {
			return sslEngine().wrap(this.plainDatas, this.encryptedData);
		} catch (SSLException ex) {
			throw ex;
		} catch (ReadOnlyBufferException ex) {
			throw ex;
		} catch (IllegalArgumentException ex) {
			throw ex;
		} catch (IllegalStateException ex) {
			throw ex;
		} catch (RuntimeException ex) {
			Throwable cause = ex.getCause();
			if (cause == null) {
				cause = ex;
			}
			throw new SSLException(cause);
		} finally {
			plainData.compact();
		}
	}

	private final int writeEncryptedData() throws IOException {
		this.lastWrites = 0;
		this.encryptedData.flip();
		try {
			while (this.encryptedData.hasRemaining()) {
				this.lastWrites = lowerChannel().writableChannel().write(this.encryptedData);
				if (this.lastWrites <= 0) {
					this.lastWrites = -1;
					this.transport.setOutputEvent();
					break;
				}
			}
		} finally {
			this.encryptedData.compact();
		}
		return this.lastWrites;
	}

	final int getLastWrites() {
		return this.lastWrites;
	}

	final void flush() throws IOException {
		synchronized (this.transport) {
			try {
				doFlush();
			} catch (SSLException ex) {
				this.transport.toShuttingDown();
				this.transport.handleUncaughtException(ex);
			} catch (IOException ex) {
				this.transport.onOutputException();
				throw ex;
			}
		}
	}

	private final void doFlush() throws SSLException, IOException {
		if (writeEncryptedData() >= 0) {
			while (hasAppDataRemaining()) {
				final SSLEngineResult result = wrap(null);

				switch (result.getStatus()) {
				case BUFFER_OVERFLOW:
					return;

				case BUFFER_UNDERFLOW:
					return;

				case CLOSED:
					this.transport.toShuttingDown();
					return;

				//case OK:
				default:
					if (this.lastWrites < 0) {
						return;
					}
					break;
				}
			}
		}
	}

	private final boolean expandEncryptedDataBuffer() {
		final SSLSession sslSession = sslEngine().getSession();

		final int netSize = sslSession.getPacketBufferSize();

		final ByteBuffer oldBuffer = this.encryptedData;

		if (netSize > oldBuffer.capacity()) {
			final ByteBuffer newBuffer = ByteBuffer.allocate(oldBuffer.position() + netSize);

			oldBuffer.flip();
			newBuffer.put(oldBuffer);

			this.encryptedData = newBuffer;

			return true;
		}

		return false;
	}

	private final boolean expandPlainDataBuffer() {
		final SSLSession sslSession = sslEngine().getSession();

		final int appSize = sslSession.getApplicationBufferSize();

		final ByteBuffer oldBuffer = this.plainDatas[0];

		if (appSize > oldBuffer.capacity()) {
			final ByteBuffer newBuffer = ByteBuffer.allocate(appSize);

			oldBuffer.flip();
			newBuffer.put(oldBuffer);

			this.plainDatas[0] = newBuffer;

			return true;
		}
		return false;
	}

}
