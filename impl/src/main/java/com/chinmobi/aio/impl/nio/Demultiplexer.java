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
import java.nio.channels.CancelledKeyException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ClosedSelectorException;
import java.nio.channels.IllegalBlockingModeException;
import java.nio.channels.IllegalSelectorException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.locks.Lock;

import com.chinmobi.aio.AIONotActiveException;
import com.chinmobi.aio.impl.util.ConcurrentLinkedQueue;
import com.chinmobi.aio.impl.util.IllegalQueueNodeStateException;
import com.chinmobi.aio.impl.util.SmartLock;
import com.chinmobi.aio.impl.util.TimerEntrySet;
import com.chinmobi.logging.Logger;

/**
 * @author <a href="mailto:yuzhaoping1970@gmail.com">Zhaoping Yu</a>
 *
 */
public final class Demultiplexer implements EventHandlerCallback {

	public interface ActiveChecker {
		public void assertActive() throws AIONotActiveException;
	}

	public interface SelectInterceptor {
		public void onHandlerSelected(EventHandler handler, int index, int total);
	}


	private final Selector selector;

	private final TimerEntrySet timerEntries;

	private final ActiveChecker activeChecker;

	private final ConcurrentLinkedQueue<EventHandler.ActiveNode> selectedHandlers;

	private final ConcurrentLinkedQueue<EventHandler.ActiveNode> interestOpsPendings;
	private final Lock interestOpsLock;

	private final ConcurrentLinkedQueue<EventHandler.ActiveNode> registerPendings;
	private final Lock registerLock;

	private final ConcurrentLinkedQueue<EventHandler.ActiveNode> runnableNodes;

	private final long[] selectTimeout;

	private final Logger logger;


	public Demultiplexer(final Selector selector, final TimerEntrySet timerEntries,
			final ActiveChecker activeChecker,
			final boolean interestOpsQueueing,
			final boolean registerQueueing) {
		this(selector, timerEntries, activeChecker,
				interestOpsQueueing, registerQueueing, null);
	}

	public Demultiplexer(final Selector selector, final TimerEntrySet timerEntries,
			final ActiveChecker activeChecker,
			final boolean interestOpsQueueing, final boolean registerQueueing,
			final Logger logger) {
		super();

		this.selector = selector;

		this.timerEntries = timerEntries;

		this.activeChecker = activeChecker;

		this.selectedHandlers = new ConcurrentLinkedQueue<EventHandler.ActiveNode>();

		this.registerPendings = registerQueueing ?
				new ConcurrentLinkedQueue<EventHandler.ActiveNode>() : null;
		this.registerLock = registerQueueing ? null : new SmartLock();

		this.interestOpsPendings = interestOpsQueueing ?
				new ConcurrentLinkedQueue<EventHandler.ActiveNode>() : null;
		this.interestOpsLock = interestOpsQueueing ? null : new SmartLock();

		this.runnableNodes = new ConcurrentLinkedQueue<EventHandler.ActiveNode>();

		this.selectTimeout = new long[1];
		this.selectTimeout[0] = 0;

		this.logger = logger;
	}


	final TimerEntrySet timerEntries() {
		return this.timerEntries;
	}

	public final ActiveChecker activeChecker() {
		return this.activeChecker;
	}

	public final Selector selector() {
		return this.selector;
	}

	public final int select() throws InterruptedIOException, IOException, ClosedSelectorException {
		return select(null);
	}

	public final int select(final SelectInterceptor selectInterceptor)
			throws InterruptedIOException, IOException, ClosedSelectorException {

		/*if (this.logger != null && this.logger.isDebugEnabled()) {
			this.logger.debug().writeLogHeader("beginSelecting: ").flush();
		}*/

		if (this.timerEntries.adjustScheduleTimeout(this.selectTimeout) < 0) {
			this.selector.wakeup();
		} else {
			handleInterestOpsPendings();
		}

		int readyCount = 0;

		try {
			if (this.logger != null && this.logger.isDebugEnabled()) {
				this.logger.debug().write(currentThreadId()).writeln("select timeout: " + this.selectTimeout[0]).flush();
			}
			readyCount = this.selector.select(this.selectTimeout[0]);
		} catch (InterruptedIOException ex) {
			throw ex;
		} catch (IOException ex) {
			throw ex;
		} catch (RuntimeException ex) {	// ClosedSelectorException, IllegalArgumentException
			throw ex;
		}

		if (readyCount > 0) {
			putSelectedHandlers(this.selector.selectedKeys(), selectInterceptor, readyCount);
		}

		handleRegisterPendings();

		/*if (this.logger != null && this.logger.isDebugEnabled()) {
			this.logger.debug().writeLogHeader("endSelecting: " + readyCount).flush();
		}*/

		return readyCount;
	}

	public final void wakeup() {
		this.selector.wakeup();
	}


	public final boolean contains(final EventHandler handler) {
		final SelectionKey key = handler.selectionKey();
		if (key != null) {
			return this.selector.keys().contains(key);
		}
		if (this.registerPendings != null) {
			return (handler.activeNode().belongsQueue() == this.registerPendings);
		}
		return false;
	}

	public final EventHandler registerChannel(final SelectableChannel channel, final int ops,
			final EventHandler.Factory factory, final Object token,
			final boolean toProcess) throws ClosedChannelException, IOException {
		return registerChannel(channel, ops, factory, token, toProcess, true);
	}

	public final EventHandler registerChannel(final SelectableChannel channel, final int ops,
			final EventHandler.Factory factory, final Object token,
			final boolean toProcess, final boolean allowsPending)
			throws ClosedChannelException, IllegalBlockingModeException,
				IOException,
				IllegalSelectorException, IllegalArgumentException, CancelledKeyException,
				AIONotActiveException, IllegalQueueNodeStateException {

		final EventHandler handler = factory.createEventHandler(token);

		if (this.logger != null && this.logger.isDebugEnabled()) {
			this.logger.debug().write(currentThreadId()).writeln("register:").
				write((byte)'\t').writeln(handler.toString()).flush();
		}

		try {
			channel.configureBlocking(false);

			final SelectionKey key;

			final boolean registerQueueing = (this.registerPendings != null);
			if (registerQueueing) {
				key = null;
			} else {
				final Lock lock = this.registerLock;
				lock.lock();
				try {
					this.selector.wakeup();
					key = channel.register(this.selector, 0);
				} finally {
					lock.unlock();
				}
			}

			if (handler.registerTo(key, toProcess)) {

				putHandler(handler);
				handler.timerEntry().registerTo(this.timerEntries);

				handler.setInterestOps(ops, true);

				if (registerQueueing && allowsPending) {
					putRegisterPending(handler);
				}

			} else {
				throw new CancelledKeyException();
			}
		} catch (ClosedChannelException ex) {
			handler.fail(ex);
			throw ex;
		} catch (IOException ex) {
			handler.fail(ex);
			throw ex;
		} catch (CancelledKeyException ex) {
			handler.close();
			throw ex;
		} catch (IllegalBlockingModeException ex) {
			handler.fail(ex);
			throw ex;
		} catch (IllegalSelectorException ex) {
			handler.fail(ex);
			throw ex;
		} catch (RuntimeException ex) { // AIONotActiveException, IllegalQueueNodeStateException,
										// IllegalArgumentException
			handler.fail(ex);
			throw ex;
		}

		return handler;
	}

	public final EventHandler transferHandler(final EventHandler source,
			final int ops, final EventHandler.Factory factory, final Object token, final boolean toProcess)
			throws CancelledKeyException,
				AIONotActiveException, IllegalQueueNodeStateException {

		final EventHandler handler = factory.createEventHandler(token);

		if (this.logger != null && this.logger.isDebugEnabled()) {
			this.logger.debug().write(currentThreadId()).writeln("transfer:").
				write("\tfrom ").writeln(source.toString()).
				write("\tto   ").writeln(handler.toString()).flush();
		}

		try {
			final boolean registered = source.transferSelectionKey(handler, toProcess);

			transferHandler(source, handler);
			handler.timerEntry().registerTo(this.timerEntries);

			handler.setInterestOps(ops, true);

			if (!registered) {
				final boolean registerQueueing = (this.registerPendings != null);
				if (registerQueueing) {
					putRegisterPending(handler);
				} else {
					throw new CancelledKeyException();
				}
			}
		} catch (CancelledKeyException ex) {
			handler.close();
			source.close();
			throw ex;
		} catch (RuntimeException ex) { // AIONotActiveException, IllegalQueueNodeStateException,
										// IllegalArgumentException
			handler.close();
			source.fail(ex);
			throw ex;
		}

		return handler;
	}

	final void putHandler(final EventHandler handler)
			throws AIONotActiveException {
		this.activeChecker.assertActive();
		handler.setCallback(this);
	}

	private final void transferHandler(final EventHandler source, final EventHandler target)
			throws AIONotActiveException {
		this.activeChecker.assertActive();
		target.setCallback(this);
	}


	public final boolean containsInSelectedHandlers(final EventHandler handler) {
		return (handler.activeNode().belongsQueue() == this.selectedHandlers);
	}

	private final void putSelectedHandlers(final Set<SelectionKey> selectedKeys,
			final SelectInterceptor selectInterceptor, final int readyCount) {
		final Iterator<SelectionKey> keysIter = selectedKeys.iterator();

		int index = -1;
		while (keysIter.hasNext()) {
			final SelectionKey key = keysIter.next();

			final EventHandler handler = (EventHandler)key.attachment();
			if (handler != null) {
				if (putSelectedHandler(handler, false) && (selectInterceptor != null)) {
					++index;
					selectInterceptor.onHandlerSelected(handler, index, readyCount);
				}
			} else {
				key.cancel();

				try {
					key.channel().close();
				} catch (IOException ignore) {
				}
			}
		}

		selectedKeys.clear();
	}

	final boolean activateHandler(final EventHandler handler) {
		return putSelectedHandler(handler, true);
	}

	private final boolean putSelectedHandler(final EventHandler handler, final boolean toImitate) {
		if (handler.toSelectedState(toImitate)) {
			final EventHandler.ActiveNode activeNode = handler.activeNode();
			for (;;) {
				final int status = this.selectedHandlers.offer(activeNode, 's', null);

				if (status < 0) {
					activeNode.dequeue();
					continue;
				}

				break;
			}

			if (toImitate) {
				this.selector.wakeup();
			}

			return true;
		}
		return false;
	}

	public final boolean hasSelectedHandler() {
		return (this.selectedHandlers.peek() != null);
	}

	public final EventHandler pollSelectedHandler() {
		final EventHandler.ActiveNode activeNode = this.selectedHandlers.poll();
		if (activeNode != null) {
			return activeNode.handler();
		}
		return null;
	}


	public final void putRunnable(final EventHandler.ActiveNode runnableNode, final boolean checkActive)
			throws AIONotActiveException, IllegalQueueNodeStateException {
		putRunnable(runnableNode, 'x', "RUN", checkActive);
	}

	public final void putRunnable(final EventHandler.ActiveNode runnableNode,
			final char traceCode, final String actionName,
			final boolean checkActive) throws AIONotActiveException, IllegalQueueNodeStateException {
		for (;;) {
			if (checkActive) {
				this.activeChecker.assertActive();
			}

			final int status = this.runnableNodes.offer(runnableNode, traceCode, actionName);

			if (status < 0) {
				runnableNode.dequeue();
				continue;
			}

			this.selector.wakeup();

			break;
		}
	}

	public final boolean hasRunnable() {
		return (this.runnableNodes.peek() != null);
	}

	public final RunnableQueueNode pollRunnable() {
		return this.runnableNodes.poll();
	}

	private final void clearRunnables() {
		RunnableQueueNode node = this.runnableNodes.poll();
		while (node != null) {
			try {
				node.close();
			} catch (RuntimeException ignore) {
			}

			node = this.runnableNodes.poll();
		}
	}


	private final void putRegisterPending(final EventHandler handler) {
		final EventHandler.ActiveNode activeNode = handler.activeNode();
		for (;;) {
			final int status = this.registerPendings.offer(activeNode, 'r', null);

			if (status < 0) {
				activeNode.dequeue();
				continue;
			}

			this.selector.wakeup();

			break;
		}
	}

	private final void handleRegisterPendings() {
		if (this.registerPendings == null) {
			final Lock lock = this.registerLock;
			lock.lock();
			lock.unlock();
			return;
		}

		EventHandler.ActiveNode activeNode = this.registerPendings.poll();
		while (activeNode != null) {
			try {
				if (this.logger != null && this.logger.isDebugEnabled()) {
					this.logger.debug().write(currentThreadId()).writeln("doRegister:").
						write((byte)'\t').writeln(activeNode.handler().toString()).flush();
				}

				if (activeNode.toRegister(this.selector) == 0) {
					eventHandlerCancelled(activeNode.handler());
				}
			} catch (ClosedChannelException ex) {
				putHandlerFailNode(activeNode.handler(), ex);
			} catch (RuntimeException ex) { // IllegalBlockingModeException, IllegalSelectorException
											// CancelledKeyException, IllegalArgumentException
											// IllegalStateException
				putHandlerFailNode(activeNode.handler(), ex);
			}

			activeNode = this.registerPendings.poll();
		}
	}

	private final void putHandlerFailNode(final EventHandler handler, final Throwable cause) {
		final EventHandler.FailNode failNode = new EventHandler.FailNode(handler, cause);

		putRunnable(failNode, 'f', "EVENT_HANDLER_FAIL", false);
	}

	private final void cancelAllRegisterPendings(final boolean isClose) {
		if (this.registerPendings == null) {
			return;
		}

		EventHandler.ActiveNode activeNode;
		while ((activeNode = this.registerPendings.poll()) != null) {
			if (!isClose) {
				try {
					activeNode.cancel();
					eventHandlerCancelled(activeNode.handler());
					continue;
				} catch (RuntimeException ignore) {
				}
			}

			try {
				activeNode.close();
			} catch (RuntimeException ignore) {
			}
		}
	}


	public final boolean containsInInterestOpsPendings(final EventHandler handler) {
		if (this.interestOpsPendings != null) {
			return (handler.activeNode().belongsQueue() == this.interestOpsPendings);
		}
		return false;
	}

	public final void handleInterestOpsPendings() {
		if (this.interestOpsPendings == null) {
			final Lock lock = this.interestOpsLock;
			lock.lock();
			lock.unlock();
			return;
		}

		EventHandler.ActiveNode activeNode = this.interestOpsPendings.poll();
		while (activeNode != null) {
			activeNode.handler().toSetInterestOps();

			activeNode = this.interestOpsPendings.poll();
		}
	}

	/*
	 * (non-Javadoc)
	 * @see com.chinmobi.aio.impl.nio.EventHandlerCallback#eventHandlerChangedInterestOps(EventHandler handler, int ops)
	 */
	public final boolean eventHandlerChangedInterestOps(final EventHandler handler, final int ops) {
		if (this.logger != null && this.logger.isDebugEnabled()) {
			final String strOps = stringOfOps(ops);
			this.logger.debug().write(currentThreadId()).writeln("changeInterestOps: " + strOps).
				write((byte)'\t').writeln(handler.toString()).flush();
		}

		final boolean interestOpsQueueing = (this.interestOpsPendings != null);

		if (!interestOpsQueueing) {
			final Lock lock = this.interestOpsLock;
			lock.lock();
			try {
				this.selector.wakeup();
				handler.selectionKey().interestOps(ops);
			} finally {
				lock.unlock();
			}
			handler.traceSetOps(ops);
			return true;
		} else {
			final EventHandler.ActiveNode activeNode = handler.activeNode();
			for (;;) {
				final int status = this.interestOpsPendings.offer(activeNode, 'o', null);

				if (status < 0) {
					activeNode.dequeue();
					continue;
				}

				this.selector.wakeup();

				break;
			}

			return false;
		}
	}

	/*
	 * (non-Javadoc)
	 * @see com.chinmobi.aio.impl.nio.EventHandlerCallback#eventHandlerCancelled(EventHandler handler)
	 */
	public final void eventHandlerCancelled(final EventHandler handler) {
		if (this.logger != null && this.logger.isDebugEnabled()) {
			this.logger.debug().write(currentThreadId()).writeln("cancelled:").
				write((byte)'\t').writeln(handler.toString()).flush();
		}

		final EventHandler.ActiveNode activeNode = handler.activeNode();
		putRunnable(activeNode, 'c', "CANCEL_EVENT_HANDLER", false);
	}

	/*
	 * (non-Javadoc)
	 * @see com.chinmobi.aio.impl.nio.EventHandlerCallback#eventHandlerClosed(EventHandler handler, boolean onProcessing, int atCase)
	 */
	public final void eventHandlerClosed(final EventHandler handler, final boolean onProcessing, final int atCase) {
		if (onProcessing && this.logger != null && this.logger.isDebugEnabled()) {
			this.logger.debug().write(currentThreadId()).write(stringOfClosedCase(atCase)).writeln(":").
				write((byte)'\t').writeln(handler.toString()).flush();
		}
	}

	private static final String stringOfClosedCase(final int atCase) {
		switch (atCase) {
		case EventHandlerCallback.CASE_FAILED: return "failed";
		case EventHandlerCallback.CASE_RELEASED: return "released";
		case EventHandlerCallback.CASE_TIMEOUT: return "timeout";
		default:
			return "closed";
		}
	}


	public final void shuttingDown() {
		cancelAllSelectionKeys(false);

		cancelAllRegisterPendings(false);
	}

	public final void clear() {
		cancelAllSelectionKeys(true);

		cancelAllRegisterPendings(true);

		clearRunnables();
	}

	private final void cancelAllSelectionKeys(final boolean onClear) {
		try {
			final Set<SelectionKey> keys = this.selector.keys();

			if (!keys.isEmpty()) {
				final Iterator<SelectionKey> keysIter = keys.iterator();

				while (keysIter.hasNext()) {
					final SelectionKey key = keysIter.next();

					if (!key.isValid()) {
						continue;
					}

					final EventHandler handler = (EventHandler)key.attachment();
					if (handler != null) {
						try {
							if (handler.cancel() >= 0 && onClear) {
								handler.close();
							}
							continue;
						} catch (RuntimeException ignore) {
						}
					}

					key.cancel();
					try {
						key.channel().close();
					} catch (IOException ignore) {
					}
				}
			}

		} catch (ClosedSelectorException ignore) {
		}
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
