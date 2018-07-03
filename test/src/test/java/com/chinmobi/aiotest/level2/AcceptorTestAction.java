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

import com.chinmobi.aio.service.AIOConnectionAcceptor;

/**
 * @author <a href="mailto:yuzhaoping1970@gmail.com">Zhaoping Yu</a>
 *
 */
public final class AcceptorTestAction extends ServiceTestBase {

	private static final class Observer implements AIOConnectionAcceptor.Observer {

		private volatile int status;

		volatile Throwable cause;


		Observer() {
		}

		void reset() {
			this.status = 0;
			this.cause = null;
		}

		public final void aioAcceptorStarted(final AIOConnectionAcceptor acceptor) {
			synchronized (this) {
				this.status = 1;
				notify();
			}
		}

		public final void aioAcceptorAccepting(final AIOConnectionAcceptor acceptor) {
			synchronized (this) {
				this.status = 2;
				notify();
			}
		}

		public final void aioAcceptorStopped(final AIOConnectionAcceptor acceptor,
				final Object msgObj, final Throwable cause) {
			this.cause = cause;

			acceptor.setObserver(null);

			synchronized (this) {
				this.status = -1;
				notify();
			}
		}

		final void waitForStarted() throws InterruptedException {
			synchronized (this) {
				while (this.status == 0) {
					wait();
				}
			}
		}

		final void waitForAccepting() throws InterruptedException {
			synchronized (this) {
				while (this.status <= 1) {
					wait();
				}
			}
		}

		final void waitForStopped() throws InterruptedException {
			synchronized (this) {
				while (this.status > 0) {
					wait();
				}
			}
		}

	}


	private AIOConnectionAcceptor acceptor;

	private ServiceCallback serviceCallback;

	private final Observer observer;


	public AcceptorTestAction() {
		super();
		this.observer = new Observer();
	}


	@Override
	protected final void setUp(final String methodName) throws Exception {
		this.acceptor = this.aioRuntime.getConnectionAcceptor("TCP", 8080);

		this.observer.reset();
		this.acceptor.setObserver(this.observer);

		this.serviceCallback = new ServiceCallback("Server");
	}

	@Override
	protected final void tearDown() throws Exception {
		this.acceptor.stop();
	}

	/*
	 * Test methods
	 */

	public final void test0StartAndStop() {
		try {
			this.acceptor.start(this.serviceCallback);

			this.observer.waitForStarted();

			this.acceptor.stop();

			this.observer.waitForStopped();

			if (this.observer.cause != null) {
				fail(this.observer.cause);
			}

		} catch (Exception ex) {
			fail(ex);
		}
	}

	public final void test1StartAndStop() {
		try {
			this.acceptor.start(this.serviceCallback);

			this.observer.waitForAccepting();

			this.acceptor.stop();

			this.observer.waitForStopped();

			if (this.observer.cause != null) {
				fail(this.observer.cause);
			}

		} catch (Exception ex) {
			fail(ex);
		}
	}

}
