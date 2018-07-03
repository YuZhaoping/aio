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
package com.chinmobi.aio.octet;

import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

import com.chinmobi.aio.act.AIOInputLegacy;
import com.chinmobi.aio.act.AIOReadableActEntry;
import com.chinmobi.aio.act.AIOWritableActEntry;
import com.chinmobi.octet.ExpandableOctetBuffer;

/**
 * @author <a href="mailto:yuzhaoping1970@gmail.com">Zhaoping Yu</a>
 *
 */
public class AIOOctetBufferBase {

	protected ExpandableOctetBuffer octetBuffer;

	protected Input input;
	protected Output output;


	protected AIOOctetBufferBase() {
	}

	public AIOOctetBufferBase(final ExpandableOctetBuffer octetBuffer) {
		this.octetBuffer = octetBuffer;

		this.input = new Input(this);
		this.output = new Output(this);
	}


	public final ExpandableOctetBuffer octetBuffer() {
		return this.octetBuffer;
	}

	public AIOOctetBufferBase wrap(final ExpandableOctetBuffer octetBuffer) {
		this.octetBuffer = octetBuffer;
		return this;
	}

	public AIOOctetBufferBase clear() {
		final ExpandableOctetBuffer octet = this.octetBuffer;
		if (octet != null) {
			octet.clear();
		}
		return this;
	}

	public AIOWritableActEntry toOutput() {
		final ExpandableOctetBuffer octet = this.octetBuffer;
		if (octet != null) {
			octet.toOutput();
		}
		return this.output;
	}

	public AIOWritableActEntry toOutput(final int count) {
		final ExpandableOctetBuffer octet = this.octetBuffer;
		if (octet != null) {
			octet.toOutput();
			final int start = octet.position();
			final int end = start + count;
			octet.toOutput(start, end);
		}
		return this.output;
	}

	public AIOReadableActEntry toInput() {
		final ExpandableOctetBuffer octet = this.octetBuffer;
		if (octet != null) {
			octet.toInput();
		}
		return this.input;
	}

	public AIOInputLegacy toInputLegacy() {
		final ExpandableOctetBuffer octet = this.octetBuffer;
		if (octet != null) {
			octet.toInput();
		}
		return this.input;
	}


	protected void onCompleted(final boolean asInput, final long position, final long count) {
	}


	private final ByteBuffer buffer() {
		final ExpandableOctetBuffer octet = this.octetBuffer;
		return (octet != null) ? octet.buffer() : null;
	}

	private final int position() {
		final ExpandableOctetBuffer octet = this.octetBuffer;
		return (octet != null) ? octet.position() : 0;
	}

	private final int remaining() {
		final ExpandableOctetBuffer octet = this.octetBuffer;
		return (octet != null) ? octet.remaining() : 0;
	}


	private static class IOBase {

		protected final AIOOctetBufferBase base;


		protected IOBase(final AIOOctetBufferBase base) {
			this.base = base;
		}


		public final ByteBuffer byteBuffer() { return this.base.buffer(); }

		public final FileChannel fileChannel() { return null; }

		public final long position() { return this.base.position(); }

		public final long count() { return this.base.remaining(); }

		public AIOOctetBufferBase base() {
			return this.base;
		}

	}


	public static class Input extends IOBase implements AIOReadableActEntry, AIOInputLegacy {


		protected Input(final AIOOctetBufferBase base) {
			super(base);
		}


		public final void completed(final long position, final long count) {
			this.base.onCompleted(true, position, count);
		}

		public final void release() {
			// Nothing to do.
		}

	}


	public static class Output extends IOBase implements AIOWritableActEntry {


		protected Output(final AIOOctetBufferBase base) {
			super(base);
		}


		public final void completed(final long position, final long count) {
			this.base.onCompleted(false, position, count);
		}

	}

}
