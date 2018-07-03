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

import java.io.FileInputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManagerFactory;

import com.chinmobi.aio.AIOAcceptor;
import com.chinmobi.aio.AIOFuture;
import com.chinmobi.aio.AIOInetEndpoint;
import com.chinmobi.aio.AIOSession;
import com.chinmobi.aio.scheme.AIOSecurityScheme;
import com.chinmobi.aio.scheme.AIOSocketScheme;
import com.chinmobi.logging.Logger;

/**
 * @author <a href="mailto:yuzhaoping1970@gmail.com">Zhaoping Yu</a>
 *
 */
public final class SecurityTransportTestAction extends TransportTestsBase {

	private static abstract class SecurityScheme extends AIOSocketScheme implements AIOSecurityScheme {

		private final Logger logger;

		protected final boolean isNeedClientAuth;

		private int handshakStatus;


		protected SecurityScheme(final Logger logger, final boolean isNeedClientAuth) {
			super("TLS");

			this.logger = logger;
			this.isNeedClientAuth = isNeedClientAuth;
			this.handshakStatus = 0;
		}

		final void init() {
			synchronized (this) {
				this.handshakStatus = 0;
			}
		}

		@Override
		public final AIOSecurityScheme getSecurityScheme() {
			return this;
		}

		public final SSLContext getSSLContext() throws IOException, GeneralSecurityException {
			// Create/initialize the SSLContext with key material

			// First initialize the key and trust material.
			final char[] passphrase = getKeyStorePassphrase();

			final KeyStore ksKeys = getKeyStore(passphrase);

			// KeyManager's decide which key material to use.
			final KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
			kmf.init(ksKeys, passphrase);

			// TrustManager's decide whether to allow connections.
			final TrustManagerFactory tmf = getTrustManagerFactory(passphrase);

			final SSLContext sslContext = SSLContext.getInstance("TLS");
			sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);

			return sslContext;
		}

		public final void initalizeSSLEngine(final SSLEngine sslEngine) throws SSLException {
			if (!sslEngine.getUseClientMode()) {
				//sslEngine.setWantClientAuth(true);
				sslEngine.setNeedClientAuth(this.isNeedClientAuth);
			}

			if (this.logger != null && this.logger.isDebugEnabled()) {
				this.logger.debug().writeln().write(currentThreadId()).
					write("\t========").write(toString()).writeln(": Initalized.").flush();
			}
		}

		public final void verifySSLSessionHandshaked(final SSLSession sslSession) throws SSLException {
			synchronized (this) {
				this.handshakStatus = 1;
				notify();
			}

			if (this.logger != null && this.logger.isDebugEnabled()) {
				this.logger.debug().writeln().write(currentThreadId()).
					write("\t========").write(toString()).writeln(": Handshaked.").flush();
			}
		}

		public final void shuttingDownSSLEngine(final SSLEngine sslEngine) {
			if (this.logger != null && this.logger.isDebugEnabled()) {
				this.logger.debug().writeln().write(currentThreadId()).
					write("\t========").write(toString()).writeln(": Shutting down...").flush();
			}
		}

		public final void closedSSLEngine(final SSLEngine sslEngine) {
			synchronized (this) {
				if (this.handshakStatus == 0) {
					this.handshakStatus = -1;
					notify();
				}
			}

			if (this.logger != null && this.logger.isDebugEnabled()) {
				this.logger.debug().writeln().write(currentThreadId()).
					write("\t========").write(toString()).writeln(": Closed.").flush();
			}
		}


		private final KeyStore getKeyStore(final char[] passphrase) throws GeneralSecurityException, IOException {
			final String fileName = getKeyStoreFileName();
			if (fileName != null) {
				final KeyStore ksKeys = KeyStore.getInstance("JKS");
				ksKeys.load(new FileInputStream(fileName), passphrase);
				return ksKeys;
			}
			return null;
		}

		private final TrustManagerFactory getTrustManagerFactory(final char[] passphrase)
				throws GeneralSecurityException, IOException {
			final KeyStore ksTrust = getTrustStore(passphrase);

			if (ksTrust != null) {
				final TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
				tmf.init(ksTrust);

				return tmf;
			} else {
				return BogusTrustManagerFactory.getInstance();
			}
		}

		private final KeyStore getTrustStore(final char[] passphrase) throws GeneralSecurityException, IOException {
			final String fileName = getTrustStoreFileName();
			if (fileName != null) {
				final KeyStore ksTrust = KeyStore.getInstance("JKS");
				ksTrust.load(new FileInputStream(fileName), passphrase);
				return ksTrust;
			}
			return null;
		}

		protected abstract char[] getKeyStorePassphrase();

		protected abstract String getKeyStoreFileName();

		protected abstract String getTrustStoreFileName();


		final void waitForHandshaked() throws InterruptedException {
			synchronized (this) {
				while (this.handshakStatus == 0) {
					wait();
				}
			}
		}

		private static final String currentThreadId() {
			final StringBuilder builder = new StringBuilder();

			builder.append('[');
			builder.append(Long.toString(Thread.currentThread().getId(), 16));
			builder.append("] ");

			return builder.toString();
		}

	}

	private static final class ServerSecurityScheme extends SecurityScheme {

		public ServerSecurityScheme(final Logger logger, final boolean isNeedClientAuth) {
			super(logger, isNeedClientAuth);
		}

		@Override
		protected final char[] getKeyStorePassphrase() {
			return "ats123456".toCharArray();
		}

		@Override
		protected final String getKeyStoreFileName() {
			return "../tests/aiotstsvrkeystore";
		}

		@Override
		protected final String getTrustStoreFileName() {
			return "../tests/aiotstsvrtruststore";
		}

		@Override
		public final String toString() {
			return "ServerTLS";
		}

	}

	private static final class ClientSecurityScheme extends SecurityScheme {

		private final boolean isTrustEverything;

		public ClientSecurityScheme(final Logger logger,
				final boolean isNeedClientAuth, final boolean isTrustEverything) {
			super(logger, isNeedClientAuth);
			this.isTrustEverything = isTrustEverything;
		}

		@Override
		protected final char[] getKeyStorePassphrase() {
			return "atc123456".toCharArray();
		}

		@Override
		protected final String getKeyStoreFileName() {
			return this.isNeedClientAuth ? "../tests/aiotstcltkeystore" : null;
		}

		@Override
		protected final String getTrustStoreFileName() {
			return this.isTrustEverything ? null : "../tests/aiotstclttruststore";
		}

		@Override
		public final String toString() {
			return "ClientTLS";
		}

	}


	public SecurityTransportTestAction() {
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

	private final AIOAcceptor startAcceptor(final boolean isNeedClientAuth)
			throws InterruptedException {
		final ServerSecurityScheme scheme = new ServerSecurityScheme(this.reactor.logger(), isNeedClientAuth);
		final AIOInetEndpoint endpoint = new AIOInetEndpoint(8282);

		final AIOAcceptor acceptor = this.reactor.getAcceptor(scheme, endpoint);
		assertNotNull(acceptor);

		if (acceptor.isActive()) {
			++this.acceptorObserver.startedCount;
			++this.acceptorObserver.acceptingCount;
		}

		acceptor.setObserver(this.acceptorObserver);
		acceptor.start(this.serverHandler);

		//logStep("acceptorObserver.waitForAccepting");
		this.acceptorObserver.waitForAccepting();
		if (this.acceptorObserver.cause != null) {
			fail(this.acceptorObserver.cause);
		}

		return acceptor;
	}

	private final AIOSession startConnect(final boolean waitForHandshaked,
			final boolean isNeedClientAuth, final boolean isClientTrustEverything)
			throws InterruptedException, ExecutionException {

		final AIOAcceptor acceptor = startAcceptor(isNeedClientAuth);
		final ServerSecurityScheme serverScheme = (ServerSecurityScheme)acceptor.getTransportScheme();

		final ClientSecurityScheme clientScheme = new ClientSecurityScheme(this.reactor.logger(),
				isNeedClientAuth, isClientTrustEverything);

		serverScheme.init();
		clientScheme.init();

		final AIOInetEndpoint remote = new AIOInetEndpoint("localhost", 8282);
		final AIOFuture<AIOSession> future = this.reactor.getConnector().connect(
				clientScheme, remote, null,
				this.clientHandler,
				null, 1000, TimeUnit.MILLISECONDS, null);

		assertNotNull(future);

		//logStep("connect.future.get");
		final AIOSession session = future.get();
		future.release();
		assertNotNull(session);

		assertTrue(session.isOpen());


		//logStep("clientHandler.waitForOpened");
		this.clientHandler.waitForOpened();
		if (this.clientHandler.cause != null) {
			fail(this.clientHandler.cause);
		}
		assertEquals(1, this.clientHandler.openedCount);

		//logStep("serverHandler.waitForOpened");
		this.serverHandler.waitForOpened();
		if (this.serverHandler.cause != null) {
			fail(this.serverHandler.cause);
		}
		assertEquals(1, this.serverHandler.openedCount);

		if (waitForHandshaked) {
			//logStep("clientScheme.waitForHandshaked");
			clientScheme.waitForHandshaked();
			//logStep("serverScheme.waitForHandshaked");
			serverScheme.waitForHandshaked();
		}

		return session;
	}

	private final void endConnect() throws InterruptedException {
		//logStep("clientHandler.waitForClosed");
		this.clientHandler.waitForClosed();
		if (this.clientHandler.cause != null) {
			fail(this.clientHandler.cause);
		}
		assertEquals(1, this.clientHandler.closedCount);

		//logStep("serverHandler.waitForClosed");
		this.serverHandler.waitForClosed();
		if (this.serverHandler.cause != null) {
			fail(this.serverHandler.cause);
		}
		assertEquals(1, this.serverHandler.closedCount);
	}

	private final void doTestConnect(final boolean waitForHandshaked,
			final boolean isNeedClientAuth, final boolean isClientTrustEverything) {
		try {
			startConnect(waitForHandshaked, isNeedClientAuth, isClientTrustEverything);

			if (this.clientHandler.session != null) {
				this.clientHandler.session.close();
			}

			endConnect();

		} catch (Exception ex) {
			fail(ex);
		}
	}

	public final void test0Connect() {
		try {
			setUpReactor(3, true, false);
			for (int i = 0; i < 5; ++i) {
				this.serverHandler.reset();
				this.clientHandler.reset();

				this.acceptorObserver.reset();

				doTestConnect(false, true, false);

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

	public final void test1Connect() {
		try {
			setUpReactor(3, true, false);
			for (int i = 0; i < 5; ++i) {
				this.serverHandler.reset();
				this.clientHandler.reset();

				this.acceptorObserver.reset();

				doTestConnect(true, true, false);

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

	public final void test2Connect() {
		try {
			setUpReactor(3, true, false);
			for (int i = 0; i < 1; ++i) {
				this.serverHandler.reset();
				this.clientHandler.reset();

				this.acceptorObserver.reset();

				doTestConnect(true, false, false);

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

	public final void test3Connect() {
		try {
			setUpReactor(3, true, false);
			for (int i = 0; i < 1; ++i) {
				this.serverHandler.reset();
				this.clientHandler.reset();

				this.acceptorObserver.reset();

				doTestConnect(true, false, true);

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

	public final void testX0InputOutputAct() {
		try {
			setUpReactor(3, true, false);

			for (int i = 0; i < 5; ++i) {
				this.serverHandler.reset();
				this.clientHandler.reset();

				this.acceptorObserver.reset();

				doTestAct(false, 100);

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

	public final void testX1InputOutputAct() {
		try {
			setUpReactor(3, true, false);

			for (int i = 0; i < 5; ++i) {
				this.serverHandler.reset();
				this.clientHandler.reset();

				this.acceptorObserver.reset();

				doTestAct(true, 100);

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

	private final void doTestAct(final boolean waitForHandshaked, final long transferCount) {
		AIOSession serverSession = null;
		AIOSession clientSession = null;
		try {
			startConnect(waitForHandshaked, true, false);

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


			//logStep("outputHelper.waitForDone");
			outputHelper.waitForDone();
			if (outputHelper.cause != null) {
				fail(outputHelper.cause);
			}
			assertEquals(0, outputHelper.remainingCount);

			//logStep("serverHelper.waitForDone");
			serverHelper.waitForDone(transferCount);
			if (serverHelper.cause != null) {
				fail(serverHelper.cause);
			}

			if (transferCount != serverHelper.receivedCount) {
				this.out().println("ServerInputHelper.doneStatus: " + serverHelper.doneStatus);
				this.out().println(serverSession.toString());
				this.out().println(clientSession.toString());
			}
			assertEquals(transferCount, serverHelper.receivedCount);


			serverSession.shutdownOutput(false);


			//logStep("clientHelper.waitForDone");
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
