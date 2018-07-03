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
package com.chinmobi.aiotest.level0;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.util.concurrent.TimeUnit;

import com.chinmobi.aio.act.AIOActEntry;
import com.chinmobi.aio.act.AIOOutputActResult;
import com.chinmobi.aio.act.AIOReadableActEntry;
import com.chinmobi.aio.impl.act.BaseActRequest;
import com.chinmobi.aio.impl.act.BaseActor;
import com.chinmobi.aio.impl.act.OutputActRequest;
import com.chinmobi.aio.impl.nio.Session;

/**
 * @author <a href="mailto:yuzhaoping1970@gmail.com">Zhaoping Yu</a>
 *
 */
public final class OutputActTestAction extends BaseActTestAction<AIOOutputActResult> {


	private static final class FileActEntry implements AIOReadableActEntry {

		private final FileInputStream inputStream;
		private final FileChannel fileChannel;


		public FileActEntry(final String fileName) throws IOException {
			this.inputStream = new FileInputStream(fileName);
			this.fileChannel = this.inputStream.getChannel();
		}


		public final void close() {
			try {
				this.fileChannel.close();
			} catch (IOException ignore) {
			}
			try {
				this.inputStream.close();
			} catch (IOException ignore) {
			}
		}

		public final ByteBuffer byteBuffer() {
			return null;
		}

		public final FileChannel fileChannel() {
			return this.fileChannel;
		}

		public final long count() {
			try {
				return this.fileChannel.size() - this.fileChannel.position();
			} catch (IOException ex) {
			}
			return 0;
		}

		public final long position() {
			try {
				return this.fileChannel.position();
			} catch (IOException ex) {
			}
			return 0;
		}

		public final void completed(final long position, final long count) {
			try {
				this.fileChannel.position(position + count);
			} catch (IOException ignore) {

			}
		}

	}


	public OutputActTestAction() {
		super();
	}


	@Override
	protected final Session createSession() throws Exception {
		final SelectableChannel channel = this.pipe.sink();
		return sessionCreator().createSession(channel, SelectionKey.OP_WRITE, this);
	}

	@Override
	protected final BaseActRequest<AIOOutputActResult> createRequest(final AIOActEntry entry) throws Exception {
		if (entry.byteBuffer() != null) {
			entry.byteBuffer().put((byte)0x00);
			entry.byteBuffer().flip();
		}
		OutputActRequest request = this.session.outputActor().addRequest(this.session.id(),
				(AIOReadableActEntry)entry, this.helper, 1000, TimeUnit.MILLISECONDS,
				null);
		return request;
	}

	@Override
	protected final BaseActor<AIOOutputActResult> sessionActor() {
		return this.session.outputActor();
	}

	@Override
	protected final boolean containsFree(final BaseActRequest<AIOOutputActResult> request) {
		final OutputActRequest req = (OutputActRequest)request;
		return this.sessionContext.actRequestFactory().containsFree(req);
	}

	/*
	 * Test methods
	 */

	public final void testSessionReady() {
		try {
			BufferActEntry entry = new BufferActEntry();

			BaseActRequest<AIOOutputActResult> request = createRequest(entry);

			assertTrue(sessionActor().contains(request));
			assertFalse(request.isQueued());

			assertEquals(0, entry.position());
			assertEquals(1, entry.count());

			assertTrue(entry == request.entry());
			assertEquals(0, request.position());
			assertEquals(0, request.completedCount());
			assertFalse(request.endOfInput());

			assertEquals(0, this.helper.accomplishedCount);

			// ---------------------------------------------

			sessionActor().handleSessionReady(true);

			assertFalse(sessionActor().contains(request));

			assertEquals(1, this.helper.accomplishedCount);

			assertEquals(1, entry.position());
			assertEquals(0, entry.count());

			assertEquals(0, request.position());
			assertEquals(1, request.completedCount());
			assertFalse(request.endOfInput());

			assertNull(sessionActor().currentRequest());

			// ---------------------------------------------

		} catch (Exception ex) {
			fail(ex);
		}
	}

	public final void testSessionReadyFile0() {
		FileActEntry entry = null;
		try {
			final String fileName = "../tmp/outacttest0.txt";
			ensureFileExist(fileName, 0);

			entry = new FileActEntry(fileName);

			BaseActRequest<AIOOutputActResult> request = createRequest(entry);

			assertTrue(sessionActor().contains(request));
			assertFalse(request.isQueued());

			assertEquals(0, entry.position());
			assertEquals(0, entry.count());

			assertTrue(entry == request.entry());
			assertEquals(0, request.position());
			assertEquals(0, request.completedCount());
			assertFalse(request.endOfInput());

			assertEquals(0, this.helper.accomplishedCount);

			// ---------------------------------------------

			sessionActor().handleSessionReady(true);

			assertFalse(sessionActor().contains(request));

			assertEquals(1, this.helper.accomplishedCount);

			assertEquals(0, entry.position());
			assertEquals(0, entry.count());

			assertEquals(0, request.position());
			assertEquals(0, request.completedCount());
			assertFalse(request.endOfInput());

			assertNull(sessionActor().currentRequest());

			// ---------------------------------------------

		} catch (Exception ex) {
			fail(ex);
		} finally {
			if (entry != null) {
				entry.close();
			}
		}
	}

	public final void testSessionReadyFile1() {
		FileActEntry entry = null;
		try {
			final String fileName = "../tmp/outacttest0.txt";
			ensureFileExist(fileName, 1);

			entry = new FileActEntry(fileName);

			BaseActRequest<AIOOutputActResult> request = createRequest(entry);

			assertTrue(sessionActor().contains(request));
			assertFalse(request.isQueued());

			assertEquals(0, entry.position());
			assertEquals(1, entry.count());

			assertTrue(entry == request.entry());
			assertEquals(0, request.position());
			assertEquals(0, request.completedCount());
			assertFalse(request.endOfInput());

			assertEquals(0, this.helper.accomplishedCount);

			// ---------------------------------------------

			sessionActor().handleSessionReady(true);

			assertFalse(sessionActor().contains(request));

			assertEquals(1, this.helper.accomplishedCount);

			assertEquals(1, entry.position());
			assertEquals(0, entry.count());

			assertEquals(0, request.position());
			assertEquals(1, request.completedCount());
			assertFalse(request.endOfInput());

			assertNull(sessionActor().currentRequest());

			// ---------------------------------------------

		} catch (Exception ex) {
			fail(ex);
		} finally {
			if (entry != null) {
				entry.close();
			}
		}
	}

	private static final void ensureFileExist(final String fileName, final int bytes)
			throws IOException {
		final File file = new File(fileName);
		file.createNewFile();

		final FileOutputStream outputStream = new FileOutputStream(file);
		try {
			for (int i = 0; i < bytes; ++i) {
				outputStream.write(0x00);
			}
		} finally {
			outputStream.close();
		}
	}

}
