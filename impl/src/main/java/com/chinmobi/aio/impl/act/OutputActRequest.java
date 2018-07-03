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
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.DatagramChannel;
import java.nio.channels.FileChannel;
import java.nio.channels.NonWritableChannelException;
import java.nio.channels.WritableByteChannel;

import com.chinmobi.aio.AIOFutureCallback;
import com.chinmobi.aio.act.AIOActEntryRuntimeException;
import com.chinmobi.aio.act.AIODatagramActEntry;
import com.chinmobi.aio.act.AIODatagramReadableActEntry;
import com.chinmobi.aio.act.AIOOutputActResult;
import com.chinmobi.aio.act.AIOReadableActEntry;
import com.chinmobi.aio.impl.nio.Session;
import com.chinmobi.aio.impl.nio.TransportChannel;

/**
 * @author <a href="mailto:yuzhaoping1970@gmail.com">Zhaoping Yu</a>
 *
 */
public final class OutputActRequest extends BaseActRequest<AIOOutputActResult>
	implements AIOOutputActResult {

	private final ActRequestFactory requestFactory;


	OutputActRequest(final ActRequestFactory requestFactory) {
		super(requestFactory.sessionContext());
		this.requestFactory = requestFactory;
	}


	final void set(final AIOReadableActEntry source, final AIOFutureCallback<AIOOutputActResult> callback,
			final long timeout, final Object attachment) throws IllegalArgumentException {

		long position = source.position();
		long count = source.count();

		if (position < 0) {
			position = 0;
		}

		final ByteBuffer byteBuffer = source.byteBuffer();
		if (byteBuffer != null) {
			if (count < 0) {
				count = byteBuffer.remaining();
			}

			try {
				byteBuffer.position(0).limit((int)(position + count)).position((int)position);
			} catch (IllegalArgumentException ex) {
				throw ex;
			}
		} else {
			final FileChannel fileChannel = source.fileChannel();

			if (fileChannel != null) {
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
		}

		super.set(source, position, count, timeout, attachment);

		this.future.set(this.futureReleaseCallback(), this.futureCancellable(),
				callback, attachment, (AIOOutputActResult)this);
	}

	@Override
	final AIOOutputActResult outputResult() {
		return this;
	}

	/* (non-Javadoc)
	 * @see com.chinmobi.aio.AIOOutputActResult#entry()
	 */
	public final AIOReadableActEntry entry() {
		return (AIOReadableActEntry)this.entry;
	}


	@Override
	final int outputReady(final Session session, final TransportChannel transportChannel) throws IOException {
		final WritableByteChannel outputChannel = transportChannel.writableChannel();
		if (outputChannel != null) {
			return outputReady(session, outputChannel);
		} else {
			final DatagramChannel datagramChannel = transportChannel.datagramChannel();
			if (datagramChannel != null) {
				return datagramReady(session, datagramChannel);
			}
		}
		return STATUS_NULL_CHANNEL;
	}

	private final int datagramReady(final Session session, final DatagramChannel datagramChannel) throws IOException {
		synchronized (this.future.lock()) {
			if (!this.future.isDone()) {
				final ByteBuffer buffer = this.entry.byteBuffer();
				if (buffer != null) {
					if (this.entry instanceof AIODatagramActEntry) {
						final AIODatagramReadableActEntry entry = (AIODatagramReadableActEntry)this.entry;
						final SocketAddress remote = entry.getRemoteSocketAddress();
						if (remote != null) {
							return sendFromBuffer(buffer, datagramChannel, remote);
						}
					}
				}
				return STATUS_NO_MATCHED_ENTRY;
			}
		}
		return STATUS_DO_NOTHING;
	}

	@Override
	final int outputReady(final Session session, final WritableByteChannel outputChannel) throws IOException {
		synchronized (this.future.lock()) {
			if (!this.future.isDone()) {
				final ByteBuffer buffer = this.entry.byteBuffer();
				if (buffer != null) {
					return writeFromBuffer(buffer, outputChannel);
				} else {
					final FileChannel fileChannel = this.entry.fileChannel();
					if (fileChannel != null) {
						return writeFromFile(fileChannel, outputChannel);
					}
				}
			}
		}
		return STATUS_DO_NOTHING;
	}

	private final int writeFromBuffer(final ByteBuffer buffer, final WritableByteChannel channel) throws IOException {
		try {
			for (;;) {
				final int writes = channel.write(buffer);

				if (writes  > 0) {
					this.completedCount += writes;
					this.count -= writes;

					if (this.count > 0) {
						continue;
					}
				} else
				if (writes == 0) {
					if (this.count > 0) {
						return STATUS_TO_CONTINUE;
					}
				}

				break;
			}

			return STATUS_TO_TERMINATE;

		} catch (ClosedChannelException ex) {	// ClosedChannelException,
												// AsynchronousCloseException, ClosedByInterruptException
			throw ex;
		} catch (IOException ex) {
			throw ex;
		} catch (RuntimeException ex) {	// NonWritableChannelException
			throw ex;
		}
	}

	private final int writeFromFile(final FileChannel fileChannel, final WritableByteChannel channel) throws IOException {
		try {
			for (;;) {
				final long writes = fileChannel.transferTo(
						this.position + this.completedCount, this.count, channel);

				if (writes > 0) {
					this.completedCount += writes;
					this.count -= writes;

					if (this.count <= 0) {
						return STATUS_TO_TERMINATE;
					}
				} else {
					break;
				}
			}

			return (this.count <= 0) ? STATUS_TO_TERMINATE : STATUS_TO_CONTINUE;

		} catch (IOException ex) {	// ClosedChannelException,
									// AsynchronousCloseException, ClosedByInterruptException
									// IOException
			if (channel.isOpen()) {
				throw new AIOActEntryRuntimeException(ex);
			} else {
				throw ex;
			}
		} catch (NonWritableChannelException ex) {
			throw ex;
		} catch (RuntimeException ex) {	// IllegalArgumentException,
										// NonReadableChannelException
			throw new AIOActEntryRuntimeException(ex);
		}
	}

	private final int sendFromBuffer(final ByteBuffer buffer, final DatagramChannel channel,
			final SocketAddress remote) throws SocketException, IOException {
		final int sendSize = channel.socket().getSendBufferSize();
		final int oldLimit = buffer.limit();
		try {
			while (this.count > 0) {
				buffer.limit(buffer.position() + (int)((this.count > sendSize) ? sendSize : this.count));

				final int sends = channel.send(buffer, remote);

				if (sends > 0) {
					this.completedCount += sends;
					this.count -= sends;
				} else {
					break;
				}
			}
		} catch (IOException ex) {	// ClosedChannelException,
									// AsynchronousCloseException, ClosedByInterruptException
									// IOException
			throw ex;
		} catch (SecurityException ex) {
			throw ex;
		} finally {
			buffer.limit(oldLimit);
		}

		return (this.count <= 0) ? STATUS_TO_TERMINATE : STATUS_TO_CONTINUE;
	}

	@Override
	final void accomplished() {
		if (!this.future.accomplished((AIOOutputActResult)this, futureDoAccomplishCallback())) {
			internalRelease();
		}
	}

	@Override
	protected final void released() {
		super.released();

		this.requestFactory.releaseActRequest(this);
	}

	@Override
	public final String toString() {
		final StringBuilder builder = new StringBuilder();

		builder.append("OutputAct: [");
		final BaseActor<AIOOutputActResult> actor = this.actor;
		if (actor != null) {
			builder.append(actor.session.toString());
		}
		builder.append("]");

		return builder.toString();
	}

}
