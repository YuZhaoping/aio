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
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.DatagramChannel;
import java.nio.channels.FileChannel;
import java.nio.channels.NonReadableChannelException;
import java.nio.channels.ReadableByteChannel;

import com.chinmobi.aio.AIOFutureCallback;
import com.chinmobi.aio.act.AIOActDirection;
import com.chinmobi.aio.act.AIOActEntryRuntimeException;
import com.chinmobi.aio.act.AIODatagramActEntry;
import com.chinmobi.aio.act.AIODatagramWritableActEntry;
import com.chinmobi.aio.act.AIOInputActResult;
import com.chinmobi.aio.act.AIOInputActStrategy;
import com.chinmobi.aio.act.AIOWritableActEntry;
import com.chinmobi.aio.impl.nio.Session;
import com.chinmobi.aio.impl.nio.TransportChannel;

/**
 * @author <a href="mailto:yuzhaoping1970@gmail.com">Zhaoping Yu</a>
 *
 */
public class InputActRequest extends BaseActRequest<AIOInputActResult>
	implements AIOInputActResult {

	private static final long MAX_TRANSFER_FILE_COUNT = 10240;

	private final ActRequestFactory requestFactory;

	private boolean endOfInput;

	private AIOInputActStrategy strategy;


	InputActRequest(final ActRequestFactory requestFactory) {
		super(requestFactory.sessionContext());
		this.requestFactory = requestFactory;
	}


	final void set(final AIOWritableActEntry target, final AIOFutureCallback<AIOInputActResult> callback,
			final long timeout, final Object attachment) throws IllegalArgumentException {

		long position = target.position();
		long count = target.count();

		final ByteBuffer byteBuffer = target.byteBuffer();
		if (byteBuffer != null) {
			if (position < 0) {
				position = byteBuffer.position();
			}

			if (count < 0 || (count + position) > byteBuffer.capacity()) {
				count = byteBuffer.capacity() - position;
			}

			try {
				byteBuffer.position(0).limit((int)(position + count)).position((int)position);
			} catch (IllegalArgumentException ex) {
				throw ex;
			}

		} else {
			final FileChannel fileChannel = target.fileChannel();

			if (fileChannel != null) {
				try {
					final long fileSize = fileChannel.size();

					if (position < 0) {
						position = fileSize;
					} else
					if (position > fileSize) {
						final ByteBuffer buf = ByteBuffer.allocate(8);
						buf.put((byte)0x00);
						buf.flip();

						fileChannel.position(position);
						fileChannel.write(buf);

						fileChannel.truncate(position);
					}

				} catch (IOException ex) {
					throw new IllegalArgumentException(ex.getMessage(), ex);
				}
			}
		}

		super.set(target, position, count, timeout, attachment);
		this.endOfInput = false;

		this.future.set(this.futureReleaseCallback(), this.futureCancellable(),
				callback, attachment, (AIOInputActResult)this);
	}

	final void setStrategy(final AIOInputActStrategy strategy) {
		this.strategy = strategy;
	}

	@Override
	final AIOInputActResult inputResult() {
		return this;
	}

	@Override
	final void setEndOfInput(final boolean endOfInput) {
		this.endOfInput = endOfInput;
	}

	/* (non-Javadoc)
	 * @see com.chinmobi.aio.AIOInputActResult#entry()
	 */
	public final AIOWritableActEntry entry() {
		return (AIOWritableActEntry)this.entry;
	}

	/* (non-Javadoc)
	 * @see com.chinmobi.aio.AIOInputActResult#endOfInput()
	 */
	public final boolean endOfInput() {
		return this.endOfInput;
	}


	@Override
	final int inputReady(final Session session, final TransportChannel transportChannel) throws IOException {
		final ReadableByteChannel inputChannel = transportChannel.readableChannel();
		if (inputChannel != null) {
			return inputReady(session, inputChannel);
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
						return receiveToBuffer(buffer, datagramChannel);
					}
				}
				return STATUS_NO_MATCHED_ENTRY;
			}
		}
		return STATUS_DO_NOTHING;
	}

	@Override
	final int inputReady(final Session session, final ReadableByteChannel inputChannel) throws IOException {
		synchronized (this.future.lock()) {
			if (!this.future.isDone()) {
				final ByteBuffer buffer = this.entry.byteBuffer();
				if (buffer != null) {
					return readToBuffer(buffer, inputChannel);
				} else {
					final FileChannel fileChannel = this.entry.fileChannel();
					if (fileChannel != null) {
						return readToFile(fileChannel, inputChannel);
					}
				}
			}
		}
		return STATUS_DO_NOTHING;
	}

	private final int readToBuffer(ByteBuffer buffer, final ReadableByteChannel channel) throws IOException {
		try {
			for (;;) {
				final int reads = channel.read(buffer);

				if (reads  > 0) {
					this.completedCount += reads;
					this.count -= reads;

					if (this.strategy != null) {
						buffer = determineBufferReads(buffer, reads);
						if (buffer == null) {
							return STATUS_TO_TERMINATE;
						}
					}

					if (this.count > 0) {
						continue;
					}
				} else
				if (reads == 0) {
					if (this.count > 0) {
						return STATUS_TO_CONTINUE;
					}
				} else {
					this.endOfInput = true;
					return STATUS_END_OF_INPUT;
				}

				break;
			}

			return STATUS_TO_TERMINATE;

		} catch (ClosedChannelException ex) {	// ClosedChannelException,
												// AsynchronousCloseException, ClosedByInterruptException
			this.endOfInput = true;
			return STATUS_END_OF_INPUT;

		} catch (IOException ex) {
			throw ex;
		} catch (AIOActEntryRuntimeException ex) {
			throw ex;
		} catch (RuntimeException ex) {	// NonReadableChannelException
			throw ex;
		}
	}

	private final ByteBuffer determineBufferReads(ByteBuffer buffer, final int reads) {
		try {
			final AIOActDirection direction = this.strategy.determineInputActReads(this.entry,
					this.position, this.completedCount, reads);

			if (direction != null) {
				switch (direction.status()) {
				case TERMINATE:
					return null;
				default: // CONTINUE
				}

				long newCount = direction.newActEntryCount();
				if (newCount != 0) {
					buffer = this.entry.byteBuffer();

					if (buffer == null) {
						throw new NullPointerException("byteBuffer");
					}

					final long newPosition = buffer.position();

					if (newCount < 0 || (newCount + newPosition) > buffer.capacity()) {
						newCount = buffer.capacity() - newPosition;
					}

					buffer.position(0).limit((int)(newPosition + newCount)).position((int)newPosition);

					if (this.position > newPosition) {
						this.position = newPosition;
					}

					this.completedCount = newPosition - this.position;

					this.count = newCount;
				}
			}
		} catch (RuntimeException ex) {
			throw new AIOActEntryRuntimeException(ex);
		}

		return buffer;
	}

	private final int readToFile(FileChannel fileChannel, final ReadableByteChannel channel) throws IOException {
		try {
			for (;;) {
				final long reads = fileChannel.transferFrom(channel,
						this.position + this.completedCount,
						(this.count >= 0) ? this.count : MAX_TRANSFER_FILE_COUNT);

				if (reads > 0) {
					this.completedCount += reads;
					if (this.count > 0) this.count -= reads;

					if (this.strategy != null) {
						try {
							final AIOActDirection direction = this.strategy.determineInputActReads(this.entry,
									this.position, this.completedCount, reads);

							if (direction != null) {
								switch (direction.status()) {
								case TERMINATE:
									return STATUS_TO_TERMINATE;
								default: // CONTINUE
								}

								final long newCount = direction.newActEntryCount();
								if (newCount != 0) {
									fileChannel = this.entry.fileChannel();

									if (fileChannel == null) {
										throw new NullPointerException("fileChannel");
									}

									this.count = newCount;
								}
							}
						} catch (RuntimeException ex) {
							throw new AIOActEntryRuntimeException(ex);
						}
					}

					if (this.count == 0) {
						return STATUS_TO_TERMINATE;
					}
				} else {
					break;
				}
			}

			if (this.count  > 0) {
				return STATUS_TO_CONTINUE;
			} else
			if (this.count == 0) {
				return STATUS_TO_TERMINATE;
			} else
			if (this.completedCount > 0) {
				return STATUS_TO_TERMINATE;
			} else {
				return STATUS_TO_CONTINUE;
			}

		} catch (IOException ex) {	// ClosedChannelException,
									// AsynchronousCloseException, ClosedByInterruptException
									// IOException
			if (channel.isOpen()) {
				throw new AIOActEntryRuntimeException(ex);
			} else {
				this.endOfInput = true;
				return STATUS_END_OF_INPUT;
			}
		} catch (NonReadableChannelException ex) {
			throw ex;
		} catch (AIOActEntryRuntimeException ex) {
			throw ex;
		} catch (RuntimeException ex) {	// IllegalArgumentException,
										// NonWritableChannelException
			throw new AIOActEntryRuntimeException(ex);
		}
	}

	private final int receiveToBuffer(ByteBuffer buffer, final DatagramChannel channel) throws IOException {

		try {
			final int position = buffer.position();

			final SocketAddress remote = channel.receive(buffer);

			if (remote != null) {
				final int reads = buffer.position() - position;
				if (reads > 0) {
					//if (this.entry instanceof AIODatagramActEntry) {
					final AIODatagramWritableActEntry entry = (AIODatagramWritableActEntry)this.entry;
					entry.setRemoteSocketAddress(remote);
					//}

					this.completedCount += reads;
					this.count -= reads;

					if (this.strategy != null) {
						buffer = determineBufferReads(buffer, reads);
						if (buffer == null) {
							return STATUS_TO_TERMINATE;
						}
					} else {
						return STATUS_TO_TERMINATE;
					}
				}
			}

			return (this.count <= 0) ? STATUS_TO_TERMINATE : STATUS_TO_CONTINUE;

		} catch (IOException ex) {	// ClosedChannelException,
									// AsynchronousCloseException, ClosedByInterruptException
									// IOException
			throw ex;
		} catch (SecurityException ex) {
			throw ex;
		}
	}

	@Override
	final void accomplished() {
		if (!this.future.accomplished((AIOInputActResult)this, futureDoAccomplishCallback())) {
			internalRelease();
		}
	}

	@Override
	protected final void released() {
		super.released();
		this.strategy = null;

		this.requestFactory.releaseActRequest(this);
	}

	@Override
	public final String toString() {
		final StringBuilder builder = new StringBuilder();

		builder.append("InputAct: [");
		final BaseActor<AIOInputActResult> actor = this.actor;
		if (actor != null) {
			builder.append(actor.session.toString());
		}
		builder.append("]");

		return builder.toString();
	}

}
