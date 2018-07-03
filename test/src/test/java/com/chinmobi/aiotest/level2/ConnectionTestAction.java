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

import com.chinmobi.aio.service.AIOConnection;
import com.chinmobi.aio.service.AIOConnectionAcceptor;

/**
 * @author <a href="mailto:yuzhaoping1970@gmail.com">Zhaoping Yu</a>
 *
 */
public final class ConnectionTestAction extends ServiceTestBase {


	static final class ConnectCallback implements AIOConnection.Callback {

		volatile int status;
		volatile Throwable cause;


		ConnectCallback() {
			this.status = 0;
			this.cause = null;
		}


		public final void aioConnectInitiate(final AIOConnection connection) {
		}

		public final void aioConnectAccomplished(final AIOConnection connection) {
			synchronized (this) {
				this.status = 1;
				notify();
			}
		}

		public final void aioConnectTimeout(final AIOConnection connection) {
			synchronized (this) {
				this.status = 2;
				notify();
			}
		}

		public final void aioConnectFailed(final AIOConnection connection, final Throwable cause) {
			synchronized (this) {
				this.status = -1;
				this.cause = cause;
				notify();
			}
		}

		public final void aioConnectCancelled(final AIOConnection connection) {
			synchronized (this) {
				this.status = -1;
				notify();
			}
		}

		final void waitForConnected() throws InterruptedException {
			synchronized (this) {
				while (this.status == 0) {
					wait();
				}
			}
		}

	}


	private AIOConnectionAcceptor acceptor;


	public ConnectionTestAction() {
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

	public final void test0Connect() {
		try {
			final AIOConnection connection = this.aioRuntime.createConnection("TCP", null);

			assertFalse(connection.isOpen());

			connection.setServiceCallback(new ServiceCallback("Client"));
			connection.remoteEndpoint().set("localhost", 8080);

			assertFalse(connection.connect());

			assertTrue(connection.waitForConnected());

			assertTrue(connection.isOpen());

			connection.close();

			assertFalse(connection.isOpen());

		} catch (Exception ex) {
			fail(ex);
		}
	}

	public final void test1Connect() {
		try {
			final AIOConnection connection = this.aioRuntime.createConnection("TCP", null);

			assertFalse(connection.isOpen());

			connection.setServiceCallback(new ServiceCallback("Client"));
			connection.remoteEndpoint().set("localhost", 8080);

			final ConnectCallback callback = new ConnectCallback();

			assertFalse(connection.connect(callback));

			callback.waitForConnected();
			if (callback.cause != null) {
				fail(callback.cause);
			}
			assertEquals(1, callback.status);

			assertTrue(connection.isOpen());

			connection.close();

			assertFalse(connection.isOpen());

		} catch (Exception ex) {
			fail(ex);
		}
	}

}
