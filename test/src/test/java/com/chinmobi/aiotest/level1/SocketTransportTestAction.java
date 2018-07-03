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

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import com.chinmobi.aio.AIOAcceptor;
import com.chinmobi.aio.AIOFuture;
import com.chinmobi.aio.AIOInetEndpoint;
import com.chinmobi.aio.AIOSession;
import com.chinmobi.aio.scheme.AIOSocketScheme;

/**
 * @author <a href="mailto:yuzhaoping1970@gmail.com">Zhaoping Yu</a>
 *
 */
public final class SocketTransportTestAction extends TransportTestsBase {


	public SocketTransportTestAction() {
		super();
	}


	@Override
	protected final void doSetUp() throws Exception {
	}

	@Override
	protected final void doTearDown() throws Exception {
	}


	/*
	 * Test methods
	 */

	public final void testAccept() {
		try {
			setUpReactor(3, true, false);
			for (int i = 0; i < 5; ++i) {
				this.serverHandler.reset();
				this.clientHandler.reset();

				this.acceptorObserver.reset();

				doTestAccept();

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

	private final void doTestAccept() {
		try {
			final AIOSocketScheme scheme = new AIOSocketScheme();
			final AIOInetEndpoint endpoint = new AIOInetEndpoint(8080);

			final AIOAcceptor acceptor = this.reactor.getAcceptor(scheme, endpoint);
			assertNotNull(acceptor);

			acceptor.setObserver(this.acceptorObserver);
			acceptor.start(this.serverHandler);

			this.acceptorObserver.waitForStarted();
			if (this.acceptorObserver.cause != null) {
				fail(this.acceptorObserver.cause);
			}

			this.acceptorObserver.waitForAccepting();
			if (this.acceptorObserver.cause != null) {
				fail(this.acceptorObserver.cause);
			}


			acceptor.close();


			this.acceptorObserver.waitForStopped();
			if (this.acceptorObserver.cause != null) {
				fail(this.acceptorObserver.cause);
			}
		} catch (Exception ex) {
			fail(ex);
		}
	}

	private final AIOAcceptor startAcceptor() throws InterruptedException {
		final AIOSocketScheme scheme = new AIOSocketScheme();
		final AIOInetEndpoint endpoint = new AIOInetEndpoint(8080);

		final AIOAcceptor acceptor = this.reactor.getAcceptor(scheme, endpoint);
		assertNotNull(acceptor);

		if (acceptor.isActive()) {
			++this.acceptorObserver.startedCount;
			++this.acceptorObserver.acceptingCount;
		}

		acceptor.setObserver(this.acceptorObserver);
		acceptor.start(this.serverHandler);

		this.acceptorObserver.waitForAccepting();
		if (this.acceptorObserver.cause != null) {
			fail(this.acceptorObserver.cause);
		}

		return acceptor;
	}

	private final AIOSession startConnect() throws InterruptedException, ExecutionException {
		final AIOSocketScheme scheme = new AIOSocketScheme();
		/*final AIOAcceptor acceptor = */startAcceptor();

		final AIOInetEndpoint remote = new AIOInetEndpoint("localhost", 8080);
		final AIOFuture<AIOSession> future = this.reactor.getConnector().connect(
				scheme, remote, null,
				this.clientHandler,
				null, 1000, TimeUnit.MILLISECONDS, null);

		assertNotNull(future);

		final AIOSession session = future.get();
		future.release();
		assertNotNull(session);

		assertTrue(session.isOpen());


		this.clientHandler.waitForOpened();
		if (this.clientHandler.cause != null) {
			fail(this.clientHandler.cause);
		}
		assertEquals(1, this.clientHandler.openedCount);

		this.serverHandler.waitForOpened();
		if (this.serverHandler.cause != null) {
			fail(this.serverHandler.cause);
		}
		assertEquals(1, this.serverHandler.openedCount);

		return session;
	}

	private final void endConnect() throws InterruptedException {
		this.clientHandler.waitForClosed();
		if (this.clientHandler.cause != null) {
			fail(this.clientHandler.cause);
		}
		assertEquals(1, this.clientHandler.closedCount);

		this.serverHandler.waitForClosed();
		if (this.serverHandler.cause != null) {
			fail(this.serverHandler.cause);
		}
		assertEquals(1, this.serverHandler.closedCount);
	}

	public final void testConnect1() {
		try {
			setUpReactor(1, true, false);
			for (int i = 0; i < 5; ++i) {
				this.serverHandler.reset();
				this.clientHandler.reset();

				this.acceptorObserver.reset();

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

	public final void testConnect2() {
		try {
			setUpReactor(2, true, false);
			for (int i = 0; i < 5; ++i) {
				this.serverHandler.reset();
				this.clientHandler.reset();

				this.acceptorObserver.reset();

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

	public final void testConnect3() {
		try {
			setUpReactor(3, true, false);
			for (int i = 0; i < 5; ++i) {
				this.serverHandler.reset();
				this.clientHandler.reset();

				this.acceptorObserver.reset();

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
		AIOSession serverSession = null;
		AIOSession clientSession = null;
		try {
			startConnect();

			serverSession = this.serverHandler.session;
			clientSession = this.clientHandler.session;

			this.clientHandler.session.close();

			endConnect();

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


	public final void testXInputOutputAct() {
		try {
			setUpReactor(3, true, false);

			for (int i = 0; i < 5; ++i) {
				this.serverHandler.reset();
				this.clientHandler.reset();

				this.acceptorObserver.reset();

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


			clientSession.shutdownOutput(false);


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

			endConnect();

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
