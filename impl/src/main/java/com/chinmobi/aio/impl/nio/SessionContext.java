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

import java.util.concurrent.atomic.AtomicInteger;

import com.chinmobi.aio.impl.act.ActRequestFactory;
import com.chinmobi.aio.impl.util.ConcurrentLinkedQueue;
import com.chinmobi.aio.impl.util.IllegalQueueNodeStateException;
import com.chinmobi.aio.impl.util.TimerEntry;
import com.chinmobi.logging.Logger;

/**
 * @author <a href="mailto:yuzhaoping1970@gmail.com">Zhaoping Yu</a>
 *
 */
public final class SessionContext implements ExceptionHandler, Dispatcher {

	private static final int MAX_CACHED_SESSION = 8;

	private static final AtomicInteger SESSION_ID = new AtomicInteger(-1);

	private final Logger logger;

	private final Demultiplexer demultiplexer;

	private final Dispatcher dispatcher;

	private final SessionCreator creator;

	private final ActRequestFactory actRequestFactory;

	private final ConcurrentLinkedQueue<EventHandler.ActiveNode> freeSessions;
	private final AtomicInteger freeSessionsCount;

	private final AtomicInteger largestSessionCount;
	private final AtomicInteger activeSessionCount;


	public SessionContext(final Logger logger,
			final Demultiplexer demultiplexer) {
		this(logger, demultiplexer, null);
	}

	public SessionContext(final Logger logger,
			final Demultiplexer demultiplexer, final Dispatcher dispatcher) {
		this.logger = logger;

		this.demultiplexer = demultiplexer;

		this.dispatcher = dispatcher;

		this.creator = new SessionCreator(this);

		this.actRequestFactory = new ActRequestFactory(this);

		this.freeSessions = new ConcurrentLinkedQueue<EventHandler.ActiveNode>();
		this.freeSessionsCount = new AtomicInteger(0);

		this.largestSessionCount = new AtomicInteger(0);
		this.activeSessionCount = new AtomicInteger(0);
	}


	public final Logger logger() {
		return this.logger;
	}

	public final Demultiplexer demultiplexer() {
		return this.demultiplexer;
	}

	public final SessionCreator sessionCreator() {
		return this.creator;
	}

	public final void registerTimerEntry(final TimerEntry timerEntry) {
		timerEntry.registerTo(this.demultiplexer.timerEntries());
	}

	public final ActRequestFactory actRequestFactory() {
		return this.actRequestFactory;
	}

	/* (non-Javadoc)
	 * @see com.chinmobi.aio.impl.nio.ExceptionHandler#handleUncaughtException(Throwable ex)
	 */
	public final void handleUncaughtException(final Throwable ex) {
		this.logger().error().writeln().write(ThreadPool.currentThreadId()).write(ex).flush();
	}

	/*
	 * (non-Javadoc)
	 * @see com.chinmobi.aio.impl.nio.Dispatcher#dispatch(SessionContext currentContext)
	 */
	public final SessionContext dispatch(final SessionContext currentContext) {
		if (this.dispatcher != null) {
			final SessionContext context = this.dispatcher.dispatch(currentContext);
			if (context != null) {
				return context;
			}
		}
		if (currentContext != null) {
			return currentContext;
		}
		return this;
	}

	public final SessionContext dispatch() {
		if (this.dispatcher != null) {
			return this.dispatcher.dispatch(this);
		}
		return this;
	}

	// For test
	public final boolean containsFree(final Session session) {
		final EventHandler.ActiveNode node = session.handler().activeNode();
		return (node.belongsQueue() == this.freeSessions);
	}

	public final int getLargestSessionCount() {
		return this.largestSessionCount.get();
	}

	public final int getActiveSessionCount() {
		return this.activeSessionCount.get();
	}

	final Session createSession() {
		Session session = null;

		final EventHandler.ActiveNode node = this.freeSessions.poll();
		if (node != null) {
			this.freeSessionsCount.decrementAndGet();

			final Session.Handler handler = (Session.Handler)node.handler();
			session = handler.session();
		}

		if (session == null) {
			session = new Session(this);
		}

		incrementActiveSessionCount();

		return session.setId(SESSION_ID.incrementAndGet());
	}

	final void releaseSession(final Session session)
			throws IllegalQueueNodeStateException {
		this.activeSessionCount.decrementAndGet();

		if (this.freeSessionsCount.incrementAndGet() < MAX_CACHED_SESSION) {

			final EventHandler.ActiveNode node = session.handler().activeNode();
			this.freeSessions.add(node);
		}
	}

	private final void incrementActiveSessionCount() {
		final int activeCount = this.activeSessionCount.incrementAndGet();
		final int largestCount = this.largestSessionCount.get();

		if (activeCount > largestCount) {
			this.largestSessionCount.set(activeCount);
		}
	}

}
