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
package com.chinmobi.aio.impl.act;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.ScatteringByteChannel;

import com.chinmobi.aio.act.AIOInputLegacy;
import com.chinmobi.aio.act.AIOInputLegacyRuntimeException;
import com.chinmobi.aio.impl.nio.ExceptionHandler;

/**
 * @author <a href="mailto:yuzhaoping1970@gmail.com">Zhaoping Yu</a>
 *
 */
public final class LegacyChannel implements ScatteringByteChannel {

	LegacyChannel next;

	private final LegacyChannelFactory factory;

	private ExceptionHandler exceptionHandler;

	private AIOInputLegacy legacy;

	private long offset;
	private long length;

	private boolean endOfInput;


	LegacyChannel(final LegacyChannelFactory factory) {
		this.factory = factory;
	}


	final void set(final AIOInputLegacy legacy, final boolean endOfInput)
			throws IllegalArgumentException {

		if (legacy == null) {
			throw new IllegalArgumentException("Null legacy.");
		}

		this.legacy = legacy;

		final ByteBuffer byteBuffer = legacy.byteBuffer();

		long position = legacy.position();
		long count = legacy.count();

		if (position < 0) {
			position = 0;
		}

		if (byteBuffer != null) {
			if (count <= 0) {
				count = byteBuffer.remaining();
			}

			try {
				byteBuffer.position(0).limit((int)(position + count)).position((int)position);
			} catch (IllegalArgumentException ex) {
				throw ex;
			}
		} else {
			final FileChannel fileChannel = legacy.fileChannel();

			if (fileChannel == null) {
				throw new IllegalArgumentException("Null legacy byteBuffer and fileChannel.");
			}

			try {
				final long fileSize = fileChannel.size();

				if (position > fileSize) {
					position = fileSize;
				}

				if (count < 0 || (position + count) > fileSize) {
					count = fileSize - position;
				}
			} catch (IOException ex) {
				throw new IllegalArgumentException(ex.getMessage(), ex);
			}
		}

		this.offset = position;
		this.length = count;
		this.endOfInput = endOfInput;
	}

	final void reset() {
		this.legacy = null;
		this.exceptionHandler = null;
	}

	final void release() {
		if (this.legacy != null) {
			try {
				this.legacy.release();
			} catch (Throwable ex) {
				final ExceptionHandler handler = this.exceptionHandler;
				if (handler != null) {
					handler.handleUncaughtException(ex);
				}
			}

			reset();
		}

		this.factory.releaseLegacyChannel(this);
	}

	public final ExceptionHandler exceptionHandler() {
		return this.exceptionHandler;
	}

	public final void setExceptionHandler(final ExceptionHandler exceptionHandler) {
		this.exceptionHandler = exceptionHandler;
	}

	public final AIOInputLegacy legacy() {
		return this.legacy;
	}

	public final boolean endOfInput() {
		return (this.endOfInput && (this.length <= 0));
	}

	public final long remaining() {
		return this.length;
	}

	/* (non-Javadoc)
	 * @see java.io.Closeable#close()
	 */
	public final void close() throws IOException {
		// Nothing to do.
	}

	/* (non-Javadoc)
	 * @see java.nio.channels.Channel#isOpen()
	 */
	public final boolean isOpen() {
		return true;
	}

	/* (non-Javadoc)
	 * @see java.nio.channels.ReadableByteChannel#read(ByteBuffer dst)
	 */
	public final int read(final ByteBuffer dst) throws IOException {
		final ByteBuffer byteBuffer = this.legacy.byteBuffer();
		if (byteBuffer != null) {
			return (int)readFromBuffer(byteBuffer, dst);
		} else {
			final FileChannel fileChannel = this.legacy.fileChannel();

			if (fileChannel != null) {
				return (int)readFromFile(fileChannel, dst);
			} else {
				this.length = 0;
				return (this.endOfInput) ? -1 : 0;
			}
		}
	}

	/* (non-Javadoc)
	 * @see java.nio.channels.ScatteringByteChannel#read(ByteBuffer[] dsts, int offset, int length)
	 */
	public final long read(final ByteBuffer[] dsts, final int dstsOffset, final int dstsLength) throws IOException {
		long count = 0;

		final ByteBuffer byteBuffer = this.legacy.byteBuffer();
		if (byteBuffer != null) {
			for (int i = dstsOffset; i < dstsLength; ++i) {
				final long reads = readFromBuffer(byteBuffer, dsts[i]);

				if (reads > 0) {
					count += reads;
				} else
				if (reads < 0) {
					return -1;
				} else if (this.length <= 0) {
					break;
				}
			}
		} else {
			final FileChannel fileChannel = this.legacy.fileChannel();

			if (fileChannel != null) {
				for (int i = dstsOffset; i < dstsLength; ++i) {
					final long reads = readFromFile(fileChannel, dsts[i]);

					if (reads > 0) {
						count += reads;
					} else
					if (reads < 0) {
						return -1;
					} else if (this.length <= 0) {
						break;
					}

					if (dsts[i].hasRemaining()) {
						--i;
					}
				}
			} else {
				this.length = 0;
				return (this.endOfInput) ? -1 : 0;
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

	private final long readFromBuffer(final ByteBuffer byteBuffer, final ByteBuffer dst) {
		if (this.length > 0) {
			long count = dst.remaining();
			if (count > 0) {
				if (count > this.length) {
					count = this.length;
				}

				byteBuffer.position(0).limit((int)(this.offset + count)).position((int)this.offset);

				dst.put(byteBuffer);

				this.offset += count;
				this.length -= count;

				byteBuffer.limit((int)(this.offset + this.length));

				return count;
			} else {
				return 0;
			}
		}
		return (this.endOfInput) ? -1 : 0;
	}

	private final long readFromFile(final FileChannel fileChannel, final ByteBuffer dst)
			throws AIOInputLegacyRuntimeException {
		if (this.length > 0) {
			long count = dst.remaining();
			if (count > 0) {
				int markLimit = -1;

				if (count > this.length) {
					count = this.length;

					markLimit = dst.limit();

					dst.limit((int)(dst.position() + count));
				}

				try {
					count = fileChannel.read(dst, this.offset);
				} catch (Exception ex) {
					// ClosedChannelException,
					// AsynchronousCloseException, ClosedByInterruptException
					// IOException

					// NonReadableChannelException, IllegalArgumentException

					this.length = 0;

					throw new AIOInputLegacyRuntimeException(ex);
				} finally {
					if (markLimit >= 0) {
						dst.limit(markLimit);
					}
				}

				if (count >= 0) {
					this.offset += count;
					this.length -= count;

					return count;
				} else {
					this.length = 0;
				}
			} else {
				return 0;
			}
		}
		return (this.endOfInput) ? -1 : 0;
	}

}
