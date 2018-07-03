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
public class AIOFileRegion {

	private final static int OUTPUT_MODE = 0;
	private final static int INPUT_MODE = 1;

	private final FileChannel fileChannel;

	private final long start;
	private final long end;

	private long position;
	private long count;

	private int mode;

	private final Input input;
	private final Output output;


	protected AIOFileRegion(final FileChannel fileChannel) throws IOException {
		this.fileChannel = fileChannel;

		this.input = new Input(this);
		this.output = new Output(this);

		this.start = 0;
		this.end = -1;

		this.mode = INPUT_MODE;

		this.position = 0;
		this.count = fileChannel.size();
	}

	protected AIOFileRegion(final FileChannel fileChannel, final long start, final long end) {
		this.fileChannel = fileChannel;

		this.input = new Input(this);
		this.output = new Output(this);

		this.start = start;
		this.end = end;

		this.mode = INPUT_MODE;

		this.position = start;
		this.count = end - start;
	}


	public final AIOReadableActEntry toInput() throws IOException {
		setInputMode();
		return this.input;
	}

	public final AIOInputLegacy toInputLegacy() throws IOException {
		setInputMode();
		return this.input;
	}

	public final AIOWritableActEntry toOutput() throws IOException {
		setOutputMode();
		return this.output;
	}


	public final long readFrom(final ReadableByteChannel src, long length)
			throws IOException {
		if (length < 0) {
			throw new IllegalArgumentException("length: " + length);
		}

		setOutputMode();

		if (this.count > 0 && length > this.count) {
			length = this.count;
		}

		final long reads = this.fileChannel.transferFrom(src, this.position, length);

		this.output.setCompleted(reads);

		return reads;
	}

	public final long readFrom(final ReadableByteChannel src)
			throws IOException {
		setOutputMode();

		if (this.count > 0) {
			return readFrom(src, this.count);
		} else
		if (this.count < 0) {
			long total = 0;

			for (;;) {
				final long reads = readFrom(src, lengthPerTransfer());

				if (reads > 0) {
					total += reads;
				} else {
					break;
				}
			}

			return total;
		}

		return 0;
	}

	public final long writeTo(final WritableByteChannel target, long length)
			throws IOException {
		if (length < 0) {
			throw new IllegalArgumentException("length: " + length);
		}

		setInputMode();

		if (length > this.count) {
			length = this.count;
		}

		final long writes = this.fileChannel.transferTo(this.position, length, target);

		this.input.setCompleted(writes);

		return writes;
	}

	public final long writeTo(final WritableByteChannel target)
			throws IOException {
		setInputMode();
		return writeTo(target, this.count);
	}


	protected final FileChannel fileChannel() {
		return this.fileChannel;
	}

	protected long lengthPerTransfer() {
		return 1024;
	}

	protected void onCompleted(final boolean asInput, final long position, final long count) {
	}

	protected void onInputLegacyRelease() {
	}

	protected final void setOutputMode() throws IOException {
		if (this.mode != OUTPUT_MODE) {
			this.mode = OUTPUT_MODE;

			if (this.end < 0) {
				this.position = this.fileChannel.size();
				this.count = -1;
			} else {
				this.position = this.start;
				this.count = this.end - this.start;
			}
		}
	}

	protected final void setInputMode() throws IOException {
		if (this.mode == OUTPUT_MODE) {
			this.mode = INPUT_MODE;

			if (this.end < 0) {
				this.position = 0;
				this.count = fileChannel.size();
			} else {
				this.position = this.start;
				this.count = this.end - this.start;
			}
		}
	}

	protected final void setCount(final long count) {
		this.count = count;
	}

	final void updateSize(final long oldSize, final long newSize) {
		if (this.end < 0) {
			if (this.mode == OUTPUT_MODE) {
				if (this.position == oldSize && this.count < 0) {
					this.position = newSize;
				}
			} else {
				if (this.count == oldSize && this.position == 0) {
					this.count = newSize;
				}
			}
		}
	}


	private static class IOBase {

		protected final AIOFileRegion region;


		protected IOBase(final AIOFileRegion region) {
			this.region = region;
		}


		public final ByteBuffer byteBuffer() {
			return null;
		}

		public final FileChannel fileChannel() {
			return this.region.fileChannel;
		}

		public final long position() {
			return this.region.position;
		}

		public final long count() {
			return this.region.count;
		}

		protected final void setCompleted(long count) {
			if (count > 0) {
				if (this.region.count >= 0) {
					if (count > this.region.count) {
						count = this.region.count;
					}

					this.region.count -= count;
				}

				this.region.position += count;
			}
		}

	}

	private static class Input extends IOBase implements AIOReadableActEntry, AIOInputLegacy {


		protected Input(final AIOFileRegion region) {
			super(region);
		}


		public final void completed(final long position, final long count) {
			setCompleted(count);
			this.region.onCompleted(true, position, count);
		}

		public final void release() {
			this.region.onInputLegacyRelease();
		}

	}

	private static class Output extends IOBase implements AIOWritableActEntry {


		protected Output(final AIOFileRegion region) {
			super(region);
		}


		public final void completed(final long position, final long count) {
			setCompleted(count);
			this.region.onCompleted(false, position, count);
		}

	}

}
