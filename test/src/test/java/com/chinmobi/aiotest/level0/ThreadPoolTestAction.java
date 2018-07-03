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
package com.chinmobi.aiotest.level0;

import com.chinmobi.aio.impl.nio.ThreadPool;
import com.chinmobi.testapp.BaseTestAction;

/**
 * @author <a href="mailto:yuzhaoping1970@gmail.com">Zhaoping Yu</a>
 *
 */
public final class ThreadPoolTestAction extends BaseTestAction
	implements ThreadPool.Helper, Runnable {

	private volatile int coreThreadPoolSize;

	private volatile int runCount;


	public ThreadPoolTestAction() {
		super();
		this.coreThreadPoolSize = 1;
		this.runCount = 0;
	}


	public final void handleUncaughtException(final Throwable ex) {
	}

	public final int getCoreThreadPoolSize() {
		return this.coreThreadPoolSize;
	}

	public final int getMaximumThreadPoolSize() {
		return 4;
	}

	public final long getIdleThreadKeepAliveTime() {
		return 60000;
	}

	public final int getThreadPriority() {
		return Thread.NORM_PRIORITY;
	}

	public final boolean isThreadDaemon() {
		return true;
	}

	public final void run() {
		++this.runCount;
	}


	/*
	 * Test methods
	 */

	public final void testStartAndShutdown() {
		final ThreadPool threadPool = new ThreadPool(this);

		assertFalse(threadPool.isShutdown());
		assertEquals(0, threadPool.getPoolSize());

		this.coreThreadPoolSize = 3;
		threadPool.start();

		assertFalse(threadPool.isShutdown());
		assertEquals(3, threadPool.getPoolSize());

		threadPool.shutdown(true);

		assertTrue(threadPool.isShutdown());
		assertEquals(0, threadPool.getPoolSize());
	}

	public final void testExecute0() {
		final ThreadPool threadPool = new ThreadPool(this);

		this.coreThreadPoolSize = 1;
		threadPool.start();

		this.runCount = 0;

		for (int i = 0; i < 9; ++i) {
			threadPool.execute((Runnable)this, false);
		}

		threadPool.shutdown(true);
		assertEquals(9, this.runCount);

		assertEquals(getCoreThreadPoolSize(), threadPool.getLargestPoolSize());
	}

	public final void testExecute1() {
		final ThreadPool threadPool = new ThreadPool(this);

		this.coreThreadPoolSize = 1;
		threadPool.start();

		this.runCount = 0;

		for (int i = 0; i < 9; ++i) {
			threadPool.execute((Runnable)this, true);
		}

		threadPool.shutdown(true);
		assertEquals(9, this.runCount);

		assertTrue(threadPool.getLargestPoolSize() > getCoreThreadPoolSize());
		assertTrue(threadPool.getLargestPoolSize() <= getMaximumThreadPoolSize());
	}

}
