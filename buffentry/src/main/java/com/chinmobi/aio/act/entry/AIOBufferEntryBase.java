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
package com.chinmobi.aio.act.entry;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;

import com.chinmobi.aio.act.AIOInputLegacy;
import com.chinmobi.aio.act.AIOReadableActEntry;
import com.chinmobi.aio.act.AIOWritableActEntry;

/**
 * @author <a href="mailto:yuzhaoping1970@gmail.com">Zhaoping Yu</a>
 *
 */
public class AIOBufferEntryBase {

	protected final static int OUTPUT_MODE = 0;
	protected final static int INPUT_MODE = 1;

	protected transient AIOBufferAllocator allocator;

	protected ByteBuffer buffer;

	protected int mode;

	protected Input input;
	protected Output output;


	protected AIOBufferEntryBase() {
		super();
	}

	public AIOBufferEntryBase(final int size) {
		this((AIOBufferAllocator)null, size);
	}

	public AIOBufferEntryBase(final AIOBufferAllocator allocator, final int size) {
		super();

		if (size > 0) {
			this.buffer = allocate(allocator, null, size);
		}

		this.allocator = allocator;

		this.mode = OUTPUT_MODE;

		this.input = new Input(this);
		this.output = new Output(this);
	}

	public AIOBufferEntryBase(final ByteBuffer buffer, final boolean asInput) {
		super();

		this.buffer = buffer;

		if (asInput) {
			this.mode = INPUT_MODE;
		} else {
			this.mode = OUTPUT_MODE;
		}

		this.input = new Input(this);
		this.output = new Output(this);
	}


	public final void setAllocator(final AIOBufferAllocator allocator) {
		this.allocator = allocator;
	}

	public AIOBufferEntryBase wrap(final ByteBuffer buffer, final boolean asInput) {
		this.buffer = buffer;

		if (asInput) {
			this.mode = INPUT_MODE;
		} else {
			this.mode = OUTPUT_MODE;
		}

		return this;
	}

	public AIOBufferEntryBase clear() {
		final ByteBuffer buffer = this.buffer;
		if (buffer != null) {
			buffer.clear();
		}

		this.mode = OUTPUT_MODE;

		return this;
	}

	public AIOBufferEntryBase ensureCapacity(final int requiredCapacity) {
		final ByteBuffer buffer = this.buffer;

		if (buffer == null) {
			this.allocate(null, requiredCapacity);
			clear();
		} else if (buffer.capacity() < requiredCapacity) {
			final int position = buffer.position();

			final ByteBuffer newBuffer = allocate(buffer, requiredCapacity);

			if (this.mode == OUTPUT_MODE) {
				buffer.flip();
			} else {
				buffer.position(0);
			}

			newBuffer.put(buffer);

			if (this.mode != OUTPUT_MODE) {
				newBuffer.flip();
			}

			newBuffer.position(position);
		}

		return this;
	}

	public AIOBufferEntryBase ensureLength(final int requiredLength) {
		ensureOutputLength(requiredLength);
		return this;
	}

	public AIOWritableActEntry toOutput() {
		setOutputMode();
		return this.output;
	}

	public AIOWritableActEntry toOutput(final int count) {
		final ByteBuffer buffer = ensureOutputLength(count);
		buffer.limit(buffer.position() + count);
		return this.output;
	}

	public AIOReadableActEntry toInput() {
		setInputMode();
		return this.input;
	}

	public AIOInputLegacy toInputLegacy() {
		setInputMode();
		return this.input;
	}

	public final int readFrom(final ReadableByteChannel src, int length)
			throws IOException {
		if (length > 0) {
			final ByteBuffer buffer = ensureOutputLength(length);

			int total = 0;

			final int limit = buffer.limit();
			buffer.limit(buffer.position() + length);
			try {
				while (length > 0) {
					final int n = src.read(buffer);

					if (n > 0) {
						total += n;
						length -= n;
					} else if (n < 0) {
						if (total == 0) {
							total = -1;
						}
						break;
					} else {
						break;
					}
				}
			} finally {
				buffer.limit(limit);
			}

			return total;
		} else if (length == 0) {
		} else {
			throw new IllegalArgumentException("length: " + length);
		}
		return 0;
	}

	public final int readFrom(final ReadableByteChannel src)
			throws IOException {

		setOutputMode();

		int total = 0;

		for (;;) {
			int length = remaining();

			if (length > 0) {
				final int n = readFrom(src, length);

				if (n > 0) {
					total += n;
					length -= n;

					if (length <= 0) {
						ensureOutputLength(lengthPerTransfer());
						continue;
					}
				} else if (n < 0) {
					if (total == 0) {
						total = -1;
					}
				} else {
				}
			} else {
				ensureOutputLength(lengthPerTransfer());
				continue;
			}

			break;
		}

		return total;
	}

	protected int lengthPerTransfer() {
		return 1024;
	}

	public final int writeTo(final WritableByteChannel target, int length)
			throws IOException {

		setInputMode();

		if (length > 0 && length <= remaining()) {

			int total = 0;

			final ByteBuffer buffer = this.buffer;

			final int limit = buffer.limit();
			buffer.limit(buffer.position() + length);
			try {
				while (length > 0) {
					final int n = target.write(buffer);

					if (n > 0) {
						total += n;
						length -= n;
					} else {
						break;
					}
				}
			} finally {
				buffer.limit(limit);
			}

			return total;

		} else if (length == 0) {
		} else {
			throw new IllegalArgumentException("length: " + length);
		}
		return 0;
	}

	public final int writeTo(final WritableByteChannel target)
			throws IOException {
		setInputMode();
		return writeTo(target, remaining());
	}


	protected void onCompleted(final boolean asInput, final long position, final long count) {
	}


	protected final int remaining() {
		if (this.buffer != null) {
			return this.buffer.remaining();
		} else {
			return 0;
		}
	}

	/*
	 * Mode methods
	 */

	protected final void setOutputMode() {
		if (this.mode != OUTPUT_MODE) {
			this.mode = OUTPUT_MODE;

			final ByteBuffer buffer = this.buffer;

			if (buffer != null) {
				if (buffer.hasRemaining()) {
					buffer.compact();
				} else {
					buffer.clear();
				}
			}
		}
	}

	protected final void setInputMode() {
		if (this.mode == OUTPUT_MODE) {
			this.mode = INPUT_MODE;

			final ByteBuffer buffer = this.buffer;

			if (buffer != null) {
				buffer.flip();
			}
		}
	}

	/*
	 * Allocate methods
	 */

	protected final ByteBuffer ensureOutputLength(final int requiredLength) {
		setOutputMode();
		final ByteBuffer buffer = this.buffer;
		if (buffer != null) {
			return ensureLength(buffer, requiredLength);
		} else {
			return allocate(buffer, requiredLength);
		}
	}

	protected final ByteBuffer ensureLength(ByteBuffer buffer, final int requiredLength) {
		final int requiredCapacity = buffer.position() + requiredLength;
		if (requiredCapacity > buffer.capacity()) {
			buffer = expandLength(buffer, requiredLength);
		}
		return buffer;
	}

	protected final ByteBuffer expandLength(final ByteBuffer buffer, final int length) {
		final int requiredCapacity = buffer.position() + length;
		final ByteBuffer newBuffer = allocate(buffer, requiredCapacity);
		buffer.flip();
		newBuffer.put(buffer);
		return newBuffer;
	}

	protected final ByteBuffer allocate(final ByteBuffer oldBuffer, final int size) {
		final ByteBuffer buffer = allocate(this.allocator, oldBuffer, size);
		this.buffer = buffer;
		return buffer;
	}

	public static final ByteBuffer allocate(final AIOBufferAllocator allocator,
			final ByteBuffer oldBuffer, int size) {
		size = newCapacity(oldBuffer, size);

		ByteBuffer newBuffer;
		if (allocator != null) {
			newBuffer = allocator.aioAllocate(oldBuffer, size);
		} else {
			if (oldBuffer != null && oldBuffer.isDirect()) {
				newBuffer = ByteBuffer.allocateDirect(size);
			} else {
				newBuffer = ByteBuffer.allocate(size);
			}
		}
		return newBuffer;
	}

	private static final int newCapacity(final ByteBuffer buffer, final int requiredCapacity) {
		final int currentCapacity = (buffer != null) ? buffer.capacity() : 0;
		int newCapacity = currentCapacity;

		newCapacity += (newCapacity >> 1);

		if (requiredCapacity > newCapacity) {
			newCapacity = requiredCapacity;
		}

		int padding = newCapacity & 0x03;
		if (padding > 0) padding = (4 - padding);
		newCapacity += padding;

		return newCapacity;
	}


	private static class IOBase {

		protected final AIOBufferEntryBase entry;


		protected IOBase(final AIOBufferEntryBase entry) {
			this.entry = entry;
		}


		public final ByteBuffer byteBuffer() {
			return this.entry.buffer;
		}

		public final FileChannel fileChannel() {
			return null;
		}

		public final long position() {
			if (this.entry.buffer != null) {
				return this.entry.buffer.position();
			} else {
				return 0;
			}
		}

		public final long count() {
			return this.entry.remaining();
		}

		public AIOBufferEntryBase entry() {
			return this.entry;
		}

	}

	public static class Input extends IOBase implements AIOReadableActEntry, AIOInputLegacy {


		protected Input(final AIOBufferEntryBase entry) {
			super(entry);
		}


		public final void completed(final long position, final long count) {
			this.entry.onCompleted(true, position, count);
		}

		public final void release() {
			// Nothing to do.
		}

	}

	public static class Output extends IOBase implements AIOWritableActEntry {


		protected Output(final AIOBufferEntryBase entry) {
			super(entry);
		}


		public final void completed(final long position, final long count) {
			this.entry.onCompleted(false, position, count);
		}

	}

}
