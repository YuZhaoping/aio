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
package com.chinmobi.aio.impl.util;

import com.chinmobi.aio.util.AIOScheduledFuture;
import com.chinmobi.aio.util.AIOTimer;
import com.chinmobi.aio.util.AIOTimerCallable;

/**
 * @author <a href="mailto:yuzhaoping1970@gmail.com">Zhaoping Yu</a>
 *
 */
public final class TimerCallableEntry extends TimerEntry implements Runnable {

	private final AIOTimer timer;

	private final AIOTimerCallable callable;

	private final Future future;


	TimerCallableEntry(final AIOTimer timer, final AIOTimerCallable callable, final Object attachment) {
		super();

		this.timer = timer;
		this.callable = callable;
		this.future = new Future(attachment);
	}


	public final AIOScheduledFuture future() {
		return this.future;
	}

	@Override
	protected final void free() {
		if (this.future.toInvalid() > 0) {
			try {
				this.callable.aioTimerCall(this.timer, this.future.attachment(), true);
			} catch (RuntimeException ex) {
				this.belongsTo.notifyEntryRuntimeException(this, ex);
			}
		}
	}

	@Override
	public final Runnable getRunnable() {
		return this;
	}

	@Override
	public final void cancel() {
		this.future.toInvalid();
	}

	@Override
	public final void fail(final Throwable cause) {
		this.future.toInvalid();
	}

	@Override
	protected final int checkTimeout(final long now, final long[] delayTime) {
		return this.future.checkTimeout(now, delayTime);
	}

	@Override
	protected final boolean enableZeroTimeout() {
		return true;
	}

	/* (non-Javadoc)
	 * @see java.lang.Runnable#run()
	 */
	public final void run() {
		final int status = this.future.toRun();
		if (status >= 0) {
			try {
				this.callable.aioTimerCall(this.timer, this.future.attachment(), (status == 0));
			} catch (RuntimeException ex) {
				this.belongsTo.notifyEntryRuntimeException(this, ex);
			}
		}
	}

	@Override
	public final String toString() {
		final StringBuilder builder = new StringBuilder();

		builder.append("TimerCallableEntry: [");
		builder.append("timeout: ").append(getTimeout());
		builder.append("]");

		return builder.toString();
	}


	private final int doCheckTimeout(final long now, final long[] delayTime) {
		return super.checkTimeout(now, delayTime);
	}


	private final class Future implements AIOScheduledFuture {

		private static final int CASE_INVALID	= -2;
		private static final int CASE_CANCELLED	= -1;
		private static final int CASE_PENDING	=  0;
		private static final int CASE_RAN		=  1;

		private final Object attachment;

		private int status;


		Future(final Object attachment) {
			this.attachment = attachment;

			this.status = CASE_PENDING;
		}


		public final boolean cancel() {
			synchronized (this) {
				switch (this.status) {
				case CASE_PENDING:
					this.status = CASE_CANCELLED;
					break;

				case CASE_CANCELLED:
					return true;

				default:
					return false;
				}
			}

			TimerCallableEntry.this.syncRemoveFromTree();
			return true;
		}

		public final boolean isCancelled() {
			synchronized (this) {
				return (this.status == CASE_CANCELLED);
			}
		}

		public final boolean isDone() {
			synchronized (this) {
				return (this.status != CASE_PENDING);
			}
		}

		public final Object attachment() {
			return this.attachment;
		}

		final int checkTimeout(final long now, final long[] delayTime) {
			synchronized (this) {
				switch (this.status) {
				case CASE_PENDING:
					return TimerCallableEntry.this.doCheckTimeout(now, delayTime);

				case CASE_CANCELLED:
					return -1;

				default:
					return 1;
				}
			}
		}

		final int toRun() {
			synchronized (this) {
				switch (this.status) {
				case CASE_PENDING:
					this.status = CASE_RAN;

				case CASE_RAN:
					return 1;

				case CASE_CANCELLED:
					return -1;

				default:
					return 0;
				}
			}
		}

		final int toInvalid() {
			synchronized (this) {
				switch (this.status) {
				case CASE_PENDING:
					this.status = CASE_INVALID;

				case CASE_INVALID:
					return 1;

				case CASE_CANCELLED:
					return -1;

				default:
					return 0;
				}
			}
		}

	}

}
