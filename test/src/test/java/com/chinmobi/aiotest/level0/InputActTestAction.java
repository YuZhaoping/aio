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

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.WritableByteChannel;
import java.util.concurrent.TimeUnit;

import com.chinmobi.aio.act.AIOActDirection;
import com.chinmobi.aio.act.AIOActEntry;
import com.chinmobi.aio.act.AIOInputActResult;
import com.chinmobi.aio.act.AIOInputActStrategy;
import com.chinmobi.aio.act.AIOWritableActEntry;
import com.chinmobi.aio.impl.act.BaseActRequest;
import com.chinmobi.aio.impl.act.BaseActor;
import com.chinmobi.aio.impl.act.InputActRequest;
import com.chinmobi.aio.impl.nio.Session;

/**
 * @author <a href="mailto:yuzhaoping1970@gmail.com">Zhaoping Yu</a>
 *
 */
public final class InputActTestAction extends BaseActTestAction<AIOInputActResult> {

	private final ActStrategy strategy;


	private static final class ActStrategy implements AIOInputActStrategy {

		int testCase;

		public final void reset() {
			this.testCase = 0;
		}

		public final AIOActDirection determineInputActReads(final AIOActEntry entry,
				final long origPosition, final long totalReadCount, final long readCount) {
			switch (this.testCase) {
			case -1:
				this.testCase = 0;

				return new AIOActDirection.ContinueClass() {
					public final long newActEntryCount() {
						return -1;
					}
				};

			case 2:
				if (entry.byteBuffer().hasRemaining()) {
					return new AIOActDirection.ContinueClass();
				}

				this.testCase = 0;

				((BufferActEntry)entry).expand(BUF_INIT_CAP);
				return new AIOActDirection.ContinueClass() {
					public final long newActEntryCount() {
						return entry.byteBuffer().remaining();
					}
				};

			case 1:
				if (entry.byteBuffer().hasRemaining()) {
					return new AIOActDirection.ContinueClass();
				}

				this.testCase = 0;

				entry.byteBuffer().clear();
				return new AIOActDirection.ContinueClass() {
					public final long newActEntryCount() {
						return entry.byteBuffer().remaining();
					}
				};

			default: break;
			}
			return AIOActDirection.Terminate;
		}

	}

	private static final class FileActEntry implements AIOWritableActEntry {

		private final FileOutputStream outputStream;
		private final FileChannel fileChannel;

		private long position;
		private long count;

		private int status;


		public FileActEntry(final String fileName, final boolean append) throws IOException {
			this.outputStream = new FileOutputStream(fileName, append);
			this.fileChannel = this.outputStream.getChannel();

			this.position = this.fileChannel.position();
			this.count = -1;

			this.status = 0;
		}


		public final void setPosition(final long position) {
			this.position = position;
		}

		public final void setCount(final long count) {
			this.count = count;
		}

		public final void close() {
			try {
				this.fileChannel.close();
			} catch (IOException ignore) {
			}
			try {
				this.outputStream.close();
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
			return this.count;
		}

		public final long position() {
			if (this.status == 0) {
				return this.position;
			}
			try {
				return this.fileChannel.position();
			} catch (IOException ex) {
				return -1;
			}
		}

		public final void completed(final long position, final long count) {
			this.status = 1;
			try {
				final long size = position + count;
				this.fileChannel.position(size);
				this.fileChannel.truncate(size);
			} catch (IOException ignore) {
				ignore.printStackTrace();
			}
		}

	}


	public InputActTestAction() {
		super();
		this.strategy = new ActStrategy();
	}


	@Override
	protected final Session createSession() throws Exception {
		final SelectableChannel channel = this.pipe.source();
		return sessionCreator().createSession(channel, SelectionKey.OP_READ, this);
	}

	@Override
	protected final BaseActRequest<AIOInputActResult> createRequest(final AIOActEntry entry) throws Exception {
		InputActRequest request = this.session.inputActor().addRequest(this.session.id(),
				(AIOWritableActEntry)entry, this.helper, 1000, TimeUnit.MILLISECONDS,
				this.strategy, null);
		return request;
	}

	@Override
	protected final BaseActor<AIOInputActResult> sessionActor() {
		return this.session.inputActor();
	}

	@Override
	protected final boolean containsFree(final BaseActRequest<AIOInputActResult> request) {
		final InputActRequest req = (InputActRequest)request;
		return this.sessionContext.actRequestFactory().containsFree(req);
	}

	@Override
	protected final void doSetUp() throws Exception {
		super.doSetUp();
		this.strategy.reset();
	}

	/*
	 * Test methods
	 */

	public final void testSessionReady0() {
		try {
			BufferActEntry entry = new BufferActEntry();

			BaseActRequest<AIOInputActResult> request = createRequest(entry);

			assertTrue(sessionActor().contains(request));
			assertFalse(request.isQueued());

			assertEquals(0, entry.position());
			assertEquals(BUF_INIT_CAP, entry.count());

			assertTrue(entry == request.entry());
			assertEquals(0, request.position());
			assertEquals(0, request.completedCount());
			assertFalse(request.endOfInput());

			assertEquals(0, this.helper.accomplishedCount);

			// ---------------------------------------------
			mockWrite(1);

			while(!request.future().isDone())sessionActor().handleSessionReady(true);

			assertFalse(sessionActor().contains(request));

			assertEquals(1, this.helper.accomplishedCount);

			assertEquals(1, entry.position());
			assertEquals(BUF_INIT_CAP - 1, entry.count());

			assertEquals(0, request.position());
			assertEquals(1, request.completedCount());
			assertFalse(request.endOfInput());

			assertNull(sessionActor().currentRequest());

			// ---------------------------------------------
		} catch (Exception ex) {
			fail(ex);
		}
	}

	public final void testSessionReady1() {
		try {
			BufferActEntry entry = new BufferActEntry();

			BaseActRequest<AIOInputActResult> request = createRequest(entry);

			assertTrue(sessionActor().contains(request));
			assertFalse(request.isQueued());

			assertEquals(0, entry.position());
			assertEquals(BUF_INIT_CAP, entry.count());

			assertTrue(entry == request.entry());
			assertEquals(0, request.position());
			assertEquals(0, request.completedCount());
			assertFalse(request.endOfInput());

			assertEquals(0, this.helper.accomplishedCount);

			// ---------------------------------------------
			this.strategy.testCase = 1;

			final int count = BUF_INIT_CAP + 1;
			mockWrite(count);

			while(!request.future().isDone())sessionActor().handleSessionReady(true);

			assertFalse(sessionActor().contains(request));

			assertEquals(1, this.helper.accomplishedCount);

			assertEquals(1, entry.position());
			assertEquals(BUF_INIT_CAP - 1, entry.count());

			assertEquals(0, request.position());
			assertEquals(1, request.completedCount());
			assertFalse(request.endOfInput());

			assertNull(sessionActor().currentRequest());

			// ---------------------------------------------
		} catch (Exception ex) {
			fail(ex);
		}
	}

	public final void testSessionReady2() {
		try {
			BufferActEntry entry = new BufferActEntry();

			BaseActRequest<AIOInputActResult> request = createRequest(entry);

			assertTrue(sessionActor().contains(request));
			assertFalse(request.isQueued());

			assertEquals(0, entry.position());
			assertEquals(BUF_INIT_CAP, entry.count());

			assertTrue(entry == request.entry());
			assertEquals(0, request.position());
			assertEquals(0, request.completedCount());
			assertFalse(request.endOfInput());

			assertEquals(0, this.helper.accomplishedCount);

			// ---------------------------------------------
			this.strategy.testCase = 2;

			final int count = BUF_INIT_CAP + 1;
			mockWrite(count);

			while(!request.future().isDone())sessionActor().handleSessionReady(true);

			assertFalse(sessionActor().contains(request));

			assertEquals(1, this.helper.accomplishedCount);

			assertEquals(count, entry.position());
			assertEquals(2*BUF_INIT_CAP - count, entry.count());

			assertEquals(0, request.position());
			assertEquals(count, request.completedCount());
			assertFalse(request.endOfInput());

			assertNull(sessionActor().currentRequest());

			// ---------------------------------------------
		} catch (Exception ex) {
			fail(ex);
		}
	}

	public final void testSessionReady3() {
		try {
			BufferActEntry entry = new BufferActEntry();

			BaseActRequest<AIOInputActResult> request = createRequest(entry);

			assertTrue(sessionActor().contains(request));
			assertFalse(request.isQueued());

			assertEquals(0, entry.position());
			assertEquals(BUF_INIT_CAP, entry.count());

			assertTrue(entry == request.entry());
			assertEquals(0, request.position());
			assertEquals(0, request.completedCount());
			assertFalse(request.endOfInput());

			assertEquals(0, this.helper.accomplishedCount);

			// ---------------------------------------------
			mockWrite(BUF_INIT_CAP + 2);

			while(!request.future().isDone())sessionActor().handleSessionReady(true);

			assertFalse(sessionActor().contains(request));

			assertEquals(1, this.helper.accomplishedCount);

			assertEquals(BUF_INIT_CAP, entry.position());
			assertEquals(0, entry.count());

			assertEquals(0, request.position());
			assertEquals(BUF_INIT_CAP, request.completedCount());
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
			final String fileName = "../tmp/inacttest0.txt";

			entry = new FileActEntry(fileName, false);

			BaseActRequest<AIOInputActResult> request = createRequest(entry);

			assertTrue(sessionActor().contains(request));
			assertFalse(request.isQueued());

			assertEquals(0, entry.position());
			assertEquals(-1, entry.count());

			assertTrue(entry == request.entry());
			assertEquals(0, request.position());
			assertEquals(0, request.completedCount());
			assertFalse(request.endOfInput());

			assertEquals(0, this.helper.accomplishedCount);

			// ---------------------------------------------
			mockWrite(1);

			while(!request.future().isDone())sessionActor().handleSessionReady(true);

			assertFalse(sessionActor().contains(request));

			if (this.helper.cause != null) {
				fail(this.helper.cause);
			}
			assertEquals(1, this.helper.accomplishedCount);

			assertEquals(1, entry.position());
			assertEquals(-1, entry.count());

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

	public final void testSessionReadyFile1() {
		FileActEntry entry = null;
		try {
			final String fileName = "../tmp/inacttest0.txt";

			entry = new FileActEntry(fileName, false);
			entry.setCount(4);

			BaseActRequest<AIOInputActResult> request = createRequest(entry);

			assertTrue(sessionActor().contains(request));
			assertFalse(request.isQueued());

			assertEquals(0, entry.position());
			assertEquals(4, entry.count());

			assertTrue(entry == request.entry());
			assertEquals(0, request.position());
			assertEquals(0, request.completedCount());
			assertFalse(request.endOfInput());

			assertEquals(0, this.helper.accomplishedCount);

			// ---------------------------------------------
			this.strategy.testCase = -1;

			mockWrite(6);

			while(!request.future().isDone())sessionActor().handleSessionReady(true);

			assertFalse(sessionActor().contains(request));

			if (this.helper.cause != null) {
				fail(this.helper.cause);
			}
			assertEquals(1, this.helper.accomplishedCount);

			assertEquals(6, entry.position());
			//assertEquals(-1, entry.count());

			assertEquals(0, request.position());
			assertEquals(6, request.completedCount());
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

	public final void testSessionReadyFile2() {
		final long atPosition = 10;

		FileActEntry entry = null;
		try {
			final String fileName = "../tmp/inacttest0.txt";

			entry = new FileActEntry(fileName, false);
			entry.setPosition(atPosition);

			BaseActRequest<AIOInputActResult> request = createRequest(entry);

			assertTrue(sessionActor().contains(request));
			assertFalse(request.isQueued());

			assertEquals(atPosition, entry.position());
			assertEquals(-1, entry.count());

			assertTrue(entry == request.entry());
			assertEquals(atPosition, request.position());
			assertEquals(0, request.completedCount());
			assertFalse(request.endOfInput());

			assertEquals(0, this.helper.accomplishedCount);

			// ---------------------------------------------
			mockWrite(1);

			while(!request.future().isDone())sessionActor().handleSessionReady(true);

			assertFalse(sessionActor().contains(request));

			if (this.helper.cause != null) {
				fail(this.helper.cause);
			}
			assertEquals(1, this.helper.accomplishedCount);

			assertEquals(atPosition + 1, entry.position());
			assertEquals(-1, entry.count());

			assertEquals(atPosition, request.position());
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

	private final void mockWrite(final int count) throws IOException {
		final ByteBuffer buffer = ByteBuffer.allocate(count > 16 ? count : 16);
		for (int i = 0; i < count; ++i) {
			buffer.put((byte)0x00);
		}
		buffer.flip();

		final WritableByteChannel channel = this.pipe.sink();
		while (buffer.hasRemaining()) {
			channel.write(buffer);
		}
	}

}
