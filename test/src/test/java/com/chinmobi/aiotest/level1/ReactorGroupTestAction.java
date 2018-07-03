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

import com.chinmobi.aio.AIOConfiguration;
import com.chinmobi.aio.AIOReactor;
import com.chinmobi.aio.impl.nio.ReactorGroup;
import com.chinmobi.testapp.BaseTestAction;

/**
 * @author <a href="mailto:yuzhaoping1970@gmail.com">Zhaoping Yu</a>
 *
 */
public final class ReactorGroupTestAction extends BaseTestAction {

	private static final class Helper implements AIOReactor.Observer {

		private volatile int status;

		volatile int testCase;

		volatile Throwable cause;


		Helper() {
			this.status = 0;
			this.testCase = 0;
			this.cause = null;
		}

		final void reset() {
			this.status = 0;
			this.testCase = 0;
		}

		final void waitForStarted() throws InterruptedException {
			synchronized (this) {
				while (this.status == 0) {
					wait();
				}
			}
		}

		final boolean isStarted() {
			return (this.status > 0);
		}

		final void waitForStopped() throws InterruptedException {
			synchronized (this) {
				while (this.status >= 0) {
					wait();
				}
			}
		}

		final boolean isStopped() {
			return (this.status < 0);
		}

		public final void onAIOReactorStarted(final AIOReactor reactor) {
			if (reactor.toGroup() == null) {
				return;
			}

			switch (this.testCase) {
			case -1:
				notifyStarted();
				reactor.stop(true);
				return;

			case  2:
				reactor.configuration().setReactorGroupSize(3);

			case  1:
				try {
					reactor.start();
				} catch (Exception ex) {
					ReactorTestAction.fail(ex);
				}
				break;

			default:
				break;
			}
			notifyStarted();
		}

		private final void notifyStarted() {
			synchronized (this) {
				this.status = 1;
				notify();
			}
		}

		public final void onAIOReactorStopped(final AIOReactor reactor, final Throwable cause) {
			if (reactor.toGroup() == null) {
				return;
			}

			synchronized (this) {
				this.status = -1;
				this.cause = cause;
				notify();
			}
		}

	}


	private ReactorGroup reactor;

	private final Helper helper;

	public ReactorGroupTestAction() {
		super();
		this.helper = new Helper();
	}


	@Override
	protected final void setUp(final String methodName) throws Exception {
		this.reactor = new ReactorGroup(new AIOConfiguration());

		this.reactor.configuration().setReactorGroupSize(3);

		//this.reactor.logger().enableLevel(Logger.Level.DEBUG);

		this.helper.reset();
		this.reactor.setObserver(this.helper);

		this.reactor.logger().writeln().write("\tsetUp:\t").writeln(methodName).flush();
	}

	@Override
	protected final void tearDown() throws Exception {
		this.reactor.setObserver(null);
		this.reactor.stop(false);
		this.reactor = null;
	}

	/*
	 * Test methods
	 */

	public final void test0StartAndStop() {
		try {
			assertFalse(this.reactor.isActive());

			this.reactor.start();

			assertTrue(this.reactor.isActive());

			this.helper.waitForStarted();

			assertTrue(this.helper.isStarted());

			this.reactor.stop(false);

			assertFalse(this.reactor.isActive());

			this.helper.waitForStopped();

			assertTrue(this.helper.isStopped());

		} catch (Exception ex) {
			fail(ex);
		}
	}

	public final void test1StartAndStop() {
		try {
			assertFalse(this.reactor.isActive());

			this.reactor.start();

			assertTrue(this.reactor.isActive());

			this.helper.waitForStarted();

			if (this.helper.cause != null) {
				fail(this.helper.cause);
			}
			assertTrue(this.helper.isStarted());

			this.reactor.stop(true);

			assertFalse(this.reactor.isActive());

			this.helper.waitForStopped();

			assertTrue(this.helper.isStopped());

		} catch (Exception ex) {
			fail(ex);
		}
	}

	public final void test2StartAndStop() {
		try {
			assertFalse(this.reactor.isActive());

			this.reactor.start();

			assertTrue(this.reactor.isActive());

			this.reactor.stop(true);

			assertFalse(this.reactor.isActive());

			if (this.helper.isStarted()) {
				this.helper.waitForStopped();

				assertTrue(this.helper.isStopped());
			}

		} catch (Exception ex) {
			fail(ex);
		}
	}

	public final void test3StartAndStop() {
		try {
			this.helper.testCase = -1;

			assertFalse(this.reactor.isActive());

			this.reactor.start();

			this.helper.waitForStopped();

			assertFalse(this.reactor.isActive());

			assertTrue(this.helper.isStopped());

		} catch (Exception ex) {
			fail(ex);
		}
	}

	public final void test4StartAndStop() {
		try {
			this.helper.testCase = 1;

			assertFalse(this.reactor.isActive());

			this.reactor.start();
			this.reactor.start();

			assertTrue(this.reactor.isActive());

			this.helper.waitForStarted();

			assertTrue(this.helper.isStarted());

			this.reactor.stop(false);

			assertFalse(this.reactor.isActive());

			this.reactor.stop(true);

			this.helper.waitForStopped();

			assertTrue(this.helper.isStopped());

		} catch (Exception ex) {
			fail(ex);
		}
	}

	public final void test5StartAndStop() {
		try {
			assertFalse(this.reactor.isActive());

			this.reactor.configuration().setReactorGroupSize(1);
			this.reactor.start();

			assertTrue(this.reactor.isActive());

			this.reactor.configuration().setReactorGroupSize(3);
			this.reactor.start();

			assertTrue(this.reactor.isActive());

			this.helper.waitForStarted();

			assertTrue(this.helper.isStarted());

			this.reactor.stop(true);

			assertFalse(this.reactor.isActive());

			this.helper.waitForStopped();

			assertTrue(this.helper.isStopped());

		} catch (Exception ex) {
			fail(ex);
		}
	}

	public final void test6StartAndStop() {
		try {
			this.helper.testCase = 2;

			assertFalse(this.reactor.isActive());

			this.reactor.configuration().setReactorGroupSize(1);
			this.reactor.start();

			assertTrue(this.reactor.isActive());

			this.helper.waitForStarted();

			assertTrue(this.helper.isStarted());

			this.reactor.stop(true);

			assertFalse(this.reactor.isActive());

			this.helper.waitForStopped();

			assertTrue(this.helper.isStopped());

		} catch (Exception ex) {
			fail(ex);
		}
	}

}
