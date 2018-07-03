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

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;

import com.chinmobi.aio.impl.util.ExtendedCondition;
import com.chinmobi.aio.impl.util.SmartLock;

/**
 * @author <a href="mailto:yuzhaoping1970@gmail.com">Zhaoping Yu</a>
 *
 */
public final class ThreadPool implements Runnable {

	public interface Helper {
		public void handleUncaughtException(Throwable ex);

		public int getCoreThreadPoolSize();
		public int getMaximumThreadPoolSize();

		public long getIdleThreadKeepAliveTime();

		public int getThreadPriority();
		public boolean isThreadDaemon();
	}


	public interface NotEmptyCallback {
		public void onThreadPoolNotEmpty();
	}


	private static final AtomicInteger THREAD_COUNT = new AtomicInteger();

	private final Helper helper;

	private final String poolName;


	private final Lock lock;

	private final Condition termination;
	private final Condition notFull;
	private final ExtendedCondition notEmpty;


	private boolean isShutdown;

	private int poolSize;
	private int largestPoolSize;
	private int activeCount;

	private Runnable pendingTask;


	public ThreadPool(final Helper helper) {
		this(helper, null);
	}

	public ThreadPool(final Helper helper, final String name) {
		this.helper = helper;

		this.poolName = name;

		this.lock = new SmartLock();

		this.termination = this.lock.newCondition();
		this.notFull = this.lock.newCondition();
		this.notEmpty = (ExtendedCondition)this.lock.newCondition();
	}


	public final String name() {
		return this.poolName;
	}

	public final void start() {
		final Lock lock = this.lock;
		lock.lock();
		try {
			this.isShutdown = false;

			final int corePoolSize = corePoolSize();

			while (this.poolSize < corePoolSize) {
				spawn();
			}
		} finally {
			lock.unlock();
		}
	}

	public final void shutdown(final boolean waitForShuttedDown) {
		final Lock lock = this.lock;
		lock.lock();
		try {
			this.isShutdown = true;

			this.notEmpty.signalAll();

			if (waitForShuttedDown) {
				final Thread currentThread = Thread.currentThread();
				if (currentThread instanceof Worker) {
					final Worker worker = (Worker)currentThread;
					if (worker.pool() == this) {
						--this.poolSize;
					}
				}

				while (this.poolSize > 0) {
					try {
						this.termination.await();
					} catch (InterruptedException ex) {
						Thread.currentThread().interrupt();
						break;
					}
				}

				if (currentThread instanceof Worker) {
					final Worker worker = (Worker)currentThread;
					if (worker.pool() == this) {
						++this.poolSize;
					}
				}
			}

		} finally {
			lock.unlock();
		}
	}

	public final boolean tryExecute(final Runnable task) {
		final Lock lock = this.lock;
		if (lock.tryLock()) {
			try {
				if (this.pendingTask == null) {
					this.pendingTask = task;
					this.notEmpty.signal();
					return true;
				}
			} finally {
				lock.unlock();
			}
		}
		return false;
	}

	public final void execute(final Runnable task, final boolean autoSpawn) {
		execute(task, autoSpawn, true, null);
	}

	public final boolean execute(final Runnable task,
			final boolean autoSpawn, final boolean waitForPut,
			final NotEmptyCallback callback) {

		boolean interrupted = false;

		final Lock lock = this.lock;
		lock.lock();
		try {
			while (this.pendingTask != null) {
				if (!this.notEmpty.hasWaiters() &&
					(autoSpawn || this.poolSize < corePoolSize()) &&
					this.poolSize < maximumPoolSize()) {
					spawn();
				}

				if (waitForPut) {
					try {
						this.notFull.await();
					} catch (InterruptedException ignore) {
						interrupted = true;
					}
				} else {
					if (callback != null) {
						callback.onThreadPoolNotEmpty();
					}
					return false;
				}
			}

			this.pendingTask = task;
			this.notEmpty.signal();

		} finally {
			lock.unlock();
		}

		if (interrupted) {
			Thread.currentThread().interrupt();
		}

		return true;
	}


	public final boolean isShutdown() {
		final Lock lock = this.lock;
		lock.lock();
		try {
			return this.isShutdown;
		} finally {
			lock.unlock();
		}
	}

	public final int getPoolSize() {
		final Lock lock = this.lock;
		lock.lock();
		try {
			return this.poolSize;
		} finally {
			lock.unlock();
		}
	}

	public final int getLargestPoolSize() {
		final Lock lock = this.lock;
		lock.lock();
		try {
			return this.largestPoolSize;
		} finally {
			lock.unlock();
		}
	}

	public final int getActiveCount() {
		final Lock lock = this.lock;
		lock.lock();
		try {
			return this.activeCount;
		} finally {
			lock.unlock();
		}
	}

	/* (non-Javadoc)
	 * @see java.lang.Runnable#run()
	 */
	public final void run() {
		Runnable task = poll(false);

		while (task != null) {
			try {
				task.run();
			} catch (Throwable ex) {
				try {
					this.helper.handleUncaughtException(ex);
				} catch (Throwable ignore) {
				}
			}

			task = poll(true);
		}
	}

	private final Runnable poll(final boolean ran) {
		final Lock lock = this.lock;
		lock.lock();
		try {
			boolean waittedTime = false;

			if (ran) {
				--this.activeCount;
			}

			while (this.pendingTask == null) {
				if (this.isShutdown ||
					this.poolSize > corePoolSize()) {

					if (!this.isShutdown && !waittedTime) {
						waittedTime = true;

						try {
							this.notEmpty.await(keepAliveTime(), TimeUnit.MILLISECONDS);
						} catch (InterruptedException ignore) {
						}

						continue;
					}

					if (--this.poolSize <= 0) {
						this.termination.signalAll();
					}

					return null;
				}

				try {
					this.notEmpty.await();
				} catch (InterruptedException ignore) {
				}
			}

			final Runnable task = this.pendingTask;
			this.pendingTask = null;

			if (task != null) {
				++this.activeCount;
			}

			this.notFull.signal();
			return task;

		} finally {
			lock.unlock();
		}
	}


	private static final class Worker extends SmartLock.WorkThread {

		private final ThreadPool pool;


		Worker(final ThreadPool pool,
				final Runnable runnable, final String name) {
			super(runnable, name);
			this.pool = pool;
		}


		final ThreadPool pool() {
			return this.pool;
		}

	}


	private final void spawn() {
		final StringBuilder name = new StringBuilder("aio_TP");
		if (this.poolName != null && this.poolName.length() > 0) {
			name.append('_').append(this.poolName);
		}
		name.append('[').append(threadCount()).append(']');

		final Thread thread = new Worker(this, (Runnable)this, name.toString());
		++this.poolSize;
		thread.setPriority(this.helper.getThreadPriority());
		thread.setDaemon(this.helper.isThreadDaemon());
		thread.start();

		if (this.poolSize > this.largestPoolSize) {
			this.largestPoolSize = this.poolSize;
		}
	}

	private static final int threadCount() {
		return THREAD_COUNT.incrementAndGet();
	}

	private final int corePoolSize() {
		int corePoolSize = this.helper.getCoreThreadPoolSize();
		if (corePoolSize <= 0) {
			corePoolSize = 1;
		}
		return corePoolSize;
	}

	private final int maximumPoolSize() {
		int maximumPoolSize = this.helper.getMaximumThreadPoolSize();
		if (maximumPoolSize <= 0) {
			maximumPoolSize = Integer.MAX_VALUE;
		}
		return maximumPoolSize;
	}

	private final long keepAliveTime() {
		long time = this.helper.getIdleThreadKeepAliveTime();
		if (time < 0) {
			time = 0;
		}
		return time;
	}

	public static final String currentThreadId() {
		final StringBuilder builder = new StringBuilder();

		builder.append('[');
		builder.append(Long.toString(Thread.currentThread().getId(), 16));
		builder.append("] ");

		return builder.toString();
	}

}
