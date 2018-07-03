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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

import com.chinmobi.aio.act.AIOInputLegacy;
import com.chinmobi.aio.act.AIOReadableActEntry;
import com.chinmobi.aio.act.AIOWritableActEntry;

/**
 * @author <a href="mailto:yuzhaoping1970@gmail.com">Zhaoping Yu</a>
 *
 */
public class AIOFileEntryBase {

	private final RandomAccessFile raFile;

	private final Region region;


	public AIOFileEntryBase(final File file) throws FileNotFoundException, IOException {
		this.raFile = new RandomAccessFile(file, "rwd");

		this.region = new Region(this, this.raFile.getChannel());
	}

	public AIOFileEntryBase(final String fileName) throws FileNotFoundException, IOException {
		this(new File(fileName));
	}


	public final void close() {
		try {
			this.region.fileChannel().close();
			this.raFile.close();
		} catch (IOException ignore) {
		}
	}

	public final AIOFileEntryBase truncate(final long size) throws IOException {
		final long oldSize = this.region.fileChannel().size();

		if (size > oldSize) {
			ensureSize(size, true);
		} else {
			this.region.fileChannel().truncate(size);
			this.region.updateSize(oldSize, size);
		}

		return this;
	}

	public final AIOReadableActEntry toInput() throws IOException {
		return this.region.toInput();
	}

	public final AIOInputLegacy toInputLegacy() throws IOException {
		return this.region.toInputLegacy();
	}

	public final AIOWritableActEntry toOutput() throws IOException {
		return this.region.toOutput();
	}

	public final AIOWritableActEntry toOutput(final long count) throws IOException {
		if (count < 0) {
			throw new IllegalArgumentException("count: " + count);
		}

		final AIOWritableActEntry entry = this.region.toOutput();

		ensureSize(entry.position() + count, false);

		this.region.setCount(count);

		return entry;
	}

	public final AIOFileRegion createRegion(final long start, final long end) throws IOException {

		if (end <= start || start < 0) {
			throw new IllegalArgumentException("start: " + start + " end: " + end);
		}

		ensureSize(end, true);

		return new Region(this, this.region.fileChannel(), start, end);
	}

	private final void ensureSize(final long newSize, final boolean toUpdate) throws IOException {
		final FileChannel fileChannel = this.region.fileChannel();
		final long size = fileChannel.size();

		if (newSize > size) {
			final ByteBuffer buf = ByteBuffer.allocate(8);
			buf.put((byte)0x00);
			buf.flip();

			fileChannel.position(newSize);
			fileChannel.write(buf);

			fileChannel.truncate(newSize);

			if (toUpdate) {
				this.region.updateSize(size, newSize);
			}
		}
	}

	protected final AIOFileRegion region() {
		return this.region;
	}

	protected long lengthPerTransfer() {
		return 1024;
	}

	protected void onCompleted(final AIOFileRegion region, final boolean asInput, final long position, final long count) {
	}

	protected void onInputLegacyRelease(final AIOFileRegion region) {
		if (this.region == region) {
			close();
		}
	}


	private static final class Region extends AIOFileRegion {

		private final AIOFileEntryBase entryBase;


		Region(final AIOFileEntryBase entryBase, final FileChannel fileChannel) throws IOException {
			super(fileChannel);
			this.entryBase = entryBase;
		}

		Region(final AIOFileEntryBase entryBase,
				final FileChannel fileChannel, final long start, final long end) throws IOException {
			super(fileChannel, start, end);
			this.entryBase = entryBase;
		}


		protected final long lengthPerTransfer() {
			return this.entryBase.lengthPerTransfer();
		}

		@Override
		protected final void onCompleted(final boolean asInput, final long position, final long count) {
			this.entryBase.onCompleted(this, asInput, position, count);
		}

		@Override
		protected final void onInputLegacyRelease() {
			this.entryBase.onInputLegacyRelease(this);
		}

	}

}
