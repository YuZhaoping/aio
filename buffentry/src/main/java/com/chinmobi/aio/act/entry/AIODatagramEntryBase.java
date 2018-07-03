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
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;

import com.chinmobi.aio.act.AIODatagramReadableActEntry;
import com.chinmobi.aio.act.AIODatagramWritableActEntry;

/**
 * @author <a href="mailto:yuzhaoping1970@gmail.com">Zhaoping Yu</a>
 *
 */
public class AIODatagramEntryBase extends AIOBufferEntryBase {

	protected SocketAddress remote;


	public AIODatagramEntryBase(final int size) {
		this((AIOBufferAllocator)null, size);
	}

	public AIODatagramEntryBase(final AIOBufferAllocator allocator, final int size) {
		super();

		if (size > 0) {
			this.buffer = allocate(allocator, null, size);
		}

		this.allocator = allocator;

		this.mode = OUTPUT_MODE;

		this.input = new Input(this);
		this.output = new Output(this);
	}

	public AIODatagramEntryBase(final ByteBuffer buffer, final boolean asInput) {
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


	@Override
	public AIODatagramEntryBase wrap(final ByteBuffer buffer, final boolean asInput) {
		super.wrap(buffer, asInput);
		return this;
	}

	@Override
	public AIODatagramEntryBase clear() {
		super.clear();
		return this;
	}

	@Override
	public AIODatagramEntryBase ensureCapacity(final int requiredCapacity) {
		super.ensureCapacity(requiredCapacity);
		return this;
	}

	@Override
	public AIODatagramEntryBase ensureLength(final int requiredLength) {
		super.ensureLength(requiredLength);
		return this;
	}

	@Override
	public AIODatagramWritableActEntry toOutput() {
		return (AIODatagramWritableActEntry)super.toOutput();
	}

	@Override
	public AIODatagramWritableActEntry toOutput(final int count) {
		return (AIODatagramWritableActEntry)super.toOutput(count);
	}

	@Override
	public AIODatagramReadableActEntry toInput() {
		return (AIODatagramReadableActEntry)super.toInput();
	}

	public final int receiveFrom(final DatagramChannel channel)
			throws IOException {
		final ByteBuffer buffer = ensureOutputLength(channel.socket().getReceiveBufferSize());

		final int position = buffer.position();

		this.remote = channel.receive(buffer);

		return buffer.position() - position;
	}

	public final int sendTo(final DatagramChannel channel)
			throws IOException {
		if (this.remote != null) {

			setInputMode();

			int length = remaining();

			if (length > 0) {

				int total = 0;

				final ByteBuffer buffer = this.buffer;

				final int limit = buffer.limit();

				final int sendSize = channel.socket().getSendBufferSize();
				try {
					while (length > 0) {
						buffer.limit(buffer.position() + ((length > sendSize) ? sendSize : length));

						final int n = channel.send(buffer, this.remote);

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

			} else {
				return 0;
			}

		} else {
			throw new IllegalStateException("Null remote address");
		}
	}


	public static class Input extends AIOBufferEntryBase.Input
		implements AIODatagramReadableActEntry {

		protected Input(final AIODatagramEntryBase entry) {
			super(entry);
		}

		public final SocketAddress getRemoteSocketAddress() {
			return entry().remote;
		}

		@Override
		public AIODatagramEntryBase entry() {
			return (AIODatagramEntryBase)this.entry;
		}
	}

	public static class Output extends AIOBufferEntryBase.Output
		implements AIODatagramWritableActEntry {

		protected Output(final AIODatagramEntryBase entry) {
			super(entry);
		}

		public final void setRemoteSocketAddress(final SocketAddress remote) {
			entry().remote = remote;
		}

		@Override
		public AIODatagramEntryBase entry() {
			return (AIODatagramEntryBase)this.entry;
		}
	}

}
