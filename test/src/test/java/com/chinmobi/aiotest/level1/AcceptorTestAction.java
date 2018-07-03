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

import com.chinmobi.aio.AIOAcceptor;
import com.chinmobi.aio.AIOInetEndpoint;
import com.chinmobi.aio.impl.nio.Acceptor;
import com.chinmobi.aio.impl.nio.AcceptorSet;
import com.chinmobi.aio.impl.nio.AcceptorSetImpl;
import com.chinmobi.aio.scheme.AIODatagramScheme;
import com.chinmobi.aio.scheme.AIOSocketScheme;
import com.chinmobi.aio.scheme.AIOStreamScheme;

/**
 * @author <a href="mailto:yuzhaoping1970@gmail.com">Zhaoping Yu</a>
 *
 */
public final class AcceptorTestAction extends ConnectorAcceptorTestBase {


	private static final class Helper implements AIOAcceptor.Observer {

		int startedCount;
		int stoppedCount;

		Throwable cause;


		Helper() {
		}


		final void reset() {
			this.startedCount = 0;
			this.stoppedCount = 0;
			this.cause = null;
		}

		public final void onAIOAcceptorStarted(final AIOAcceptor acceptor) {
			this.startedCount++;
		}

		public final void onAIOAcceptorAccepting(final AIOAcceptor acceptor) {
		}

		public final void onAIOAcceptorStopped(final AIOAcceptor acceptor,
				final Object msgObj, final Throwable cause) {
			this.stoppedCount++;
			this.cause = cause;
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
		public final void accept(final AIOInetEndpoint endpoint) throws IOException {
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


	private AcceptorSet acceptorSet;

	private final Helper helper;


	public AcceptorTestAction() {
		super();
		this.helper = new Helper();
	}


	protected final void doSetUp() throws Exception {
		this.acceptorSet = new AcceptorSetImpl(this.sessionContext);

		this.helper.reset();
	}

	protected final void doTearDown() throws Exception {
		this.acceptorSet.clear();
	}


	/*
	 * Test methods
	 */

	public final void testGetAcceptor() {
		final AIOSocketScheme scheme = new AIOSocketScheme();
		final AIOInetEndpoint endpoint = new AIOInetEndpoint(8080);

		final Acceptor acceptor = (Acceptor)this.acceptorSet.getAcceptor(scheme, endpoint);
		assertNotNull(acceptor);

		assertTrue(endpoint == acceptor.getLocalEndpoint());
		assertTrue(acceptor.isQueued());

		assertTrue(acceptor == this.acceptorSet.getAcceptor(scheme, endpoint));

		acceptor.close();
		assertFalse(acceptor.isQueued());

		final Acceptor acceptor2 = (Acceptor)this.acceptorSet.getAcceptor(scheme, endpoint);
		assertNotNull(acceptor2);

		assertTrue(acceptor2.isQueued());

		assertTrue(acceptor != acceptor2);

		this.acceptorSet.clear();

		assertFalse(acceptor2.isQueued());
	}

	public final void testStartAndStop0() {
		final AIOSocketScheme scheme = new AIOSocketScheme();
		final AIOInetEndpoint endpoint = new AIOInetEndpoint(8080);

		final Acceptor acceptor = (Acceptor)this.acceptorSet.getAcceptor(scheme, endpoint);
		assertNotNull(acceptor);

		assertTrue(acceptor.isQueued());

		acceptor.setObserver(this.helper);

		assertEquals(0, this.helper.startedCount);
		assertEquals(0, this.helper.stoppedCount);

		acceptor.start(this);

		assertEquals(0, this.helper.startedCount);
		assertEquals(0, this.helper.stoppedCount);

		acceptor.stop();

		assertEquals(0, this.helper.startedCount);
		assertEquals(0, this.helper.stoppedCount);

		acceptor.start(this);

		assertEquals(0, this.helper.startedCount);
		assertEquals(0, this.helper.stoppedCount);

		acceptor.start(this);

		assertEquals(0, this.helper.startedCount);
		assertEquals(0, this.helper.stoppedCount);

		runAllRunnables();

		assertEquals(1, this.helper.startedCount);
		assertEquals(0, this.helper.stoppedCount);
		assertNull(this.helper.cause);

		// -------------------------------------------------
		acceptor.stop();

		assertTrue(acceptor.isQueued());

		assertEquals(1, this.helper.startedCount);
		assertEquals(0, this.helper.stoppedCount);

		runAllRunnables();

		assertEquals(1, this.helper.startedCount);
		assertEquals(1, this.helper.stoppedCount);
		assertNull(this.helper.cause);

		assertTrue(acceptor.isQueued());
	}

	public final void testStartAndStop1() {
		final AIOSocketScheme scheme = new AIOSocketScheme();
		final AIOInetEndpoint endpoint = new AIOInetEndpoint(8080);

		final Acceptor acceptor = (Acceptor)this.acceptorSet.getAcceptor(scheme, endpoint);
		assertNotNull(acceptor);

		assertTrue(acceptor.isQueued());

		acceptor.setObserver(this.helper);

		assertEquals(0, this.helper.startedCount);
		assertEquals(0, this.helper.stoppedCount);

		acceptor.start(this);

		assertEquals(0, this.helper.startedCount);
		assertEquals(0, this.helper.stoppedCount);

		// -------------------------------------------------
		this.acceptorSet.clear();

		assertFalse(acceptor.isQueued());

		assertEquals(0, this.helper.startedCount);
		assertEquals(0, this.helper.stoppedCount);

		runAllRunnables();

		assertEquals(0, this.helper.startedCount);
		assertEquals(0, this.helper.stoppedCount);
		assertNull(this.helper.cause);
	}

	public final void testStartAndStop2() {
		final AIOSocketScheme scheme = new AIOSocketScheme();
		final AIOInetEndpoint endpoint = new AIOInetEndpoint(8080);

		final Acceptor acceptor = (Acceptor)this.acceptorSet.getAcceptor(scheme, endpoint);
		assertNotNull(acceptor);

		assertTrue(acceptor.isQueued());

		acceptor.setObserver(this.helper);

		assertEquals(0, this.helper.startedCount);
		assertEquals(0, this.helper.stoppedCount);

		acceptor.start(this);

		assertEquals(0, this.helper.startedCount);
		assertEquals(0, this.helper.stoppedCount);

		runAllRunnables();

		assertEquals(1, this.helper.startedCount);
		assertEquals(0, this.helper.stoppedCount);
		assertNull(this.helper.cause);

		// -------------------------------------------------
		this.acceptorSet.clear();

		runAllRunnables();

		assertFalse(acceptor.isQueued());

		assertEquals(1, this.helper.startedCount);
		assertEquals(1, this.helper.stoppedCount);
		assertNull(this.helper.cause);
	}

	public final void testStartAndStop3() {
		final AIODatagramScheme scheme = new AIODatagramScheme();
		final AIOInetEndpoint endpoint = new AIOInetEndpoint(8080);

		final Acceptor acceptor = (Acceptor)this.acceptorSet.getAcceptor(scheme, endpoint);
		assertNotNull(acceptor);

		assertTrue(acceptor.isQueued());

		acceptor.setObserver(this.helper);

		assertEquals(0, this.helper.startedCount);
		assertEquals(0, this.helper.stoppedCount);

		acceptor.start(this);

		assertEquals(0, this.helper.startedCount);
		assertEquals(0, this.helper.stoppedCount);

		runAllRunnables();

		assertEquals(1, this.helper.startedCount);
		if (this.helper.cause != null) {
			fail(this.helper.cause);
		}
		assertEquals(0, this.helper.stoppedCount);
		assertNull(this.helper.cause);

		// -------------------------------------------------
		this.acceptorSet.clear();

		runAllRunnables();

		assertFalse(acceptor.isQueued());

		assertEquals(1, this.helper.startedCount);
		assertEquals(1, this.helper.stoppedCount);
		assertNull(this.helper.cause);

		try {
			this.demultiplexer.select();
		} catch (Exception ex) {
			fail(ex);
		}
	}

	public final void testStartAndStop4() {
		final PipedScheme scheme = new PipedScheme();
		final AIOInetEndpoint endpoint = new AIOInetEndpoint(-1);

		final Acceptor acceptor = (Acceptor)this.acceptorSet.getAcceptor(scheme, endpoint);
		assertNotNull(acceptor);

		assertTrue(acceptor.isQueued());

		acceptor.setObserver(this.helper);

		assertEquals(0, this.helper.startedCount);
		assertEquals(0, this.helper.stoppedCount);

		acceptor.start(this);

		assertEquals(0, this.helper.startedCount);
		assertEquals(0, this.helper.stoppedCount);

		runAllRunnables();

		assertEquals(1, this.helper.startedCount);
		if (this.helper.cause != null) {
			fail(this.helper.cause);
		}
		assertEquals(0, this.helper.stoppedCount);
		assertNull(this.helper.cause);

		// -------------------------------------------------
		this.acceptorSet.clear();

		runAllRunnables();

		assertFalse(acceptor.isQueued());

		assertEquals(1, this.helper.startedCount);
		assertEquals(1, this.helper.stoppedCount);
		assertNull(this.helper.cause);

		assertEquals(1, scheme.closedCount());
	}

}
