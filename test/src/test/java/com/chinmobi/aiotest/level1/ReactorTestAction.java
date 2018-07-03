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

import java.nio.channels.Selector;

import com.chinmobi.aio.AIOConfiguration;
import com.chinmobi.aio.AIOReactor;
import com.chinmobi.aio.impl.nio.Reactor;
import com.chinmobi.app.action.ActionContext;
import com.chinmobi.logging.Logger;
import com.chinmobi.testapp.BaseTestAction;

/**
 * @author <a href="mailto:yuzhaoping1970@gmail.com">Zhaoping Yu</a>
 *
 */
public final class ReactorTestAction extends BaseTestAction {


	private static final class Helper implements AIOReactor.Observer {

		private volatile int status;

		volatile int testCase;


		Helper() {
			this.status = 0;
			this.testCase = 0;
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
			synchronized (this) {
				this.status = 1;
				notify();
			}

			switch (this.testCase) {
			case -1:
				reactor.stop(true);
				break;

			case  1:
				try {
					reactor.start();
				} catch (Exception ex) {
					ReactorTestAction.fail(ex);
				}
				break;

			default: break;
			}
		}

		public final void onAIOReactorStopped(final AIOReactor reactor, final Throwable cause) {
			synchronized (this) {
				this.status = -1;
				notify();
			}
		}

	}


	private Reactor reactor;

	private final Helper helper;


	public ReactorTestAction() {
		super();
		this.helper = new Helper();
	}


	@Override
	public final void init(final ActionContext context) {
		super.init(context);
	}

	@Override
	public final void destroy() {
		super.destroy();
	}

	@Override
	protected final void setUp(final String methodName) throws Exception {
		this.reactor = new Reactor(Selector.open(), new AIOConfiguration());

		this.reactor.logger().enableLevel(Logger.Level.OFF);

		this.helper.reset();
		this.reactor.setObserver(this.helper);

		this.reactor.logger().writeln().flush();
	}

	@Override
	protected final void tearDown() throws Exception {
		this.reactor.stop(false);
		this.reactor = null;
	}

	/*
	 * Test methods
	 */

	public final void testStartAndStop0() {
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

	public final void testStartAndStop1() {
		try {
			assertFalse(this.reactor.isActive());

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

	public final void testStartAndStop2() {
		try {
			assertFalse(this.reactor.isActive());

			this.reactor.start();

			assertTrue(this.reactor.isActive());

			this.reactor.stop(true);

			assertFalse(this.reactor.isActive());

			assertFalse(this.helper.isStopped());

		} catch (Exception ex) {
			fail(ex);
		}
	}

	public final void testStartAndStop3() {
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

	public final void testStartAndStop4() {
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

}
