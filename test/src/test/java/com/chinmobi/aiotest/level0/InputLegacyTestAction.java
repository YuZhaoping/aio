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
import com.chinmobi.aio.act.AIOInputActResult;
import com.chinmobi.aio.act.AIOInputActor;
import com.chinmobi.aio.act.AIOInputLegacy;
import com.chinmobi.aio.act.AIOWritableActEntry;
import com.chinmobi.aio.impl.act.BaseActRequest;
import com.chinmobi.aio.impl.act.BaseActor;
import com.chinmobi.aio.impl.act.InputActRequest;
import com.chinmobi.aio.impl.nio.Session;

/**
 * @author <a href="mailto:yuzhaoping1970@gmail.com">Zhaoping Yu</a>
 *
 */
public final class InputLegacyTestAction extends AbstractActTestAction<AIOInputActResult> {


	private static final class BufferLegacy implements AIOInputLegacy {

		private final ByteBuffer buffer;

		private boolean isReleased;


		public BufferLegacy() {
			this.buffer = ByteBuffer.allocate(8);
			this.isReleased = false;
		}


		final boolean isReleased() {
			return this.isReleased;
		}

		final void set(final int position, final int count) {
			this.buffer.position(0).limit(position + count).position(position);
		}


		public final ByteBuffer byteBuffer() {
			return this.buffer;
		}

		public final FileChannel fileChannel() {
			return null;
		}

		public final long position() {
			return this.buffer.position();
		}

		public final long count() {
			return this.buffer.remaining();
		}

		public final void release() {
			this.isReleased = true;
		}

	}


	private static final class FileLegacy implements AIOInputLegacy {

		private final FileInputStream inputStream;
		private final FileChannel fileChannel;

		private boolean isReleased;


		public FileLegacy(final String fileName) throws IOException {
			this.inputStream = new FileInputStream(fileName);
			this.fileChannel = this.inputStream.getChannel();

			this.isReleased = false;
		}


		final boolean isReleased() {
			return this.isReleased;
		}

		final void position(final long newPosition) {
			try {
				this.fileChannel.position(newPosition);
			} catch (IOException ex) {
			}
		}

		public final ByteBuffer byteBuffer() {
			return null;
		}

		public final FileChannel fileChannel() {
			return this.fileChannel;
		}

		public final long position() {
			try {
				return this.fileChannel.position();
			} catch (IOException ex) {
			}
			return 0;
		}

		public final long count() {
			try {
				return this.fileChannel.size() - this.fileChannel.position();
			} catch (IOException ex) {
			}
			return 0;
		}

		public final void release() {
			this.isReleased = true;

			try {
				this.fileChannel.close();
			} catch (IOException ignore) {
			}
			try {
				this.inputStream.close();
			} catch (IOException ignore) {
			}
		}

	}


	public InputLegacyTestAction() {
		super();
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
				null, null);
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
	}

	private final AIOInputActor inputActor() {
		return this.session.inputActor();
	}

	/*
	 * Test methods
	 */

	public final void testPushAndPopLegacy0() {
		try {
			final BufferLegacy legacy = new BufferLegacy();
			legacy.set(1, 5);

			assertFalse(legacy.isReleased());
			assertEquals(1, legacy.position());
			assertEquals(5, legacy.count());

			for (int i = 0; i < 2; ++i) {
				int index = inputActor().pushLegacy(this.session.id(), legacy, true);

				assertEquals(0, index);

				assertEquals(index, inputActor().topIndexOfLegacy(this.session.id()));

				assertFalse(legacy.isReleased());

				assertTrue(legacy == inputActor().popLegacy(this.session.id()));

				assertFalse(legacy.isReleased());

				assertEquals(-1, inputActor().topIndexOfLegacy(this.session.id()));

				assertNull(inputActor().popLegacy(this.session.id()));
			}

		} catch (Exception ex) {
			fail(ex);
		}
	}

	public final void testPushAndPopLegacy1() {
		try {
			final BufferLegacy legacy1 = new BufferLegacy();
			legacy1.set(1, 5);

			assertFalse(legacy1.isReleased());
			assertEquals(1, legacy1.position());
			assertEquals(5, legacy1.count());

			int index = inputActor().pushLegacy(this.session.id(), legacy1, true);

			assertEquals(0, index);

			assertEquals(index, inputActor().topIndexOfLegacy(this.session.id()));

			assertFalse(legacy1.isReleased());


			final BufferLegacy legacy2 = new BufferLegacy();
			legacy2.set(0, 3);

			index = inputActor().pushLegacy(this.session.id(), legacy2, true);

			assertEquals(1, index);

			assertEquals(index, inputActor().topIndexOfLegacy(this.session.id()));

			assertFalse(legacy2.isReleased());


			assertTrue(legacy2 == inputActor().popLegacy(this.session.id()));

			assertFalse(legacy2.isReleased());

			assertEquals(0, inputActor().topIndexOfLegacy(this.session.id()));


			assertTrue(legacy1 == inputActor().popLegacy(this.session.id()));

			assertFalse(legacy1.isReleased());

			assertEquals(-1, inputActor().topIndexOfLegacy(this.session.id()));


			assertNull(inputActor().popLegacy(this.session.id()));

		} catch (Exception ex) {
			fail(ex);
		}
	}

	public final void testLegacyRelease() {
		try {
			final BufferLegacy legacy1 = new BufferLegacy();
			legacy1.set(1, 5);

			assertFalse(legacy1.isReleased());
			assertEquals(1, legacy1.position());
			assertEquals(5, legacy1.count());

			int index = inputActor().pushLegacy(this.session.id(), legacy1, true);

			assertEquals(0, index);

			assertEquals(index, inputActor().topIndexOfLegacy(this.session.id()));

			assertFalse(legacy1.isReleased());


			final BufferLegacy legacy2 = new BufferLegacy();
			legacy2.set(0, 3);

			index = inputActor().pushLegacy(this.session.id(), legacy2, true);

			assertEquals(1, index);

			assertEquals(index, inputActor().topIndexOfLegacy(this.session.id()));

			assertFalse(legacy2.isReleased());


			this.session.getEventHandler().close();

			assertTrue(legacy1.isReleased());
			assertTrue(legacy2.isReleased());

		} catch (Exception ex) {
			fail(ex);
		}
	}

	public final void testHandleLegacy0() {
		try {
			final BufferLegacy legacy = new BufferLegacy();
			legacy.set(1, 5);

			assertFalse(legacy.isReleased());
			assertEquals(1, legacy.position());
			assertEquals(5, legacy.count());

			int index = inputActor().pushLegacy(this.session.id(), legacy, true);
			assertEquals(0, index);
			assertEquals(index, inputActor().topIndexOfLegacy(this.session.id()));
			assertFalse(legacy.isReleased());


			assertEquals(0, this.helper.accomplishedCount);

			final BufferActEntry entry = new BufferActEntry(8);
			final BaseActRequest<AIOInputActResult> request = createRequest(entry);

			while(!request.future().isDone())sessionActor().handleSessionReady(true);

			assertEquals(1, this.helper.accomplishedCount);

			assertEquals(0, request.position());
			assertEquals(5, request.completedCount());
			assertTrue(request.endOfInput());

			assertTrue(legacy.isReleased());

			assertEquals(-1, inputActor().topIndexOfLegacy(this.session.id()));
			assertNull(inputActor().popLegacy(this.session.id()));

		} catch (Exception ex) {
			fail(ex);
		}
	}

	public final void testHandleLegacy1() {
		try {
			final BufferLegacy legacy = new BufferLegacy();
			legacy.set(1, 5);

			assertFalse(legacy.isReleased());
			assertEquals(1, legacy.position());
			assertEquals(5, legacy.count());

			int index = inputActor().pushLegacy(this.session.id(), legacy, true);
			assertEquals(0, index);
			assertEquals(index, inputActor().topIndexOfLegacy(this.session.id()));
			assertFalse(legacy.isReleased());


			assertEquals(0, this.helper.accomplishedCount);

			final BufferActEntry entry = new BufferActEntry(3);
			final BaseActRequest<AIOInputActResult> request = createRequest(entry);

			while(!request.future().isDone())sessionActor().handleSessionReady(true);

			assertEquals(1, this.helper.accomplishedCount);

			assertEquals(0, request.position());
			assertEquals(3, request.completedCount());
			assertFalse(request.endOfInput());

			assertFalse(legacy.isReleased());

			assertEquals(0, inputActor().topIndexOfLegacy(this.session.id()));
			assertTrue(legacy == inputActor().popLegacy(this.session.id()));

		} catch (Exception ex) {
			fail(ex);
		}
	}

	public final void testHandleLegacy2() {
		try {
			final BufferLegacy legacy1 = new BufferLegacy();
			legacy1.set(1, 5);

			assertFalse(legacy1.isReleased());
			assertEquals(1, legacy1.position());
			assertEquals(5, legacy1.count());

			int index = inputActor().pushLegacy(this.session.id(), legacy1, true);
			assertEquals(0, index);
			assertEquals(index, inputActor().topIndexOfLegacy(this.session.id()));
			assertFalse(legacy1.isReleased());


			final BufferLegacy legacy2 = new BufferLegacy();
			legacy2.set(0, 3);

			index = inputActor().pushLegacy(this.session.id(), legacy2, true);
			assertEquals(1, index);
			assertEquals(index, inputActor().topIndexOfLegacy(this.session.id()));
			assertFalse(legacy2.isReleased());


			assertEquals(0, this.helper.accomplishedCount);

			final BufferActEntry entry = new BufferActEntry(10);
			final BaseActRequest<AIOInputActResult> request = createRequest(entry);

			while(!request.future().isDone())sessionActor().handleSessionReady(true);

			assertEquals(1, this.helper.accomplishedCount);

			assertEquals(0, request.position());
			assertEquals(8, request.completedCount());
			assertTrue(request.endOfInput());

			assertTrue(legacy1.isReleased());
			assertTrue(legacy2.isReleased());

			assertEquals(-1, inputActor().topIndexOfLegacy(this.session.id()));
			assertNull(inputActor().popLegacy(this.session.id()));

		} catch (Exception ex) {
			fail(ex);
		}
	}

	public final void testHandleLegacy3() {
		try {
			final BufferLegacy legacy = new BufferLegacy();
			legacy.set(1, 5);

			assertFalse(legacy.isReleased());
			assertEquals(1, legacy.position());
			assertEquals(5, legacy.count());

			int index = inputActor().pushLegacy(this.session.id(), legacy, true);
			assertEquals(0, index);
			assertEquals(index, inputActor().topIndexOfLegacy(this.session.id()));
			assertFalse(legacy.isReleased());


			assertEquals(0, this.helper.accomplishedCount);

			final BufferActEntry entry = new BufferActEntry(5);
			final BaseActRequest<AIOInputActResult> request = createRequest(entry);

			while(!request.future().isDone())sessionActor().handleSessionReady(true);

			assertEquals(1, this.helper.accomplishedCount);

			assertEquals(0, request.position());
			assertEquals(5, request.completedCount());
			assertTrue(request.endOfInput());

			assertTrue(legacy.isReleased());

			assertEquals(-1, inputActor().topIndexOfLegacy(this.session.id()));
			assertNull(inputActor().popLegacy(this.session.id()));

		} catch (Exception ex) {
			fail(ex);
		}
	}

	public final void testHandleFileLegacy0() {
		FileLegacy legacy = null;
		try {
			final String fileName = "../tmp/inlegacytest0.txt";
			ensureFileExist(fileName, 6);

			legacy = new FileLegacy(fileName);
			legacy.position(1);

			assertFalse(legacy.isReleased());
			assertEquals(1, legacy.position());
			assertEquals(5, legacy.count());

			int index = inputActor().pushLegacy(this.session.id(), legacy, true);
			assertEquals(0, index);
			assertEquals(index, inputActor().topIndexOfLegacy(this.session.id()));
			assertFalse(legacy.isReleased());


			assertEquals(0, this.helper.accomplishedCount);

			final BufferActEntry entry = new BufferActEntry(8);
			final BaseActRequest<AIOInputActResult> request = createRequest(entry);

			while(!request.future().isDone())sessionActor().handleSessionReady(true);

			assertEquals(1, this.helper.accomplishedCount);

			assertEquals(0, request.position());
			assertEquals(5, request.completedCount());
			assertTrue(request.endOfInput());

			assertTrue(legacy.isReleased());

			assertEquals(-1, inputActor().topIndexOfLegacy(this.session.id()));
			assertNull(inputActor().popLegacy(this.session.id()));

		} catch (Exception ex) {
			fail(ex);
		} finally {
			if (legacy != null) {
				legacy.release();
			}
		}
	}

	public final void testHandleFileLegacy1() {
		FileLegacy legacy = null;
		try {
			final String fileName = "../tmp/inlegacytest0.txt";
			ensureFileExist(fileName, 6);

			legacy = new FileLegacy(fileName);
			legacy.position(1);

			assertFalse(legacy.isReleased());
			assertEquals(1, legacy.position());
			assertEquals(5, legacy.count());

			int index = inputActor().pushLegacy(this.session.id(), legacy, true);
			assertEquals(0, index);
			assertEquals(index, inputActor().topIndexOfLegacy(this.session.id()));
			assertFalse(legacy.isReleased());


			assertEquals(0, this.helper.accomplishedCount);

			final BufferActEntry entry = new BufferActEntry(3);
			final BaseActRequest<AIOInputActResult> request = createRequest(entry);

			while(!request.future().isDone())sessionActor().handleSessionReady(true);

			assertEquals(1, this.helper.accomplishedCount);

			assertEquals(0, request.position());
			assertEquals(3, request.completedCount());
			assertFalse(request.endOfInput());

			assertFalse(legacy.isReleased());

			assertEquals(0, inputActor().topIndexOfLegacy(this.session.id()));
			assertTrue(legacy == inputActor().popLegacy(this.session.id()));

		} catch (Exception ex) {
			fail(ex);
		} finally {
			if (legacy != null) {
				legacy.release();
			}
		}
	}

	public final void testHandleFileLegacy2() {
		FileLegacy legacy1 = null;
		try {
			final String fileName = "../tmp/inlegacytest0.txt";
			ensureFileExist(fileName, 6);

			legacy1 = new FileLegacy(fileName);
			legacy1.position(1);

			assertFalse(legacy1.isReleased());
			assertEquals(1, legacy1.position());
			assertEquals(5, legacy1.count());

			int index = inputActor().pushLegacy(this.session.id(), legacy1, true);
			assertEquals(0, index);
			assertEquals(index, inputActor().topIndexOfLegacy(this.session.id()));
			assertFalse(legacy1.isReleased());


			final BufferLegacy legacy2 = new BufferLegacy();
			legacy2.set(0, 3);

			index = inputActor().pushLegacy(this.session.id(), legacy2, true);
			assertEquals(1, index);
			assertEquals(index, inputActor().topIndexOfLegacy(this.session.id()));
			assertFalse(legacy2.isReleased());


			assertEquals(0, this.helper.accomplishedCount);

			final BufferActEntry entry = new BufferActEntry(10);
			final BaseActRequest<AIOInputActResult> request = createRequest(entry);

			while(!request.future().isDone())sessionActor().handleSessionReady(true);

			assertEquals(1, this.helper.accomplishedCount);

			assertEquals(0, request.position());
			assertEquals(8, request.completedCount());
			assertTrue(request.endOfInput());

			assertTrue(legacy1.isReleased());
			assertTrue(legacy2.isReleased());

			assertEquals(-1, inputActor().topIndexOfLegacy(this.session.id()));
			assertNull(inputActor().popLegacy(this.session.id()));

		} catch (Exception ex) {
			fail(ex);
		} finally {
			if (legacy1 != null) {
				legacy1.release();
			}
		}
	}

	public final void testHandleFileLegacy3() {
		FileLegacy legacy = null;
		try {
			final String fileName = "../tmp/inlegacytest0.txt";
			ensureFileExist(fileName, 6);

			legacy = new FileLegacy(fileName);
			legacy.position(1);

			assertFalse(legacy.isReleased());
			assertEquals(1, legacy.position());
			assertEquals(5, legacy.count());

			int index = inputActor().pushLegacy(this.session.id(), legacy, true);
			assertEquals(0, index);
			assertEquals(index, inputActor().topIndexOfLegacy(this.session.id()));
			assertFalse(legacy.isReleased());


			assertEquals(0, this.helper.accomplishedCount);

			final BufferActEntry entry = new BufferActEntry(5);
			final BaseActRequest<AIOInputActResult> request = createRequest(entry);

			while(!request.future().isDone())sessionActor().handleSessionReady(true);

			assertEquals(1, this.helper.accomplishedCount);

			assertEquals(0, request.position());
			assertEquals(5, request.completedCount());
			assertTrue(request.endOfInput());

			assertTrue(legacy.isReleased());

			assertEquals(-1, inputActor().topIndexOfLegacy(this.session.id()));
			assertNull(inputActor().popLegacy(this.session.id()));

		} catch (Exception ex) {
			fail(ex);
		} finally {
			if (legacy != null) {
				legacy.release();
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
