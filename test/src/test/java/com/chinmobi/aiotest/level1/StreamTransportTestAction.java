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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.chinmobi.aio.AIOAcceptor;
import com.chinmobi.aio.AIOFuture;
import com.chinmobi.aio.AIOInetEndpoint;
import com.chinmobi.aio.AIOSession;
import com.chinmobi.aio.scheme.AIOStreamScheme;

/**
 * @author <a href="mailto:yuzhaoping1970@gmail.com">Zhaoping Yu</a>
 *
 */
public final class StreamTransportTestAction extends TransportTestsBase {


	private static class PipedScheme extends AIOStreamScheme {

		protected PipedInputStream inputStream;
		protected PipedOutputStream outputStream;

		private int closedCount;

		protected volatile PipedScheme peer;

		private volatile int id;


		protected PipedScheme(final String name) {
			super(name);
			this.closedCount = 0;
		}


		final void setPeer(final PipedScheme peer) {
			this.peer = peer;
		}

		protected final void construct() {
			synchronized (this) {
				if (this.inputStream == null) {
					this.inputStream = new PipedInputStream();
					this.outputStream = new PipedOutputStream();
				}
			}
		}

		public final void reset(final int id) {
			synchronized (this) {
				this.closedCount = 0;
				this.id = id;
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

		@Override
		public final void close() {
			synchronized (this) {
				if (this.inputStream != null) {
					++this.closedCount;

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
				} else {
					return;
				}
			}
		}

		public final int closedCount() {
			synchronized (this) {
				if (this.inputStream == null) {
					return 1;
				}
				return this.closedCount;
			}
		}

		@Override
		public final String toString() {
			final StringBuilder builder = new StringBuilder();

			builder.append(name()).append('_').append(this.id);

			return builder.toString();
		}

	}

	private static final class ServerPipedScheme extends PipedScheme {

		public ServerPipedScheme() {
			super("SvrPIPE");
		}


		@Override
		public final void accept(final AIOInetEndpoint endpoint) throws IOException {
			this.peer.construct();
			synchronized (this) {
				if (this.inputStream == null) {
					this.construct();

					this.inputStream.connect(this.peer.outputStream);
					this.outputStream.connect(this.peer.inputStream);
				}
			}
		}

	}

	private static final class ClientPipedScheme extends PipedScheme {

		public ClientPipedScheme() {
			super("CltPIPE");
		}


		@Override
		public final void connect(final AIOInetEndpoint endpoint) throws IOException {
			this.peer.construct();
			synchronized (this) {
				if (this.inputStream == null) {
					this.construct();

					this.inputStream.connect(this.peer.outputStream);
					this.outputStream.connect(this.peer.inputStream);
				}
			}
		}

	}


	private volatile ServerPipedScheme serverScheme;
	private volatile ClientPipedScheme clientScheme;

	private volatile int schemeId;


	public StreamTransportTestAction() {
		super();
		this.schemeId = 0;
	}


	@Override
	protected final void doSetUp() throws Exception {
		setUpScheme();
	}

	@Override
	protected final void doTearDown() throws Exception {
		tearDownScheme();
	}

	private final void setUpScheme() {
		if (this.serverScheme == null) {
			++schemeId;

			this.serverScheme = new ServerPipedScheme();
			this.clientScheme = new ClientPipedScheme();

			this.serverScheme.reset(this.schemeId);
			this.clientScheme.reset(this.schemeId);

			this.serverScheme.setPeer(this.clientScheme);
			this.clientScheme.setPeer(this.serverScheme);
		}
	}

	private final void tearDownScheme() {
		if (this.serverScheme != null) {
			this.serverScheme.close();
			this.serverScheme = null;

			this.clientScheme.close();
			this.clientScheme = null;
		}
	}

	private final void resetScheme() {
		if (this.serverScheme == null) {
			setUpScheme();
		} else {
			++schemeId;

			this.serverScheme.close();
			this.serverScheme.reset(this.schemeId);

			this.clientScheme.close();
			this.clientScheme.reset(this.schemeId);
		}
	}


	/*
	 * Test methods
	 */

	public final void testAccept0() {
		try {
			setUpReactor(2, true, false);

			final AIOInetEndpoint endpoint = new AIOInetEndpoint(-1);

			final AIOAcceptor acceptor = this.reactor.getAcceptor(this.serverScheme, endpoint);
			assertNotNull(acceptor);

			acceptor.setObserver(this.acceptorObserver);
			acceptor.start(this.serverHandler);

			this.acceptorObserver.waitForStarted();
			if (this.acceptorObserver.cause != null) {
				fail(this.acceptorObserver.cause);
			}


			acceptor.close();


			this.acceptorObserver.waitForStopped();
			/*if (this.acceptorObserver.cause != null) {
				fail(this.acceptorObserver.cause);
			}*/

			assertEquals(1, this.serverScheme.closedCount());

		} catch (Exception ex) {
			fail(ex);
		} finally {
			tearDownReactor();
		}
	}

	private final AIOAcceptor startAccept() throws InterruptedException {
		final AIOInetEndpoint endpoint = new AIOInetEndpoint(-1);

		final AIOAcceptor acceptor = this.reactor.getAcceptor(this.serverScheme, endpoint);
		assertNotNull(acceptor);

		acceptor.setObserver(this.acceptorObserver);
		acceptor.start(this.serverHandler);

		this.acceptorObserver.waitForStarted();
		this.acceptorObserver.waitForAccepting();
		if (this.acceptorObserver.cause != null) {
			fail(this.acceptorObserver.cause);
		}

		this.serverHandler.waitForOpened();
		if (this.serverHandler.cause != null) {
			fail(this.serverHandler.cause);
		}
		assertEquals(1, this.serverHandler.openedCount);

		return acceptor;
	}

	private final void endAccept() throws InterruptedException {
		this.serverHandler.waitForClosed();
		if (this.serverHandler.cause != null) {
			fail(this.serverHandler.cause);
		}
		assertEquals(1, this.serverHandler.closedCount);

		this.acceptorObserver.waitForStopped();
		if (this.acceptorObserver.cause != null) {
			fail(this.acceptorObserver.cause);
		}

		assertEquals(1, this.serverScheme.closedCount());
	}

	public final void testAccept1() {
		try {
			setUpReactor(2, true, false);

			final AIOAcceptor acceptor = startAccept();

			acceptor.close();

			endAccept();

		} catch (Exception ex) {
			fail(ex);
		} finally {
			tearDownReactor();
		}
	}

	public final void testAccept2() {
		try {
			setUpReactor(2, true, false);

			for (int i = 0; i < 5; ++i) {
				this.serverHandler.reset();
				this.clientHandler.reset();

				this.acceptorObserver.reset();

				if (i > 0) {
					resetScheme();
				}

				doTestAccept2();

				/*if (i == 0) {
					this.reactor.logger().enableLevel(Logger.Level.OFF);
				}*/
			}
		} catch (Exception ex) {
			fail(ex);
		} finally {
			tearDownReactor();
		}
	}

	private final void doTestAccept2() {
		try {
			startAccept();

			this.serverHandler.session.close();

			endAccept();

		} catch (Exception ex) {
			fail(ex);
		}
	}

	private final AIOSession startConnect() throws InterruptedException, ExecutionException, TimeoutException {
		final AIOInetEndpoint remote = new AIOInetEndpoint("pipe", -1);

		final AIOFuture<AIOSession> future = this.reactor.getConnector().connect(
				this.clientScheme, remote, null,
				this.clientHandler,
				null, 1000, TimeUnit.MILLISECONDS, null);

		assertNotNull(future);

		final AIOSession session = future.get(10000, TimeUnit.MILLISECONDS);
		future.release();
		assertNotNull(session);

		assertTrue(session.isOpen());

		this.clientHandler.waitForOpened();
		if (this.clientHandler.cause != null) {
			fail(this.clientHandler.cause);
		}
		assertEquals(1, this.clientHandler.openedCount);

		return session;
	}

	private final void endConnect() throws InterruptedException {
		this.clientHandler.waitForClosed();
		if (this.clientHandler.cause != null) {
			fail(this.clientHandler.cause);
		}
		assertEquals(1, this.clientHandler.closedCount);

		assertEquals(1, this.clientScheme.closedCount());
	}

	public final void testConnect() {
		try {
			setUpReactor(2, true, false);
			for (int i = 0; i < 5; ++i) {
				this.serverHandler.reset();
				this.clientHandler.reset();

				if (i > 0) {
					resetScheme();
				}

				doTestConnect();

				/*if (i == 0) {
					this.reactor.logger().enableLevel(Logger.Level.OFF);
				}*/
			}
		} catch (Exception ex) {
			fail(ex);
		} finally {
			tearDownReactor();
		}
	}

	private final void doTestConnect() {
		try {
			final AIOSession session = startConnect();

			session.close();

			endConnect();

		} catch (Exception ex) {
			fail(ex);
		}
	}

	public final void testXInputOutputAct() {
		try {
			setUpReactor(3, true, false);

			for (int i = 0; i < 5; ++i) {
				this.serverHandler.reset();
				this.clientHandler.reset();

				this.acceptorObserver.reset();

				if (i > 0) {
					resetScheme();
				}

				doTestAct(100);

				/*if (i == 0) {
					this.reactor.logger().enableLevel(Logger.Level.OFF);
				}*/
			}
		} catch (Exception ex) {
			fail(ex);
		} finally {
			tearDownReactor();
		}
	}

	private final void doTestAct(final long transferCount) {
		AIOSession serverSession = null;
		AIOSession clientSession = null;
		try {
			startAccept();
			startConnect();

			/*final AIOSession */serverSession = this.serverHandler.session;
			/*final AIOSession */clientSession = this.clientHandler.session;


			final ServerInputHelper serverHelper = new ServerInputHelper(serverSession);
			serverHelper.setSpecificCount(transferCount);
			serverHelper.receive();

			final ClientInputHelper clientHelper = new ClientInputHelper(clientSession);
			clientHelper.setSpecificCount(transferCount);
			clientHelper.receive();

			final OutputHelper outputHelper = new OutputHelper(clientSession);
			outputHelper.send(transferCount);


			outputHelper.waitForDone();
			if (outputHelper.cause != null) {
				fail(outputHelper.cause);
			}
			assertEquals(0, outputHelper.remainingCount);

			serverHelper.waitForDone(transferCount);
			if (serverHelper.cause != null) {
				fail(serverHelper.cause);
			}
			assertEquals(transferCount, serverHelper.receivedCount);

			clientHelper.waitForDone(transferCount);
			if (clientHelper.cause != null) {
				fail(clientHelper.cause);
			}
			assertEquals(transferCount, clientHelper.receivedCount);


			clientSession.close();


			endConnect();
			endAccept();

		} catch (Exception ex) {
			if (serverSession != null) {
				this.out().println(serverSession.toString());
			}
			if (clientSession != null) {
				this.out().println(clientSession.toString());
			}

			fail(ex);
		}
	}

}
