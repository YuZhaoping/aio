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
import java.io.InterruptedIOException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ClosedSelectorException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Iterator;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;

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
import com.chinmobi.aio.impl.util.TimerEntry;
import com.chinmobi.aio.impl.util.TimerEntrySet;
import com.chinmobi.aio.scheme.AIOTransportScheme;
import com.chinmobi.aio.util.AIOScheduledFuture;
import com.chinmobi.aio.util.AIOTimer;
import com.chinmobi.aio.util.AIOTimerCallable;
import com.chinmobi.logging.LogManager;
import com.chinmobi.logging.Logger;

/**
 * @author <a href="mailto:yuzhaoping1970@gmail.com">Zhaoping Yu</a>
 *
 */
public final class Reactor
	implements AIOReactor, Demultiplexer.ActiveChecker, ThreadPool.Helper,
		ExceptionHandler, Dispatcher {

	private final Logger logger;

	private final AIOConfiguration config;

	private final ReactorGroup belongsGroup;

	private final ThreadPool threadPool;

	private final Leader leader;
	private final Follower follower;

	private final Timer timer;
	private final TimerEntrySet timerEntries;

	private final Demultiplexer demultiplexer;

	private final SessionContext sessionContext;

	private final AIOConnector connector;
	private final AcceptorSet acceptors;

	private final ReactorStatistics statistics;

	private volatile Observer observer;

	private volatile int status;


	public Reactor(final Selector selector, final AIOConfiguration config) {
		this(selector, config, null, null);
	}

	public Reactor(final Selector selector, final AIOConfiguration config,
			final ReactorGroup group, final String poolName) {
		this.logger = LogManager.getLogger(config.getLoggerName());

		this.config = config;

		this.belongsGroup = group;

		this.threadPool = new ThreadPool((ThreadPool.Helper)this, poolName);

		this.leader = new Leader(this);
		this.follower = new Follower(this);

		this.timer = new Timer(this);
		this.timerEntries = new TimerEntrySet((TimerEntrySet.Observer)this.timer);

		this.demultiplexer = new Demultiplexer(selector, this.timerEntries, (Demultiplexer.ActiveChecker)this,
				config.isInterestOpsQueueing(), true, this.logger);

		final Dispatcher dispatcher = (group != null) ? group : null;
		this.sessionContext = new SessionContext(this.logger, this.demultiplexer, dispatcher);

		if (group != null) {
			this.connector = group.connector();
			this.acceptors = group.acceptorSet();
		} else {
			this.connector = ComponentCreator.createConnector((Dispatcher)this);
			this.acceptors = ComponentCreator.createAcceptorSet((Dispatcher)this);
		}

		this.statistics = new ReactorStatistics(this);

		this.status = ReactorStatus.INACTIVE;
	}


	public final Logger logger() {
		return this.logger;
	}

	public final SessionContext sessionContext() {
		return this.sessionContext;
	}

	final ThreadPool threadPool() {
		return this.threadPool;
	}

	/*
	 * (non-Javadoc)
	 * @see com.chinmobi.aio.impl.nio.Dispatcher#dispatch(SessionContext currentContext)
	 */
	public final SessionContext dispatch(final SessionContext currentContext) {
		return this.sessionContext;
	}

	/*
	 * (non-Javadoc)
	 * @see com.chinmobi.aio.impl.nio.Demultiplexer.ActiveChecker#assertActive()
	 */
	public final void assertActive() throws AIONotActiveException {
		switch (this.status) {
		case ReactorStatus.INACTIVE:
		case ReactorStatus.SHUTDOWN_REQUEST:
		case ReactorStatus.SHUTTING_DOWN:
		case ReactorStatus.SHUTDOWN:
			throw new AIONotActiveException("Status: " + this.status);

		default:
			break;
		}
	}

	/* (non-Javadoc)
	 * @see com.chinmobi.aio.impl.nio.ExceptionHandler#handleUncaughtException(Throwable ex)
	 */
	public final void handleUncaughtException(final Throwable ex) {
		this.logger().error().writeln().write(ThreadPool.currentThreadId()).write(ex).flush();
	}

	/*
	 * (non-Javadoc)
	 * @see com.chinmobi.aio.impl.nio.ThreadPool.Helper#getCoreThreadPoolSize()
	 */
	public final int getCoreThreadPoolSize() {
		return this.config.getCoreThreadPoolSize();
	}

	/*
	 * (non-Javadoc)
	 * @see com.chinmobi.aio.impl.nio.ThreadPool.Helper#getMaximumThreadPoolSize()
	 */
	public final int getMaximumThreadPoolSize() {
		return this.config.getMaximumThreadPoolSize();
	}

	/*
	 * (non-Javadoc)
	 * @see com.chinmobi.aio.impl.nio.ThreadPool.Helper#getIdleThreadKeepAliveTime()
	 */
	public final long getIdleThreadKeepAliveTime() {
		return this.config.getIdleThreadKeepAliveTime();
	}

	/*
	 * (non-Javadoc)
	 * @see com.chinmobi.aio.impl.nio.ThreadPool.Helper#getThreadPriority()
	 */
	public final int getThreadPriority() {
		return this.config.getThreadPriority();
	}

	/*
	 * (non-Javadoc)
	 * @see com.chinmobi.aio.impl.nio.ThreadPool.Helper#isThreadDaemon()
	 */
	public final boolean isThreadDaemon() {
		return this.config.isThreadDaemon();
	}


	/* (non-Javadoc)
	 * @see com.chinmobi.aio.AIOReactor#configuration()
	 */
	public final AIOConfiguration configuration() {
		return this.config;
	}

	/* (non-Javadoc)
	 * @see com.chinmobi.aio.AIOReactor#setObserver(Observer observer)
	 */
	public final void setObserver(final Observer observer) {
		this.observer = observer;
	}

	/*
	 * (non-Javadoc)
	 * @see com.chinmobi.aio.AIOReactor#getObserver()
	 */
	public final Observer getObserver() {
		return this.observer;
	}

	/* (non-Javadoc)
	 * @see com.chinmobi.aio.AIOReactor#start()
	 */
	public final void start() throws IOException, AIOIllegalReactorException {
		try {
			this.leader.execute();
		} catch (AIOIllegalReactorException ex) {
			throw ex;
		}
	}

	/* (non-Javadoc)
	 * @see com.chinmobi.aio.AIOReactor#stop(boolean waitStopped)
	 */
	public final boolean stop(final boolean waitForStopped) {
		try {
			return this.leader.shutdown(waitForStopped);
		} catch (InterruptedException ignore) {
			Thread.currentThread().interrupt();
		}
		return (this.status == ReactorStatus.SHUTDOWN);
	}

	/* (non-Javadoc)
	 * @see com.chinmobi.aio.AIOReactor#isActive()
	 */
	public final boolean isActive() {
		switch (this.status) {
		case ReactorStatus.INACTIVE:
		case ReactorStatus.SHUTDOWN_REQUEST:
		case ReactorStatus.SHUTTING_DOWN:
		case ReactorStatus.SHUTDOWN:
			return false;

		default:
			return true;
		}
	}

	/*
	 * (non-Javadoc)
	 * @see com.chinmobi.aio.AIOReactor#register(...)
	 */
	public final AIOSession register(final SelectableChannel channel, final int ops,
			final AIOServiceHandler.Factory serviceFactory) throws ClosedChannelException, IOException {
		return this.sessionContext.sessionCreator().createSession(channel, ops, serviceFactory);
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
		return this.timer;
	}

	/*
	 * (non-Javadoc)
	 * @see com.chinmobi.aio.AIOReactor#getStatistics()
	 */
	public final AIOReactorStatistics getStatistics() {
		return this.statistics;
	}

	/*
	 * (non-Javadoc)
	 * @see com.chinmobi.aio.AIOReactor#toGroup()
	 */
	public final AIOReactorGroup toGroup() {
		return null;
	}

	final boolean isShutdown() {
		switch (this.status) {
		case ReactorStatus.SHUTDOWN_REQUEST:
		case ReactorStatus.SHUTTING_DOWN:
		case ReactorStatus.SHUTDOWN:
			return true;

		default:
			return false;
		}
	}

	@Override
	public final String toString() {
		final StringBuilder builder = new StringBuilder();

		builder.append(Constants.CRLF);
		if (this.belongsGroup != null) {
			builder.append("  ");
		}
		final String PRE0 = builder.toString();

		builder.append("  ");
		final String PRE1 = builder.toString();
		builder.delete(0, builder.length());

		builder.append(PRE0).append("Reactor ");
		if (this.threadPool.name() != null) {
			builder.append(this.threadPool.name()).append(' ');
		}
		builder.append('[');

		builder.append(PRE1).append("Status: ").append(stringOfStatus(this.status));

		ReactorStatistics.toString(this.statistics, builder, PRE1);

		builder.append(PRE0).append(']');

		return builder.toString();
	}

	private static final String stringOfStatus(final int status) {
		switch (status) {
		case ReactorStatus.INACTIVE: return "INACTIVE";
		case ReactorStatus.ACTIVE: return "ACTIVE";
		case ReactorStatus.STARTING: return "STARTING";
		case ReactorStatus.SHUTDOWN_REQUEST: return "SHUTDOWN_REQUEST";
		case ReactorStatus.SHUTTING_DOWN: return "SHUTTING_DOWN";
		case ReactorStatus.SHUTDOWN: return "SHUTDOWN";
		default: return null;
		}
	}


	private final boolean toStarting() {
		synchronized (this.leader) {
			switch (this.status) {
			case ReactorStatus.ACTIVE:
				this.status = ReactorStatus.STARTING;
			case ReactorStatus.STARTING:
				return true;
			default:
				return false;
			}
		}
	}

	private final boolean endStarting() {
		synchronized (this.leader) {
			switch (this.status) {
			case ReactorStatus.STARTING:
				this.status = ReactorStatus.ACTIVE;
			case ReactorStatus.ACTIVE:
				return true;
			default:
				return false;
			}
		}
	}

	private final void runStart() {
		if (this.logger().isDebugEnabled()) {
			this.logger().debug().writeln().writeLogHeader("reactorStarted ...").flush();
		}

		boolean isStarting = false;

		if (this.belongsGroup != null && toStarting()) {
			isStarting = true;
			try {
				this.belongsGroup.onAIOReactorStarted(this);
			} catch (Throwable ignore) {
				handleUncaughtException(ignore);
			}
		}

		final Observer observer = this.observer;
		if (observer != null) {
			if (toStarting()) {
				isStarting = true;
				try {
					observer.onAIOReactorStarted(this);
				} catch (Throwable ignore) {
					handleUncaughtException(ignore);
				}
			} else {
				this.observer = null;
			}
		}

		if (isStarting) {
			endStarting();
		}

		this.leader.run();
	}

	private static final class Starter implements Runnable {

		private final Reactor reactor;


		Starter(final Reactor reactor) {
			this.reactor = reactor;
		}

		/* (non-Javadoc)
		 * @see java.lang.Runnable#run()
		 */
		public final void run() {
			this.reactor.runStart();
		}

	}


	private static final class Leader
		implements Runnable, Demultiplexer.SelectInterceptor, ThreadPool.NotEmptyCallback {

		private final Reactor reactor;

		private final AtomicInteger state;


		Leader(final Reactor reactor) {
			this.reactor = reactor;
			this.state = new AtomicInteger(1);
		}

		/* (non-Javadoc)
		 * @see java.lang.Runnable#run()
		 */
		public final void run() {
			for (;;) {
				switch (this.reactor.status) {
				case ReactorStatus.SHUTTING_DOWN:
				case ReactorStatus.SHUTDOWN_REQUEST:
					doShutdown(null, false);

				case ReactorStatus.SHUTDOWN:
				case ReactorStatus.INACTIVE:
					return;

				default:
					break;
				}

				try {
					this.reactor.demultiplexer.select(this);
				} catch (InterruptedIOException ex) {
					doShutdown(ex, false);
					return;
				} catch (IOException ex) {
					doShutdown(ex, false);
					return;
				} catch (ClosedSelectorException ex) {	// ClosedSelectorException, IllegalArgumentException
					doShutdown(ex, false);
					return;
				} catch (Throwable ex) {
					this.reactor.handleUncaughtException(ex);
				}

				switch (this.reactor.status) {
				case ReactorStatus.SHUTTING_DOWN:
				case ReactorStatus.SHUTDOWN_REQUEST:
					doShutdown(null, false);

				case ReactorStatus.SHUTDOWN:
				case ReactorStatus.INACTIVE:
					return;

				default:
					break;
				}

				try {
					if (toFollowerState(this.reactor.follower.hasTask())) {
						this.reactor.follower.run();
						return;
					}
				} catch (ClosedSelectorException ex) {
					doShutdown(ex, false);
					return;
				} catch (Throwable ex) {
					this.reactor.handleUncaughtException(ex);
				}
			}
		}

		/*
		 * (non-Javadoc)
		 * @see com.chinmobi.aio.impl.nio.ThreadPool.NotEmptyCallback#onThreadPoolNotEmpty()
		 */
		public final void onThreadPoolNotEmpty() {
			this.state.set(0);
		}

		final void promote() {
			if (toLeaderState()) {
				this.reactor.threadPool.execute(this, true, false, (ThreadPool.NotEmptyCallback)this);
			}
		}

		public final void execute() throws AIOIllegalReactorException {
			synchronized (this) {
				switch (this.reactor.status) {
				case ReactorStatus.INACTIVE:
					break;

				case ReactorStatus.STARTING:
				case ReactorStatus.ACTIVE:
					return;

				default:
					throw new AIOIllegalReactorException("Status: " + this.reactor.status);
				}

				if (this.reactor.logger().isDebugEnabled()) {
					this.reactor.logger().debug().writeln().writeLogHeader("startReactor").flush();
				}

				this.reactor.statistics.updateStartTime();

				this.reactor.threadPool.start();

				this.reactor.status = ReactorStatus.ACTIVE;
				this.state.set(1);

				this.reactor.threadPool.execute(new Starter(this.reactor), true);
			}
		}

		public final boolean shutdown(final boolean waitForStopped) throws InterruptedException {
			boolean isStarting = false;

			synchronized (this) {
				switch (this.reactor.status) {
				case ReactorStatus.INACTIVE:
				case ReactorStatus.SHUTDOWN:
					return true;

				case ReactorStatus.STARTING:
					if (this.reactor.logger().isDebugEnabled()) {
						this.reactor.logger().debug().writeln().writeLogHeader("shutdownReactor").flush();
					}

					isStarting = true;
					this.reactor.status = ReactorStatus.SHUTDOWN_REQUEST;
					break;

				case ReactorStatus.ACTIVE:
					this.reactor.status = ReactorStatus.SHUTDOWN_REQUEST;

					this.reactor.demultiplexer.wakeup();

					if (this.reactor.logger().isDebugEnabled()) {
						this.reactor.logger().debug().writeln().writeLogHeader("shutdownReactor").flush();
					}

				default:
					break;
				}
			}

			if (isStarting) {
				doShutdown(null, true);
			}

			synchronized (this) {
				if (!waitForStopped) {
					return (this.reactor.status == ReactorStatus.SHUTDOWN);
				}

				while (this.reactor.status != ReactorStatus.SHUTDOWN) {
					wait();
				}

				return true;
			}
		}

		private final void doShutdown(final Throwable cause, boolean isStarting) {
			final Observer observer;

			synchronized (this) {
				observer = this.reactor.observer;
				this.reactor.observer = null;

				switch (this.reactor.status) {
				case ReactorStatus.STARTING:
					isStarting = true;

				case ReactorStatus.SHUTDOWN_REQUEST:
				case ReactorStatus.ACTIVE:
					this.reactor.status = ReactorStatus.SHUTTING_DOWN;

					this.reactor.acceptors.clear();
					this.reactor.timerEntries.shuttingDown();
					this.reactor.demultiplexer.shuttingDown();

					if (!isStarting && this.reactor.follower.hasTask()) {
						this.state.set(0);
						this.reactor.follower.promote(true);
						break;
					}

				//case ReactorStatus.SHUTTING_DOWN:
				default:
					this.reactor.status = ReactorStatus.SHUTDOWN;

					this.reactor.timerEntries.clear();
					this.reactor.demultiplexer.clear();

					if (this.reactor.logger().isDebugEnabled()) {
						this.reactor.logger().debug().writeln().writeLogHeader("reactorStopped.").flush();
					}

					notifyAll();
				}
			}

			this.reactor.threadPool.shutdown(true);
			closeSelector(this.reactor.demultiplexer.selector());

			if (observer != null) {
				try {
					observer.onAIOReactorStopped(this.reactor, cause);
				} catch (Throwable ignore) {
					this.reactor.handleUncaughtException(ignore);
				}
			} else if (cause != null) {
				this.reactor.handleUncaughtException(cause);
			}

			if (this.reactor.belongsGroup != null) {
				try {
					this.reactor.belongsGroup.onAIOReactorStopped(this.reactor, cause);
				} catch (Throwable ignore) {
					this.reactor.handleUncaughtException(ignore);
				}
			}
		}

		private static void closeSelector(final Selector selector) {
			try {
				selector.close();
			} catch (IOException ignore) {
			}
		}

		/*
		 * (non-Javadoc)
		 * @see com.chinmobi.aio.impl.nio.Demultiplexer.SelectInterceptor#onHandlerSelected(EventHandler handler, int index, int total)
		 */
		public final void onHandlerSelected(final EventHandler handler,
				final int index, final int total) {

			final int state = this.state.get();
			switch (state) {
			case -1:
			case  0:
				return;

			default: // case 1:
				this.reactor.follower.isCheckTimeout = false;

				if (this.reactor.threadPool.tryExecute(this.reactor.follower)) {
					this.state.set(-1);
				}
				return;
			}
		}

		private final boolean toFollowerState(final boolean hasTask) {
			for (;;) {
				final int state = this.state.get();
				if (hasTask) {
					if (!this.state.compareAndSet(state, 0)) {
						continue;
					}
					return true;
				} else {
					this.state.set(1);
					return false;
				}
			}
		}

		private final boolean toLeaderState() {
			for (;;) {
				final int state = this.state.get();
				switch (state) {
				case -1:
					if (!this.state.compareAndSet(state, 1)) {
						continue;
					}
				case  1:
					return false;

				default: // case 0:
					if (!this.state.compareAndSet(state, 1)) {
						continue;
					}
					return true;
				}
			}
		}

	}


	private static final class Follower implements Runnable {

		private final Reactor reactor;

		private volatile boolean isCheckTimeout;


		Follower(final Reactor reactor) {
			this.reactor = reactor;

			this.isCheckTimeout = true;
		}


		final boolean hasTask() {
			/*if (this.reactor.logger().isDebugEnabled()) {
				this.reactor.logger().debug().write(currentThreadId()).writeln("check Task...").flush();
			}*/
			this.isCheckTimeout = true;

			if (this.reactor.demultiplexer.hasSelectedHandler()) {
				return true;
			} else if (this.reactor.timerEntries.hasTimeoutEntry()) {
				return true;
			} else if (this.reactor.demultiplexer.hasRunnable()) {
				return true;
			} else {
				return false;
			}
		}

		final void promote(final boolean waitForPut) {
			this.reactor.threadPool.execute(this, true, waitForPut, null);
		}

		/* (non-Javadoc)
		 * @see java.lang.Runnable#run()
		 */
		public final void run() {
			int step = 0;

			for (;;) {
				switch (step) {
				case 0:
					if (runSelectedHandler()) break;
					step = 1;

				case 1:
					if (this.isCheckTimeout && runTimeoutEntry()) break;
					step = 2;

				case 2:
					if (runRunnableNode()) break;

				default:
					this.reactor.leader.promote();
					return;
				}
			}
		}

		private final boolean runSelectedHandler() {
			if (this.reactor.logger().isDebugEnabled()) {
				this.reactor.logger().debug().writeln().writeLogHeader("beginSelected: ").flush();
			}

			final EventHandler handler = this.reactor.demultiplexer.pollSelectedHandler();

			if (handler != null) {

				this.promote(false);

				if (this.reactor.logger().isDebugEnabled()) {
					final SelectionKey key = handler.selectionKey();
					if (key != null) try {
						final String ops = stringOfOps(handler.selectionKey().readyOps());
						this.reactor.logger().debug().write(currentThreadId()).
							write(handler.toString()).writeln(" readyOps: " + ops).flush();
					} catch (RuntimeException ignore) {
					}
				}

				handler.runSelected();

				if (this.reactor.logger().isDebugEnabled()) {
					this.reactor.logger().debug().writeLogHeader("endSelected.").flush();
				}

				return true;
			}

			return false;
		}

		private final boolean runTimeoutEntry() {
			if (this.reactor.logger().isDebugEnabled()) {
				this.reactor.logger().debug().writeln().writeLogHeader("beginTimeout: ").flush();
			}

			final TimerEntry entry = this.reactor.timerEntries.checkTimeoutEntry();

			if (entry != null) {

				this.promote(false);

				if (this.reactor.logger().isDebugEnabled()) {
					this.reactor.logger().debug().write(currentThreadId()).writeln(entry.toString()).flush();
				}

				final Runnable runnable = entry.getRunnable();
				if (runnable != null) {
					runnable.run();
				}

				if (this.reactor.logger().isDebugEnabled()) {
					this.reactor.logger().debug().writeLogHeader("endTimeout.").flush();
				}

				return true;
			}

			return false;
		}

		private final boolean runRunnableNode() {
			if (this.reactor.logger().isDebugEnabled()) {
				this.reactor.logger().debug().writeln().writeLogHeader("beginRunnable: ").flush();
			}

			final RunnableQueueNode runnableNode = this.reactor.demultiplexer.pollRunnable();

			if (runnableNode != null) {

				this.promote(false);

				if (this.reactor.logger().isDebugEnabled()) {
					String actionName = runnableNode.getActionName();
					if (actionName == null) {
						actionName = "RUN";
					}
					this.reactor.logger().debug().write(currentThreadId()).write(actionName).write((byte)':').writeln().
						write((byte)'\t').writeln(runnableNode.toString()).flush();
				}

				final Runnable runnable = runnableNode.getRunnable();
				if (runnable != null) {
					runnable.run();
				}

				if (this.reactor.logger().isDebugEnabled()) {
					this.reactor.logger().debug().writeLogHeader("endRunnable.").flush();
				}

				return true;
			}

			return false;
		}

		private static final String currentThreadId() {
			return ThreadPool.currentThreadId();
		}

		private static final String stringOfOps(final int ops) {
			final StringBuilder builder = new StringBuilder();

			boolean appended = false;
			if ((ops & SelectionKey.OP_READ) != 0) {
				if (appended) builder.append('_');
				builder.append("READ");
				appended = true;
			}
			if ((ops & SelectionKey.OP_WRITE) != 0) {
				if (appended) builder.append('_');
				builder.append("WRITE");
				appended = true;
			}
			if ((ops & SelectionKey.OP_CONNECT) != 0) {
				if (appended) builder.append('_');
				builder.append("CONNECT");
				appended = true;
			}
			if ((ops & SelectionKey.OP_ACCEPT) != 0) {
				if (appended) builder.append('_');
				builder.append("ACCEPT");
				appended = true;
			}
			if (!appended) {
				builder.append('0');
			}

			return builder.toString();
		}

	}


	private static final class Timer implements AIOTimer, TimerEntrySet.Observer {

		private final Reactor reactor;


		Timer(final Reactor reactor) {
			this.reactor = reactor;
		}


		public final AIOScheduledFuture schedule(final AIOTimerCallable callable,
				final long delay, final TimeUnit unit,
				final Object attachment) throws AIONotActiveException {
			final Lock lock = this.reactor.timerEntries.lock();
			lock.lock();
			try {
				this.reactor.assertActive();
				return this.reactor.timerEntries.schedule(this, callable,
						delay, unit, attachment).future();
			} finally {
				lock.unlock();
			}
		}

		public final void notifyTimerEntryAdded(final TimerEntrySet entrySet, final TimerEntry timerEntry) {
			this.reactor.demultiplexer.wakeup();
		}

		public final void notifyTimerEntryRuntimeException(final TimerEntry timerEntry,
				final RuntimeException ex) {
			this.reactor.handleUncaughtException(ex);
			timerEntry.fail(ex);
		}

	}

}
