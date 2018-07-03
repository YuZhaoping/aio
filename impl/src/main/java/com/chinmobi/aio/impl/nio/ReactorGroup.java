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

import java.io.IOException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.Selector;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicReferenceArray;

import com.chinmobi.aio.AIOAcceptor;
import com.chinmobi.aio.AIOConfiguration;
import com.chinmobi.aio.AIOConnector;
import com.chinmobi.aio.AIOIllegalReactorException;
import com.chinmobi.aio.AIOInetEndpoint;
import com.chinmobi.aio.AIONotActiveException;
import com.chinmobi.aio.AIOReactor;
import com.chinmobi.aio.AIOReactorGroup;
import com.chinmobi.aio.AIOReactorStatistics;
import com.chinmobi.aio.AIOServiceHandler;
import com.chinmobi.aio.AIOSession;
import com.chinmobi.aio.impl.BuildOptions;
import com.chinmobi.aio.impl.Constants;
import com.chinmobi.aio.scheme.AIOTransportScheme;
import com.chinmobi.aio.util.AIOTimer;
import com.chinmobi.logging.LogManager;
import com.chinmobi.logging.Logger;

/**
 * @author <a href="mailto:yuzhaoping1970@gmail.com">Zhaoping Yu</a>
 *
 */
public final class ReactorGroup
	implements AIOReactorGroup, Dispatcher, AIOReactor.Observer {

	private final Logger logger;

	private final AIOConfiguration config;

	private final Object lock;

	private final AIOConnector connector;
	private final AcceptorSet acceptors;

	private volatile AtomicReferenceArray<Reactor> reactors;
	private final AtomicInteger cursor;
	private final AtomicInteger activeCount;

	private int reactorTrace;

	private volatile long startTime;

	private volatile int status;

	private volatile AIOReactor.Observer observer;


	public ReactorGroup(final AIOConfiguration config) {
		this.logger = LogManager.getLogger(config.getLoggerName());

		this.config = config;

		this.lock = new Object();

		this.connector = ComponentCreator.createConnector((Dispatcher)this);
		this.acceptors = ComponentCreator.createAcceptorSet((Dispatcher)this);

		this.cursor = new AtomicInteger(0);
		this.activeCount = new AtomicInteger(0);

		this.startTime = System.currentTimeMillis();
		this.status = 0;
	}


	public final Logger logger() {
		return this.logger;
	}

	/* (non-Javadoc)
	 * @see com.chinmobi.aio.AIOReactor#configuration()
	 */
	public final AIOConfiguration configuration() {
		return this.config;
	}

	/* (non-Javadoc)
	 * @see com.chinmobi.aio.AIOReactor#setObserver(AIOReactor.Observer observer)
	 */
	public final void setObserver(final AIOReactor.Observer observer) {
		this.observer = observer;

		final AtomicReferenceArray<Reactor> reactors = this.reactors;
		if (reactors != null) {
			for (int i = 0; i < reactors.length(); ++i) {
				final Reactor reactor = reactors.get(i);
				if (reactor != null && !reactor.isShutdown()) {
					reactor.setObserver(observer);
				}
			}
		}
	}

	/*
	 * (non-Javadoc)
	 * @see com.chinmobi.aio.AIOReactor#getObserver()
	 */
	public final AIOReactor.Observer getObserver() {
		return this.observer;
	}

	/* (non-Javadoc)
	 * @see com.chinmobi.aio.AIOReactor#start()
	 */
	public final void start() throws IOException, AIOIllegalReactorException {
		int size = this.config.getReactorGroupSize();
		if (size <= 0) {
			size = 1;
		}

		synchronized (this.lock) {
			if (this.status < 0) {
				this.status = -this.status;
				this.lock.notifyAll();
			}

			if (this.status == 0) {
				this.startTime = System.currentTimeMillis();
				this.reactorTrace = 0;
			}

			if (sizeOfReactors() < size) {
				final int begin = resizeReactorsArray(size);

				for (int i = begin; i < size; ++i, ++this.reactorTrace) {
					final Selector selector = Selector.open();

					final Reactor reactor = new Reactor(selector, this.config,
							this, String.valueOf(this.reactorTrace));
					reactor.setObserver(this.observer);

					this.reactors.set(i, reactor);

					reactor.start();

					++this.status;
				}
			}
		}
	}

	/* (non-Javadoc)
	 * @see com.chinmobi.aio.AIOReactor#stop(boolean waitStopped)
	 */
	public final boolean stop(final boolean waitForStopped) {
		synchronized (this.lock) {
			if (this.status > 0) {
				this.status = -this.status;
			}

			final AtomicReferenceArray<Reactor> reactors = this.reactors;
			if (reactors != null) {
				for (int i = 0; i < reactors.length(); ++i) {
					final Reactor reactor = reactors.get(i);
					if (reactor != null && !reactor.isShutdown()) {
						reactor.stop(false);
					}
				}
			}

			if (waitForStopped) {
				while (this.status < 0) {
					try {
						this.lock.wait();
					} catch (InterruptedException ex) {
						Thread.currentThread().interrupt();
						break;
					}
				}
			}

			return (this.status == 0);
		}
	}

	/* (non-Javadoc)
	 * @see com.chinmobi.aio.AIOReactor#isActive()
	 */
	public final boolean isActive() {
		return (this.status > 0);
	}

	/*
	 * (non-Javadoc)
	 * @see com.chinmobi.aio.AIOReactor#register(...)
	 */
	public final AIOSession register(final SelectableChannel channel, final int ops,
			final AIOServiceHandler.Factory serviceFactory) throws ClosedChannelException, IOException {
		final Reactor reactor = dispatchReactor();
		if (reactor != null) {
			return reactor.register(channel, ops, serviceFactory);
		} else {
			throw new AIONotActiveException("Null reactor.");
		}
	}

	/*
	 * (non-Javadoc)
	 * @see com.chinmobi.aio.AIOReactor#acceptors()
	 */
	public final Iterator<AIOAcceptor> acceptors() {
		if (BuildOptions.SUPPORT_SERVER != 0) {
			return this.acceptors.iterator();
		}
		throw new UnsupportedOperationException();
	}

	/* (non-Javadoc)
	 * @see com.chinmobi.aio.AIOReactor#getAcceptor(AIOTransportScheme scheme, AIOInetEndpoint endpoint)
	 */
	public final AIOAcceptor getAcceptor(final AIOTransportScheme scheme, final AIOInetEndpoint endpoint)
			throws AIONotActiveException {
		if (BuildOptions.SUPPORT_SERVER != 0) {
			return this.acceptors.getAcceptor(scheme, endpoint);
		}
		throw new UnsupportedOperationException();
	}

	/* (non-Javadoc)
	 * @see com.chinmobi.aio.AIOReactor#getConnector()
	 */
	public final AIOConnector getConnector() {
		if (BuildOptions.SUPPORT_CLIENT != 0) {
			return this.connector;
		}
		throw new UnsupportedOperationException();
	}

	/* (non-Javadoc)
	 * @see com.chinmobi.aio.AIOReactor#getTimer()
	 */
	public final AIOTimer getTimer() {
		final Reactor reactor = dispatchReactor();
		if (reactor != null) {
			return reactor.getTimer();
		} else {
			throw new AIONotActiveException("Null reactor.");
		}
	}

	/*
	 * (non-Javadoc)
	 * @see com.chinmobi.aio.AIOReactor#getStatistics()
	 */
	public final AIOReactorStatistics getStatistics() {
		return new Statistics(new Iter(this), this.startTime).reset();
	}

	/*
	 * (non-Javadoc)
	 * @see com.chinmobi.aio.AIOReactor#toGroup()
	 */
	public final AIOReactorGroup toGroup() {
		return this;
	}

	/* (non-Javadoc)
	 * @see com.chinmobi.aio.AIOReactorGroup#getActiveCount()
	 */
	public final int getActiveCount() {
		return this.activeCount.get();
	}

	/* (non-Javadoc)
	 * @see com.chinmobi.aio.AIOReactorGroup#iterator()
	 */
	public final Iterator<AIOReactor> iterator() {
		return new Iter(this);
	}

	/*
	 * (non-Javadoc)
	 * @see com.chinmobi.aio.impl.nio.Dispatcher#dispatch(SessionContext currentContext)
	 */
	public final SessionContext dispatch(final SessionContext currentContext) {
		final Reactor reactor = dispatchReactor();
		if (reactor != null) {
			return reactor.sessionContext();
		} else if (currentContext != null) {
			return currentContext;
		} else {
			throw new AIONotActiveException("Null reactor.");
		}
	}

	private final Reactor dispatchReactor() {
		final AtomicReferenceArray<Reactor> reactors = this.reactors;
		if (reactors != null) {

			final int length = reactors.length();

			final int start = this.cursor.getAndIncrement();

			int o = start;
			do {
				final int i = o % length;
				if (i != o) {
					this.cursor.compareAndSet((o + 1), (i + 1));
				}

				final Reactor reactor = reactors.get(i);
				if (reactor != null && reactor.isActive()) {
					return reactor;
				}

			} while (start != (o = this.cursor.getAndIncrement()));

		}

		return null;
	}

	private final int resizeReactorsArray(final int size) {
		final AtomicReferenceArray<Reactor> newArray = new AtomicReferenceArray<Reactor>(size);
		final AtomicReferenceArray<Reactor> reactors = this.reactors;

		if (reactors == null) {
			this.reactors = newArray;
			return 0;
		} else {
			int end = 0;

			for (int i = 0; i < reactors.length(); ++i) {
				final Reactor reactor = reactors.get(i);
				if (reactor != null && !reactor.isShutdown()) {
					newArray.set(end, reactor);
					++end;
				}
			}

			this.reactors = newArray;
			return end;
		}
	}

	private final int sizeOfReactors() {
		int size = 0;
		final AtomicReferenceArray<Reactor> reactors = this.reactors;
		if (reactors != null) {
			for (int i = 0; i < reactors.length(); ++i) {
				final Reactor reactor = reactors.get(i);
				if (reactor != null && !reactor.isShutdown()) {
					++size;
				}
			}
		}
		return size;
	}

	/*
	 * (non-Javadoc)
	 * @see com.chinmobi.aio.AIOReactor.Observer#onAIOReactorStarted(AIOReactor reactor)
	 */
	public final void onAIOReactorStarted(final AIOReactor reactor) {
		if (this.activeCount.getAndIncrement() != 0) {
			return;
		}

		final AIOReactor.Observer observer = this.observer;
		if (observer != null) {
			observer.onAIOReactorStarted(this);
		}
	}

	/*
	 * (non-Javadoc)
	 * @see com.chinmobi.aio.AIOReactor.Observer#onAIOReactorStopped(AIOReactor reactor, Throwable cause)
	 */
	public final void onAIOReactorStopped(final AIOReactor reactor, final Throwable cause) {
		boolean toNotify = false;

		final AtomicReferenceArray<Reactor> reactors = this.reactors;
		if (reactors != null) {
			for (int i = 0; i < reactors.length(); ++i) {
				if (reactor == reactors.get(i)) {

					this.activeCount.decrementAndGet();

					synchronized (this.lock) {
						if (this.status > 0) {
							--this.status;
						} else
						if (this.status < 0) {
							++this.status;
						}

						if (this.status == 0) {
							this.lock.notifyAll();
							toNotify = true;
						}
					}

					break;
				}
			}
		}

		final AIOReactor.Observer observer = this.observer;
		if (observer != null && toNotify) {
			observer.onAIOReactorStopped(this, cause);
		} else if (cause != null) {
			this.logger().error().writeln().write(ThreadPool.currentThreadId()).write(cause).flush();
		}
	}

	final AIOConnector connector() {
		return this.connector;
	}

	final AcceptorSet acceptorSet() {
		return this.acceptors;
	}

	@Override
	public final String toString() {
		final StringBuilder builder = new StringBuilder();

		builder.append(Constants.CRLF).append("  ");
		final String PRE = builder.toString();
		builder.delete(0, builder.length());

		builder.append(Constants.CRLF).append("ReactorGroup [");

		builder.append(PRE).append("StartTime: ").append(ReactorStatistics.toString(this.startTime));
		builder.append(PRE).append("ActiveCount: ").append(getActiveCount());

		final Iter iter = new Iter(this);
		while (iter.hasNext()) {
			final AIOReactor reactor = iter.next();
			if (reactor != null) {
				builder.append(reactor.toString());
			}
		}

		builder.append(Constants.CRLF).append(']');

		return builder.toString();
	}


	private static final class Iter implements Iterator<AIOReactor> {

		private final ReactorGroup group;

		private final AtomicInteger index;
		private final AtomicReference<AIOReactor> current;


		Iter(final ReactorGroup group) {
			this.group = group;

			this.index = new AtomicInteger(0);
			this.current = new AtomicReference<AIOReactor>();
		}


		final void reset() {
			this.index.set(0);
		}

		private final AIOReactor indexOfReactor(final boolean increase) {
			final AtomicReferenceArray<Reactor> reactors = this.group.reactors;

			if (reactors != null) {
				final int o = this.index.get();

				int i = o;
				for (; i < reactors.length(); ++i) {
					final AIOReactor reactor = reactors.get(i);
					if (reactor != null) {
						if (increase) {
							this.index.compareAndSet(o, i + 1);
						}
						return reactor;
					}
				}

				this.index.compareAndSet(o, i);
			}

			return null;
		}


		public final boolean hasNext() {
			return (indexOfReactor(false) != null);
		}

		public final AIOReactor next() {
			final AIOReactor reactor = indexOfReactor(true);
			this.current.set(reactor);
			return reactor;
		}

		public final void remove() {
			final AIOReactor reactor = pollCurrentReactor();
			if (reactor != null) {
				reactor.stop(false);
			}
		}

		private final AIOReactor pollCurrentReactor() {
			return this.current.getAndSet(null);
		}

	}

	private static final class Statistics implements AIOReactorStatistics {

		private final Iter iter;

		private final long startTime;

		private int sessionActiveCount;
		private int sessionLargestCount;

		private int threadActiveCount;
		private int threadLargestPoolSize;
		private int threadPoolSize;


		Statistics(final Iter iter, final long startTime) {
			this.iter = iter;
			this.startTime = startTime;
		}


		final AIOReactorStatistics reset() {
			this.sessionActiveCount = 0;
			this.sessionLargestCount = 0;

			this.threadActiveCount = 0;
			this.threadLargestPoolSize = 0;
			this.threadPoolSize = 0;

			this.iter.reset();
			while (this.iter.hasNext()) {
				final AIOReactor reactor = this.iter.next();
				if (reactor != null) {
					final AIOReactorStatistics stat = reactor.getStatistics();

					this.sessionActiveCount += stat.getSessionActiveCount();
					this.sessionLargestCount += stat.getSessionLargestCount();

					this.threadActiveCount += stat.getThreadActiveCount();
					this.threadLargestPoolSize += stat.getThreadLargestPoolSize();
					this.threadPoolSize += stat.getThreadPoolSize();
				}
			}

			return this;
		}

		public final long getStartTime() {
			return this.startTime;
		}

		/*
		 * For sessions
		 */

		public final int getSessionActiveCount() {
			return this.sessionActiveCount;
		}

		public final int getSessionLargestCount() {
			return this.sessionLargestCount;
		}

		/*
		 * For threads
		 */

		public final int getThreadActiveCount() {
			return this.threadActiveCount;
		}

		public final int getThreadLargestPoolSize() {
			return this.threadLargestPoolSize;
		}

		public final int getThreadPoolSize() {
			return this.threadPoolSize;
		}

		@Override
		public final String toString() {
			final StringBuilder builder = new StringBuilder();

			builder.append("ReactorGroupStatistics [");
			ReactorStatistics.toString(this, builder, Constants.CRLF);
			builder.append(Constants.CRLF).append(']');

			return builder.toString();
		}

	}

}
