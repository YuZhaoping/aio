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
package com.chinmobi.aiotest.level2;

import java.util.concurrent.TimeUnit;

import com.chinmobi.aio.AIOSession;
import com.chinmobi.aio.pool.AIOConnectionPool;
import com.chinmobi.aio.service.AIOConnection;
import com.chinmobi.aio.service.AIOConnectionAcceptor;

/**
 * @author <a href="mailto:yuzhaoping1970@gmail.com">Zhaoping Yu</a>
 *
 */
public final class ConnectionPoolTestAction extends ServiceTestBase {

	private AIOConnectionAcceptor acceptor;


	public ConnectionPoolTestAction() {
		super();
	}


	@Override
	protected final void setUp(final String methodName) throws Exception {
		this.acceptor = this.aioRuntime.getConnectionAcceptor("TCP", 8080);

		this.acceptor.start(new ServiceCallback("Server"));
		this.acceptor.waitForAccepting();
	}

	@Override
	protected final void tearDown() throws Exception {
		this.acceptor.stop();
		this.acceptor.waitForStopped();
	}

	/*
	 * Test methods
	 */

	public final void test0LeaseAndRelease() {
		try {
			final AIOConnectionPool connPool = this.aioRuntime.getConnectionPool();

			final AIOConnection connection = connPool.leaseConnection("TCP", "localhost", 8080);

			assertFalse(connection.isOpen());

			assertTrue(connection.transportScheme().name().equalsIgnoreCase("TCP"));
			assertTrue(connection.remoteEndpoint().equals("localhost", 8080));

			connection.release();

			final AIOConnection conn2 = connPool.leaseConnection("TCP", "localhost", 8080);

			assertTrue(connection == conn2);

		} catch (Exception ex) {
			fail(ex);
		}
	}

	public final void test1LeaseAndRelease() {
		try {
			final AIOConnectionPool connPool = this.aioRuntime.getConnectionPool();

			final AIOConnection connection = connPool.leaseConnection("TCP", "localhost", 8080);

			assertFalse(connection.isOpen());

			connection.setServiceCallback(new ServiceCallback("Client"));

			connection.connect();
			connection.waitForConnected();

			assertTrue(connection.isOpen());

			connection.release();

			final AIOConnection conn2 = connPool.leaseConnection("TCP", "localhost", 8080);
			assertTrue(conn2.isOpen());

			conn2.release();

			conn2.close();

			final AIOConnection conn3 = connPool.leaseConnection("TCP", "localhost", 8080);
			assertFalse(conn3.isOpen());

			assertTrue(conn2 == conn3);

		} catch (Exception ex) {
			fail(ex);
		}
	}

	public final void test2LeaseAndRelease() {
		AIOConnection connection = null;
		AIOSession serverSession = null;
		try {
			final AIOConnectionPool connPool = this.aioRuntime.getConnectionPool();

			connection = connPool.leaseConnection("TCP", "localhost", 8080);

			assertFalse(connection.isOpen());

			final ServiceCallback clientCallback = new ServiceCallback("Client");
			connection.setServiceCallback(clientCallback);

			assertTrue(this.acceptor.isAccepting());

			final ConnectionTestAction.ConnectCallback connCallback = new ConnectionTestAction.ConnectCallback();

			connection.connect(connCallback);
			connection.waitForConnected();

			if (!connection.isOpen()) {
				final String message = "Connect status: " + connCallback.status;
				if (connCallback.cause != null) {
					fail(message, connCallback.cause);
				} else {
					fail(message);
				}
			}
			assertTrue(connection.isOpen());

			connection.release();

			// Only for test
			connection.setServiceCallback(clientCallback);

			final ServiceCallback serverCallback = (ServiceCallback)this.acceptor.getServiceCallbackFactory();
			serverCallback.waitForOpened();
			serverSession = serverCallback.service().session();
			serverCallback.close();
			serverCallback.waitForClosed();

			clientCallback.waitForOpened();
			clientCallback.waitForClosed();

			final AIOConnection conn2 = connPool.leaseConnection("TCP", "localhost", 8080);

			assertTrue(conn2 == connection);

			if (conn2.isOpen()) {
				this.out().println(conn2.toString());
			}
			assertFalse(conn2.isOpen());
		} catch (Exception ex) {
			if (connection != null) {
				this.out().println(connection.toString());
			}
			if (serverSession != null) {
				this.out().println(serverSession.toString());
			}

			fail(ex);
		}
	}

	public final void test3LeaseAndRelease() {
		try {
			final AIOConnectionPool connPool = this.aioRuntime.getConnectionPool();
			connPool.setIdleConnectionTimeToLive(50, TimeUnit.MILLISECONDS);

			final AIOConnection connection = connPool.leaseConnection("TCP", "localhost", 8080);

			assertFalse(connection.isOpen());

			final ServiceCallback clientCallback = new ServiceCallback("Client");
			connection.setServiceCallback(clientCallback);

			final ConnectionTestAction.ConnectCallback connCallback = new ConnectionTestAction.ConnectCallback();

			connection.connect(connCallback);
			connection.waitForConnected();

			if (!connection.isOpen()) {
				final String message = "Connect status: " + connCallback.status;
				if (connCallback.cause != null) {
					fail(message, connCallback.cause);
				} else {
					fail(message);
				}
			}
			assertTrue(connection.isOpen());
			clientCallback.waitForOpened();

			connection.release();

			// Only for test
			connection.setServiceCallback(clientCallback);

			clientCallback.waitForClosed();

			final AIOConnection conn2 = connPool.leaseConnection("TCP", "localhost", 8080);
			assertFalse(conn2.isOpen());

			assertTrue(conn2 == connection);

		} catch (Exception ex) {
			fail(ex);
		}
	}

}
