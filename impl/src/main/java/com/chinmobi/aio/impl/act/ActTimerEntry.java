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
package com.chinmobi.aio.impl.act;

import com.chinmobi.aio.act.AIOActResult;
import com.chinmobi.aio.impl.util.TimerEntry;

/**
 * @author <a href="mailto:yuzhaoping1970@gmail.com">Zhaoping Yu</a>
 *
 */
public final class ActTimerEntry<T extends AIOActResult>
	extends TimerEntry implements Runnable {

	private final BaseActRequest<T> request;


	ActTimerEntry(final BaseActRequest<T> request) {
		super();
		this.request = request;
	}


	@Override
	protected final void free() {
		// Nothing to do.
	}

	@Override
	public final Runnable getRunnable() {
		return this;
	}

	@Override
	public final void fail(final Throwable cause) {
		// Nothing to do.
	}

	@Override
	protected final int checkTimeout(final long now, final long[] delayTime) {
		if (!this.request.future().isDone()) {
			return super.checkTimeout(now, delayTime);
		}
		return -1;
	}

	@Override
	protected final void onScheduled() {
		this.request.onTimerEntryScheduled();
	}

	@Override
	protected final void onCancelled() {
		this.request.onTimerEntryCancelled();
	}

	@Override
	protected final void onDiscarded() {
		this.request.onTimerEntryDiscarded();
	}

	/*
	 * (non-Javadoc)
	 * @see java.lang.Runnable#run()
	 */
	public final void run() {
		this.request.timeout();
	}

	@Override
	public final String toString() {
		final StringBuilder builder = new StringBuilder();

		builder.append("ActTimerEntry: [");
		builder.append(this.request.toString());
		builder.append(" timeout: ").append(getTimeout());
		builder.append("]");

		return builder.toString();
	}

}
