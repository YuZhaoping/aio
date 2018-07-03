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
import com.chinmobi.aio.scheme.AIODatagramScheme;

/**
 * @author <a href="mailto:yuzhaoping1970@gmail.com">Zhaoping Yu</a>
 *
 */
public final class DatagramTransportTestAction extends TransportTestsBase {


	public DatagramTransportTestAction() {
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

	public final void testAccept0() {
		try {
			setUpReactor(2, true, false);

			final AIODatagramScheme scheme = new AIODatagramScheme();
			final AIOInetEndpoint endpoint = new AIOInetEndpoint(8181);

			final AIOAcceptor acceptor = this.reactor.getAcceptor(scheme, endpoint);
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

		} catch (Exception ex) {
			fail(ex);
		} finally {
			tearDownReactor();
		}
	}

	private final AIOAcceptor startAccept() throws InterruptedException {
		final AIODatagramScheme scheme = new AIODatagramScheme();
		final AIOInetEndpoint endpoint = new AIOInetEndpoint(8181);

		final AIOAcceptor acceptor = this.reactor.getAcceptor(scheme, endpoint);
		assertNotNull(acceptor);

		acceptor.setObserver(this.acceptorObserver);
		acceptor.start(this.serverHandler);

		try {

			this.acceptorObserver.waitForStarted();
			this.acceptorObserver.waitForAccepting();
			if (this.acceptorObserver.cause != null) {
				fail(this.acceptorObserver.cause);
			}
			if (0 != this.acceptorObserver.stoppedCount) {
				if (this.acceptorObserver.msg != null) {
					this.out().println(this.acceptorObserver.msg);
				}
			}
			assertEquals(0, this.acceptorObserver.stoppedCount);

			this.serverHandler.waitForOpened();
			if (this.serverHandler.cause != null) {
				fail(this.serverHandler.cause);
			}
			assertEquals(1, this.serverHandler.openedCount);

		} catch (InterruptedException ex) {
			if (this.acceptorObserver.msg != null) {
				this.out().println(this.acceptorObserver.msg);
			} else {
				this.out().println(acceptor.toString());
			}
			if (this.acceptorObserver.cause != null) {
				fail(this.acceptorObserver.cause);
			}

			throw ex;
		}

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

	private final AIOSession startConnect() throws InterruptedException, ExecutionException {
		final AIODatagramScheme scheme = new AIODatagramScheme();
		final AIOInetEndpoint remote = new AIOInetEndpoint("localhost", 8181);

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

		return session;
	}

	private final void endConnect() throws InterruptedException {
		this.clientHandler.waitForClosed();
		if (this.clientHandler.cause != null) {
			fail(this.clientHandler.cause);
		}
		assertEquals(1, this.clientHandler.closedCount);
	}

	public final void testConnect() {
		try {
			setUpReactor(2, true, false);
			for (int i = 0; i < 5; ++i) {
				this.serverHandler.reset();
				this.clientHandler.reset();

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


			final ServerInputHelper serverHelper = new ServerInputHelper(this.serverHandler);
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

			//serverHelper.waitForDone(transferCount);
			//if (serverHelper.cause != null) {
			//	fail(serverHelper.cause);
			//}
			//assertEquals(transferCount, serverHelper.receivedCount);

			clientHelper.waitForDone(transferCount);
			if (clientHelper.cause != null) {
				fail(clientHelper.cause);
			}
			assertEquals(transferCount, clientHelper.receivedCount);


			clientSession.close();
			serverSession.close();


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
