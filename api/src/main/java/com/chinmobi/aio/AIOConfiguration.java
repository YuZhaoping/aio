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
package com.chinmobi.aio;

/**
 * @author <a href="mailto:yuzhaoping1970@gmail.com">Zhaoping Yu</a>
 *
 */
public final class AIOConfiguration {

	private volatile int reactorGroupSize;

	private volatile int coreThreadPoolSize;
	private volatile int maximumThreadPoolSize;
	private volatile long idleThreadKeepAliveTime;

	private volatile int threadPriority;
	private volatile boolean isThreadDaemon;

	private volatile boolean interestOpsQueueing;

	private volatile String loggerName;


	public AIOConfiguration() {
		this.reactorGroupSize = 1;

		this.coreThreadPoolSize = 1;
		this.maximumThreadPoolSize = -1;
		this.idleThreadKeepAliveTime = 60000;

		this.threadPriority = Thread.NORM_PRIORITY;
		this.isThreadDaemon = true;

		this.interestOpsQueueing = false;

		this.loggerName = "chinmobi.aio";
	}



	public final int getReactorGroupSize() {
		return this.reactorGroupSize;
	}

	public final void setReactorGroupSize(final int reactorGroupSize) {
		this.reactorGroupSize = reactorGroupSize;
	}


	public final int getCoreThreadPoolSize() {
		return this.coreThreadPoolSize;
	}

	public final void setCoreThreadPoolSize(final int coreThreadPoolSize) {
		this.coreThreadPoolSize = coreThreadPoolSize;
	}

	public final int getMaximumThreadPoolSize() {
		return this.maximumThreadPoolSize;
	}

	public final void setMaximumThreadPoolSize(final int maximumThreadPoolSize) {
		this.maximumThreadPoolSize = maximumThreadPoolSize;
	}

	public final long getIdleThreadKeepAliveTime() {
		return this.idleThreadKeepAliveTime;
	}

	public final void setIdleThreadKeepAliveTime(final long keepAliveTime) {
		this.idleThreadKeepAliveTime = keepAliveTime;
	}


	public final int getThreadPriority() {
		return this.threadPriority;
	}

	public final void setThreadPriority(final int threadPriority) {
		this.threadPriority = threadPriority;
	}

	public final boolean isThreadDaemon() {
		return this.isThreadDaemon;
	}

	public final void setThreadDaemon(final boolean isThreadDaemon) {
		this.isThreadDaemon = isThreadDaemon;
	}


	public final boolean isInterestOpsQueueing() {
		return this.interestOpsQueueing;
	}

	public final void setInterestOpsQueueing(final boolean on) {
		this.interestOpsQueueing = on;
	}


	public final String getLoggerName() {
		return this.loggerName;
	}

	public final void setLoggerName(final String loggerName) {
		this.loggerName = loggerName;
	}

}
