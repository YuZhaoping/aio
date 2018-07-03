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
package com.chinmobi.aiotest.level1;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.concurrent.TimeUnit;

import com.chinmobi.aio.AIOFuture;
import com.chinmobi.aio.AIOFutureCallback;
import com.chinmobi.aio.AIOInetEndpoint;
import com.chinmobi.aio.AIOSession;
import com.chinmobi.aio.impl.nio.Connector;
import com.chinmobi.aio.scheme.AIODatagramScheme;
import com.chinmobi.aio.scheme.AIOSocketScheme;
import com.chinmobi.aio.scheme.AIOStreamScheme;

/**
 * @author <a href="mailto:yuzhaoping1970@gmail.com">Zhaoping Yu</a>
 *
 */
public final class ConnectorTestAction extends ConnectorAcceptorTestBase {


	private static final class Helper implements AIOFutureCallback<AIOSession> {

		int initiateCount;
		int accomplishedCount;
		int timeoutCount;
		int failedCount;
		int cancelledCount;

		Throwable cause;

		final void reset() {
			this.initiateCount = 0;
			this.accomplishedCount = 0;
			this.timeoutCount = 0;
			this.failedCount = 0;
			this.cancelledCount = 0;
			this.cause = null;
		}

		public final void initiate(final AIOFuture<AIOSession> future,
				final Object attachment, final AIOSession result) {
			this.initiateCount++;
		}

		public final void accomplished(final AIOFuture<AIOSession> future,
				final Object attachment, final AIOSession result) {
			this.accomplishedCount++;
			future.release();
		}

		public final void timeout(final AIOFuture<AIOSession> future,
				final Object attachment, final AIOSession result) {
			this.timeoutCount++;
			future.release();
		}

		public final void failed(final AIOFuture<AIOSession> future,
				final Object attachment, final AIOSession result, final Throwable cause) {
			this.failedCount++;
			this.cause = cause;
			future.release();
		}

		public final void cancelled(final AIOFuture<AIOSession> future,
				final Object attachment, final AIOSession result) {
			this.cancelledCount++;
			future.release();
		}

	}


	private static final class PipedScheme extends AIOStreamScheme {

		private PipedInputStream inputStream;
		private PipedOutputStream outputStream;

		private int closedCount;


		public PipedScheme() {
			super("PIPE");
			this.closedCount = 0;
		}


		@Override
		public final void connect(final AIOInetEndpoint endpoint) throws IOException {
			synchronized (this) {
				if (this.inputStream == null) {
					this.inputStream = new PipedInputStream();
					this.outputStream = new PipedOutputStream(this.inputStream);
				}
			}
		}

		@Override
		public final InputStream getInputStream() throws IOException {
			synchronized (this) {
				return this.inputStream;
			}
		}

		@Override
		public final OutputStream getOutputStream() throws IOException {
			synchronized (this) {
				return this.outputStream;
			}
		}

		public final void close() {
			synchronized (this) {
				++this.closedCount;

				if (this.inputStream != null) {
					try {
						this.inputStream.close();
					} catch (IOException ignore) {
					}
					try {
						this.outputStream.close();
					} catch (IOException ignore) {
					}

					this.inputStream = null;
					this.outputStream = null;
				}
			}
		}

		public final int closedCount() {
			synchronized (this) {
				return this.closedCount;
			}
		}

	}


	private Connector connector;

	private Helper helper;


	public ConnectorTestAction() {
		super();
	}


	protected final void doSetUp() throws Exception {
		this.connector = new Connector(this.sessionContext);

		this.helper = new Helper();
		this.helper.reset();
	}

	protected final void doTearDown() throws Exception {
		this.connector = null;
		this.helper = null;
	}

	/*
	 * Test methods
	 */

	public final void testConnectAndCancel0() {
		try {
			final AIOSocketScheme scheme = new AIOSocketScheme();
			final AIOInetEndpoint remote = new AIOInetEndpoint("localhost", 8080);

			final AIOFuture<AIOSession> future = this.connector.connect(scheme,
					remote, null,
					this,
					this.helper, 1000, TimeUnit.MILLISECONDS, null);

			assertNotNull(future);
			assertFalse(future.isDone());

			assertEquals(0, this.helper.initiateCount);
			assertEquals(0, this.helper.accomplishedCount);
			assertEquals(0, this.helper.timeoutCount);
			assertEquals(0, this.helper.failedCount);
			assertEquals(0, this.helper.cancelledCount);
			assertNull(this.helper.cause);

			assertEquals(0, this.connector.freeConnectRequestsCount());

			assertTrue(future.cancel(true));

			assertEquals(0, this.connector.freeConnectRequestsCount());

			runAllRunnables();

			assertEquals(1, this.connector.freeConnectRequestsCount());

			assertEquals(0, this.helper.initiateCount);
			assertEquals(0, this.helper.accomplishedCount);
			assertEquals(0, this.helper.timeoutCount);
			assertEquals(0, this.helper.failedCount);
			assertEquals(0, this.helper.cancelledCount);
			assertNull(this.helper.cause);

		} catch (Exception ex) {
			fail(ex);
		}
	}

	public final void testConnectAndCancel1() {
		try {
			final AIOSocketScheme scheme = new AIOSocketScheme();
			final AIOInetEndpoint remote = new AIOInetEndpoint("localhost", 8080);

			final AIOFuture<AIOSession> future = this.connector.connect(scheme,
					remote, null,
					this,
					this.helper, 1000, TimeUnit.MILLISECONDS, null);

			assertNotNull(future);
			assertFalse(future.isDone());

			assertEquals(0, this.helper.initiateCount);
			assertEquals(0, this.helper.accomplishedCount);
			assertEquals(0, this.helper.timeoutCount);
			assertEquals(0, this.helper.failedCount);
			assertEquals(0, this.helper.cancelledCount);
			assertNull(this.helper.cause);

			assertEquals(0, this.connector.freeConnectRequestsCount());

			runAllRunnables();

			assertEquals(1, this.helper.initiateCount);
			assertEquals(0, this.helper.accomplishedCount);
			assertEquals(0, this.helper.timeoutCount);
			assertEquals(0, this.helper.failedCount);
			assertEquals(0, this.helper.cancelledCount);
			assertNull(this.helper.cause);

			this.demultiplexer.shuttingDown();
			runAllRunnables();

			assertEquals(1, this.helper.initiateCount);
			assertEquals(0, this.helper.accomplishedCount);
			assertEquals(0, this.helper.timeoutCount);
			assertEquals(0, this.helper.failedCount);
			assertEquals(1, this.helper.cancelledCount);
			assertNull(this.helper.cause);

			assertEquals(1, this.connector.freeConnectRequestsCount());

		} catch (Exception ex) {
			fail(ex);
		}
	}

	public final void testConnect1() {
		try {
			final AIODatagramScheme scheme = new AIODatagramScheme();
			final AIOInetEndpoint remote = new AIOInetEndpoint("localhost", 8080);

			final AIOFuture<AIOSession> future = this.connector.connect(scheme,
					remote, null,
					this,
					this.helper, 1000, TimeUnit.MILLISECONDS, null);

			assertNotNull(future);
			assertFalse(future.isDone());

			assertEquals(0, this.helper.initiateCount);
			assertEquals(0, this.helper.accomplishedCount);
			assertEquals(0, this.helper.timeoutCount);
			assertEquals(0, this.helper.failedCount);
			assertEquals(0, this.helper.cancelledCount);
			assertNull(this.helper.cause);

			assertEquals(0, this.connector.freeConnectRequestsCount());

			runAllRunnables();

			assertEquals(1, this.helper.initiateCount);
			assertEquals(1, this.helper.accomplishedCount);
			assertEquals(0, this.helper.timeoutCount);
			assertEquals(0, this.helper.failedCount);
			assertEquals(0, this.helper.cancelledCount);
			assertNull(this.helper.cause);

			assertEquals(1, this.connector.freeConnectRequestsCount());

		} catch (Exception ex) {
			fail(ex);
		}
	}

	public final void testConnect2() {
		try {
			final PipedScheme scheme = new PipedScheme();
			final AIOInetEndpoint remote = new AIOInetEndpoint("pipe", -1);

			final AIOFuture<AIOSession> future = this.connector.connect(scheme,
					remote, null,
					this,
					this.helper, 1000, TimeUnit.MILLISECONDS, null);

			assertNotNull(future);
			assertFalse(future.isDone());

			assertEquals(0, this.helper.initiateCount);
			assertEquals(0, this.helper.accomplishedCount);
			assertEquals(0, this.helper.timeoutCount);
			assertEquals(0, this.helper.failedCount);
			assertEquals(0, this.helper.cancelledCount);
			assertNull(this.helper.cause);

			assertEquals(0, this.connector.freeConnectRequestsCount());

			runAllRunnables();

			assertEquals(1, this.helper.initiateCount);
			assertEquals(1, this.helper.accomplishedCount);
			assertEquals(0, this.helper.timeoutCount);
			assertEquals(0, this.helper.failedCount);
			assertEquals(0, this.helper.cancelledCount);
			assertNull(this.helper.cause);

			assertEquals(1, this.connector.freeConnectRequestsCount());


			this.demultiplexer.shuttingDown();
			runAllRunnables();

			assertEquals(1, scheme.closedCount());

		} catch (Exception ex) {
			fail(ex);
		}
	}

}
