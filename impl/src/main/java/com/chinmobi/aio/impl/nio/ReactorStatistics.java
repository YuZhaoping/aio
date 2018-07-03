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
package com.chinmobi.aio.impl.nio;

import java.text.SimpleDateFormat;
import java.util.Date;

import com.chinmobi.aio.AIOReactorStatistics;
import com.chinmobi.aio.impl.Constants;

/**
 * @author <a href="mailto:yuzhaoping1970@gmail.com">Zhaoping Yu</a>
 *
 */
public final class ReactorStatistics implements AIOReactorStatistics {

	private final Reactor reactor;

	private volatile long startTime;


	ReactorStatistics(final Reactor reactor) {
		this.reactor = reactor;
		this.startTime = System.currentTimeMillis();
	}


	public final long getStartTime() {
		return this.startTime;
	}

	final void updateStartTime() {
		this.startTime = System.currentTimeMillis();
	}

	/*
	 * For sessions
	 */

	public final int getSessionActiveCount() {
		return this.reactor.sessionContext().getActiveSessionCount();
	}

	public final int getSessionLargestCount() {
		return this.reactor.sessionContext().getLargestSessionCount();
	}

	/*
	 * For threads
	 */

	public final int getThreadActiveCount() {
		return this.reactor.threadPool().getActiveCount();
	}

	public final int getThreadLargestPoolSize() {
		return this.reactor.threadPool().getLargestPoolSize();
	}

	public final int getThreadPoolSize() {
		return this.reactor.threadPool().getPoolSize();
	}


	public static final String toString(final long startTime) {
		final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		final Date date = new Date(startTime);

		return dateFormat.format(date);
	}

	public static final void toString(final AIOReactorStatistics stats,
			final StringBuilder builder, final String prefix) {
		builder.append(prefix).append("StartTime: ").append(toString(stats.getStartTime()));
		builder.append(prefix).append("SessionActiveCount: ").append(stats.getSessionActiveCount());
		builder.append(prefix).append("SessionLargestCount: ").append(stats.getSessionLargestCount());
		builder.append(prefix).append("ThreadActiveCount: ").append(stats.getThreadActiveCount());
		builder.append(prefix).append("ThreadLargestPoolSize: ").append(stats.getThreadLargestPoolSize());
		builder.append(prefix).append("ThreadPoolSize: ").append(stats.getThreadPoolSize());
	}

	@Override
	public final String toString() {
		final StringBuilder builder = new StringBuilder();

		builder.append("ReactorStatistics [");
		toString(this, builder, Constants.CRLF);
		builder.append(Constants.CRLF).append(']');

		return builder.toString();
	}

}
