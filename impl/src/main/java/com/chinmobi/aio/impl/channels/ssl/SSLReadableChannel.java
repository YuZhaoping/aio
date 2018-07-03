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
import java.nio.channels.ScatteringByteChannel;

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
public final class SSLReadableChannel implements ScatteringByteChannel {

	private final SSLTransportChannel transport;

	private ByteBuffer encryptedData;
	private final ByteBuffer[] plainDatas;

	private int lastReads;


	SSLReadableChannel(final SSLTransportChannel transport) {
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
			//case SSLTransportChannel.STATUS_OPENED:
				//this.transport.toShuttingDown();

			case SSLTransportChannel.STATUS_CLOSED:
			case SSLTransportChannel.STATUS_CLOSING:
				return false;

			//case SSLTransportChannel.STATUS_PENDING_SHUTDOWN:
			//case SSLTransportChannel.STATUS_SHUTTING_DOWN:
			//case SSLTransportChannel.STATUS_HANDSHAKED:
			default:
				return true;
			}
		}
	}

	/* (non-Javadoc)
	 * @see java.io.Closeable#close()
	 */
	public final void close() throws IOException {
		this.transport.shutdownInput();
	}

	/* (non-Javadoc)
	 * @see java.nio.channels.Channel#isOpen()
	 */
	public final boolean isOpen() {
		synchronized (this.transport) {
			return (this.transport.status() > SSLTransportChannel.STATUS_CLOSED);
		}
	}

	/* (non-Javadoc)
	 * @see java.nio.channels.ReadableByteChannel#read(ByteBuffer dst)
	 */
	public final int read(final ByteBuffer dst) throws IOException {
		synchronized (this.transport) {
			try {
				return doRead(dst);
			} catch (SSLException ex) {
				this.transport.toShuttingDown();
				this.transport.handleUncaughtException(ex);
				return 0;
			} catch (IOException ex) {
				this.transport.closeInbound();
				this.transport.toShuttingDown();
				throw ex;
			}
		}
	}

	private final int doRead(final ByteBuffer dst) throws SSLException, IOException {
		final int position = dst.position();

		this.lastReads = 0;

		while (dst.hasRemaining()) {
			if (transferPlainData(dst) > 0) {
				continue;
			}

			if (readEncryptedData() >= 0) {

				final SSLEngineResult result = unwrap(dst);

				if (result != null) {
					switch (result.getStatus()) {
					case BUFFER_OVERFLOW:
						break;

					case BUFFER_UNDERFLOW:
						if (this.lastReads < 0) {
							this.transport.closeInbound();
							this.transport.toShuttingDown();
						}
						break;

					case CLOSED:
						this.transport.toShuttingDown();
						break;

					//case OK:
					default:
						continue;
					}
				}

			} else {// this.lastReads < 0
				this.transport.closeInbound();
				this.transport.toShuttingDown();
			}

			break;
		}

		final int count = dst.position() - position;

		return (count > 0) ? count : ((this.lastReads < 0) ? -1 : 0);
	}

	/* (non-Javadoc)
	 * @see java.nio.channels.ScatteringByteChannel#read(ByteBuffer[] dsts, int offset, int length)
	 */
	public final long read(final ByteBuffer[] dsts, final int dstsOffset, final int dstsLength) throws IOException {
		long count = 0;

		for (int i = dstsOffset; i < dstsLength; ++i) {
			final int reads = read(dsts[i]);

			if (reads > 0) {
				count += reads;
			} else
			if (reads < 0) {
				if (count == 0) {
					count = -1;
				}
				break;
			} else {
				break;
			}
		}

		return count;
	}

	/* (non-Javadoc)
	 * @see java.nio.channels.ScatteringByteChannel#read(ByteBuffer[] dsts)
	 */
	public final long read(final ByteBuffer[] dsts) throws IOException {
		return read(dsts, 0, dsts.length);
	}

	private final int transferPlainData(final ByteBuffer dst) {
		final ByteBuffer plainData = this.plainDatas[0];

		int count = plainData.position();
		if (count > 0) {
			if (count > dst.remaining()) {
				count = dst.remaining();
			}

			if (count > 0) {
				plainData.flip();

				final int limit = plainData.limit();
				plainData.limit(count);

				dst.put(plainData);

				plainData.limit(limit);

				plainData.compact();
			}
		}
		return count;
	}

	final SSLEngineResult unwrap(final ByteBuffer appData) throws SSLException, IOException {
		for (;;) {
			final SSLEngineResult result = doUnwrap();

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
				if (appData != null) {
					if (transferPlainData(appData) > 0) {
						continue;
					}
				}

				if (expandPlainDataBuffer()) {
					continue;
				}
				break;

			case CLOSED:
				break;

			case BUFFER_UNDERFLOW:
				expandEncryptedDataBuffer();

				if (readEncryptedData() > 0) {
					continue;
				}

			//case OK:
			default:
				break;
			}

			return result;
		}
	}

	private final SSLEngineResult doUnwrap() throws SSLException {
		this.encryptedData.flip();

		try {
			return sslEngine().unwrap(this.encryptedData, this.plainDatas);
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
			this.encryptedData.compact();
		}
	}

	final int readEncryptedData() throws IOException {
		this.lastReads = lowerChannel().readableChannel().read(this.encryptedData);
		return this.lastReads;
	}

	final int getLastReads() {
		return this.lastReads;
	}

	private final boolean expandPlainDataBuffer() {
		final SSLSession sslSession = sslEngine().getSession();

		final int appSize = sslSession.getApplicationBufferSize();

		final ByteBuffer oldBuffer = this.plainDatas[0];

		if (appSize > oldBuffer.capacity()) {
			final ByteBuffer newBuffer = ByteBuffer.allocate(oldBuffer.position() + appSize);

			oldBuffer.flip();
			newBuffer.put(oldBuffer);

			this.plainDatas[0] = newBuffer;

			return true;
		}
		return false;
	}

	private final boolean expandEncryptedDataBuffer() {
		final SSLSession sslSession = sslEngine().getSession();

		final int netSize = sslSession.getPacketBufferSize();

		final ByteBuffer oldBuffer = this.encryptedData;

		if (netSize > oldBuffer.capacity()) {
			final ByteBuffer newBuffer = ByteBuffer.allocate(netSize);

			oldBuffer.flip();
			newBuffer.put(oldBuffer);

			this.encryptedData = newBuffer;

			return true;
		}
		return false;
	}

}
