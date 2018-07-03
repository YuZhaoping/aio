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
import java.nio.channels.CancelledKeyException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;

import com.chinmobi.aio.impl.Constants;
import com.chinmobi.aio.impl.util.TimerEntry;

/**
 * @author <a href="mailto:yuzhaoping1970@gmail.com">Zhaoping Yu</a>
 *
 */
public abstract class EventHandler {

	static final int ENABLE_TRACE = 1;
	private final StringBuilder traceBuffer;


	public interface Factory {
		public EventHandler createEventHandler(Object token);
	}


	public interface Executor {
		public int handlerDoExecute(Object object);
	}


	public static class ActiveNode extends RunnableQueueNode implements Runnable {

		protected final EventHandler handler;


		protected ActiveNode(final EventHandler handler) {
			super();
			this.handler = handler;
		}


		public final EventHandler handler() {
			return this.handler;
		}

		@Override
		public final void close() {
			this.handler.close();
		}

		@Override
		public final Runnable getRunnable() {
			final Runnable r = this.handler.getRunnable();
			if (r != null) {
				return r;
			}

			return this.handler.isActive() ? r : this;
		}

		@Override
		public final int cancel() {
			return this.handler.cancel();
		}

		@Override
		protected final void onEnqueued(final char traceCode) {
			if (ENABLE_TRACE != 0 && traceCode != 's') synchronized (this.handler) {
				this.handler.traceBuffer.append('<').append(traceCode);
			}
		}

		@Override
		protected final void onDequeued(final char traceCode) {
			if (ENABLE_TRACE != 0 && traceCode != 's') synchronized (this.handler) {
				this.handler.traceBuffer.append(' ').append(traceCode).append('>');
			}
		}

		final int toRegister(final Selector selector) throws ClosedChannelException {
			return this.handler.doRegister(selector);
		}

		/*
		 * (non-Javadoc)
		 * @see java.lang.Runnable#run()
		 */
		public void run() {
			this.handler.close();
		}

		@Override
		public final String toString() {
			return this.handler.toString();
		}

	}


	static final class FailNode extends ActiveNode {

		private final Throwable cause;


		FailNode(final EventHandler handler, final Throwable cause) {
			super(handler);
			this.cause = cause;
		}


		@Override
		public final void run() {
			this.handler.fail(this.cause);
		}

	}


	private static final class PrivateTimerEntry extends TimerEntry implements Runnable {

		private final EventHandler handler;


		private PrivateTimerEntry(final EventHandler handler) {
			super();
			this.handler = handler;
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
		protected final void onScheduled() {
			if (ENABLE_TRACE != 0) synchronized (this.handler) { this.handler.traceBuffer.append('^'); }
		}

		@Override
		protected final void onCancelled() {
			if (ENABLE_TRACE != 0) synchronized (this.handler) { this.handler.traceBuffer.append('$'); }
		}

		@Override
		protected final void onDiscarded() {
			if (ENABLE_TRACE != 0) synchronized (this.handler) { this.handler.traceBuffer.append('%'); }
		}

		@Override
		public final void fail(final Throwable cause) {
			this.handler.cancel();
		}

		@Override
		protected final int checkTimeout(final long now, final long[] delayTime) {
			return this.handler.checkTimeout(now, delayTime);
		}

		private final int doCheckTimeout(final long now, final long[] delayTime) {
			return super.checkTimeout(now, delayTime);
		}

		/*
		 * (non-Javadoc)
		 * @see java.lang.Runnable#run()
		 */
		public final void run() {
			this.handler.runTimeout();
		}

		@Override
		public final String toString() {
			final StringBuilder builder = new StringBuilder();

			builder.append("TimerEntry: [");
			builder.append(this.handler.toString());
			builder.append(" timeout: ").append(getTimeout());
			builder.append("]");

			return builder.toString();
		}

	}


	private static final int STATE_CLOSED = -1;
	private static final int STATE_REGISTERED = 0;
	private static final int STATE_IDLE = 0;
	private static final int STATE_SELECTED = 1;
	private static final int STATE_PROCESSING = 2;
	private static final int STATE_PENDING = 3;
	private static final int STATE_INVALID = 4;
	private static final int STATE_TIMEOUT = 5;
	private static final int STATE_CANCELLED_0 = 6;
	private static final int STATE_CANCELLED_1 = 7;
	private static final int STATE_INACTIVE = 8;


	private final ActiveNode activeNode;
	private final PrivateTimerEntry timerEntry;

	private EventHandlerCallback callback;
	private int state;

	private int timerModCount;

	private SelectionKey selectionKey;
	private int interestOps;


	protected EventHandler() {
		super();
		this.activeNode = new ActiveNode(this);
		this.timerEntry = new PrivateTimerEntry(this);
		this.state = STATE_INACTIVE;

		this.traceBuffer = (ENABLE_TRACE != 0) ? new StringBuilder() : null;
	}


	public final ActiveNode activeNode() {
		return this.activeNode;
	}

	final TimerEntry timerEntry() {
		return this.timerEntry;
	}

	final void setCallback(final EventHandlerCallback callback) {
		this.callback = callback;
	}

	final int generateTimerModCount(final boolean atIdleCase) {
		synchronized (this) {
			if (atIdleCase && this.state != STATE_IDLE) {
				return 0;
			}

			++this.timerModCount;
			if (this.timerModCount == 0) {
				this.timerModCount = 1;
			}
			return this.timerModCount;
		}
	}

	private final int setExpectedTimerModCount() {
		final int modCount = generateTimerModCount(false);

		this.timerEntry.setExpectedModCount(modCount);
		toSetTimerExpectedModCount(modCount);

		return modCount;
	}

	public final SelectionKey selectionKey() {
		synchronized (this) {
			return this.selectionKey;
		}
	}


	public abstract SelectableChannel selectableChannel();


	public Runnable getRunnable() {
		return null;
	}

	public final int cancel() {
		int cancelled = 1;
		int modCount = 0;

		synchronized (this) {
			if (ENABLE_TRACE != 0) {
				this.traceBuffer.append(' ').append('C').append(this.state);
			}

			switch (this.state) {
			case STATE_CLOSED:
			case STATE_CANCELLED_0: case STATE_CANCELLED_1:
				return 0;

			case STATE_PENDING:
				this.state = STATE_CANCELLED_1;
				if (ENABLE_TRACE != 0) { this.traceBuffer.append('~').append(this.state); }

				cancelled = -1;
				break;

			case STATE_TIMEOUT:
			case STATE_SELECTED: case STATE_PROCESSING:
				this.state = STATE_INVALID;
				if (ENABLE_TRACE != 0) { this.traceBuffer.append('~').append(this.state); }

				cancelled = -1;
				break;

			case STATE_INVALID:
				return -1;

			default: // STATE_IDLE, STATE_INACTIVE
				this.state = STATE_CANCELLED_0;
				if (ENABLE_TRACE != 0) { this.traceBuffer.append('~').append(this.state); }

				onCancelled();

				final EventHandlerCallback callback = this.callback;
				if (callback != null) {
					callback.eventHandlerCancelled(this);
				}

				modCount = setExpectedTimerModCount();
			}

			final SelectionKey key = this.selectionKey;
			if (key != null) {
				cancel(key);
			}
		}

		if (cancelled > 0) {
			this.timerEntry.cancel(modCount);
			toCancelTimer(modCount);
		}

		return cancelled;
	}

	public final void close() {
		if (doClose(EventHandlerCallback.CASE_CLOSED)) {
			onClosed();
		}
	}

	public final boolean release() {
		if (doClose(EventHandlerCallback.CASE_RELEASED)) {
			onReleased();
			return true;
		}
		return false;
	}

	public final boolean fail(Throwable cause) {
		if (cause != null) {
			cause = new ThrowableWrapper(toString(), cause);
		}
		if (doClose(EventHandlerCallback.CASE_FAILED)) {
			onFailed(cause);
			return true;
		}
		return false;
	}

	public final boolean isOpen() {
		return isActive();
	}

	public final synchronized boolean isTimeout() {
		return (this.state == STATE_TIMEOUT);
	}


	final synchronized boolean isActive() {
		switch (this.state) {
		case STATE_CLOSED:
		case STATE_INVALID: case STATE_CANCELLED_0: case STATE_CANCELLED_1:
		case STATE_INACTIVE:
			return false;

		default: return true;
		}
	}

	final synchronized void reset() {
		this.state = STATE_INACTIVE;
		this.timerEntry.setTimeout(0);
		if (ENABLE_TRACE != 0) { this.traceBuffer.delete(0, this.traceBuffer.length()); }
	}

	final synchronized boolean toPendingState() {
		switch (this.state) {
		case STATE_CLOSED:
		case STATE_INVALID: case STATE_CANCELLED_0: case STATE_CANCELLED_1:
			return false;

		default:
			if (ENABLE_TRACE != 0) {
				this.traceBuffer.append('P').append(this.state).append('~').append(STATE_PENDING);
			}
			this.state = STATE_PENDING;

			return true;
		}
	}

	final boolean registerTo(final SelectionKey key, final boolean toProcess) {
		synchronized (this) {
			if (ENABLE_TRACE != 0) { this.traceBuffer.append('R').append(this.state); }

			switch (this.state) {
			case STATE_INACTIVE:
			case STATE_PENDING:
				break;

			case STATE_CANCELLED_0: case STATE_CANCELLED_1:
				if (key != null) cancel(key);
				throw new CancelledKeyException();

			default:
				if (key != null) cancel(key);
				throw new IllegalStateException("Illegal State: " + this.state);
			}

			this.selectionKey = key;

			if (toProcess) {
				this.state = STATE_PROCESSING;
			} else if (key == null) {
				this.state = STATE_PENDING;
			} else {
				this.state = STATE_REGISTERED;
			}
			if (ENABLE_TRACE != 0) { this.traceBuffer.append('~').append(this.state); }

			this.interestOps = 0;

			if (key != null) {
				key.attach(this);
			}

			return true;
		}
	}

	final boolean transferSelectionKey(final EventHandler target, final boolean toProcess) {
		final int modCount = setExpectedTimerModCount();

		this.timerEntry.cancel(modCount);
		toCancelTimer(modCount);

		this.activeNode.dequeue();

		synchronized (this) {
			if (ENABLE_TRACE != 0) {
				this.traceBuffer.append(' ').append('F').append(this.state);
			}

			switch (this.state) {
			case STATE_INVALID: case STATE_CANCELLED_0: case STATE_CANCELLED_1:
				target.onClearSelectionKey();
				throw new CancelledKeyException();

			case STATE_CLOSED:
			case STATE_INACTIVE:
				target.onClearSelectionKey();
				throw new IllegalStateException("Illegal State: " + this.state);

			default:
				this.state = STATE_INACTIVE;

				if (ENABLE_TRACE != 0) { this.traceBuffer.append('~').append(this.state); }
			}

			if (ENABLE_TRACE != 0) {
				target.traceBuffer.append(this.traceBuffer).append('.');
			}

			onClearSelectionKey();

			final SelectionKey key = this.selectionKey;
			if (key != null) {
				this.selectionKey = null;

				key.attach(null);

				return target.registerTo(key, toProcess);
			}

			target.registerTo(key, toProcess);
			return false;
		}
	}


	final boolean toSelectedState(final boolean toImitate) {
		int readyOps = 0;
		int modCount = 0;

		synchronized (this) {
			if (ENABLE_TRACE != 0) {
				this.traceBuffer.append(' ');
				this.traceBuffer.append(toImitate ? 'A' : 'S');
				this.traceBuffer.append(this.state);
			}

			switch (this.state) {
			case STATE_CLOSED:
			case STATE_INVALID: case STATE_CANCELLED_0: case STATE_CANCELLED_1:
			case STATE_INACTIVE:

			case STATE_PENDING: case STATE_TIMEOUT:
				return false;

			case STATE_SELECTED: case STATE_PROCESSING:
				return false;

			default: // STATE_IDLE
				this.state = STATE_SELECTED;
				if (ENABLE_TRACE != 0) {
					final int length = this.traceBuffer.length();
					if (length > 1024) {
						this.traceBuffer.delete(0, length - 2);
					}

					this.traceBuffer.append('~').append(this.state);
				}

				final SelectionKey key = this.selectionKey;
				if (key != null) {
					if (toImitate) {
						key.selector().wakeup();
					}

					// Never throw CancelledKeyException
					key.interestOps(0);
					readyOps = key.readyOps();

					if (ENABLE_TRACE != 0) {
						this.traceBuffer.append('#').append(readyOps);
					}
				}

				modCount = setExpectedTimerModCount();
			}
		}

		if (readyOps != 0) {
			this.timerEntry.cancel(modCount);
		}

		onSelected(readyOps, modCount);

		return true;
	}

	public final int runSelected() {
		try {
			final SelectionKey key = acquireProcessKey();
			if (key != null) {
				final int status = process(key);

				if (status >= 0) {
					if (!releaseProcess(status)) {
						close();
					}
				} else {
					traceStatus(status);
				}
				return 1;
			}

			traceEnd();

			if (!isActive()) {
				close();
			} else {
				return 0;
			}
		} catch (CancelledKeyException ex) {
			close();
		} catch (RuntimeException ex) { // handler's callback runtime-exceptions
			fail(ex);
		} catch (Throwable error) {
			fail(error);
		}
		return -1;
	}

	public final int execute(final Executor executor, final Object object) {
		try {
			if (acquireProcess('X')) {
				final int status = executor.handlerDoExecute(object);

				if (status >= 0) {
					if (!releaseProcess(status)) {
						close();
					}
				} else {
					traceStatus(status);
				}
				return 1;
			}

			traceEnd();

			if (!isActive()) {
				close();
			} else {
				return 0;
			}
		} catch (CancelledKeyException ex) {
			close();
		} catch (RuntimeException ex) {	// executor's callback exceptions
			fail(ex);
		} catch (Throwable error) {
			fail(error);
		}
		return -1;
	}

	private final SelectionKey acquireProcessKey() {
		synchronized (this) {
			if (ENABLE_TRACE != 0) { this.traceBuffer.append(' ').append('{').append('A').append(this.state); }

			switch (this.state) {
			case STATE_CLOSED:
			case STATE_INVALID: case STATE_CANCELLED_0: case STATE_CANCELLED_1:

			case STATE_INACTIVE:

				return null;

			case STATE_PROCESSING:
				return null;

			default: // STATE_SELECTED, STATE_PENDING, STATE_TIMEOUT, STATE_IDLE
				this.state = STATE_PROCESSING;

				if (ENABLE_TRACE != 0) {
					this.traceBuffer.append('~').append(this.state);
					this.traceBuffer.append('#').append(this.selectionKey.readyOps());
				}
			}

			return this.selectionKey;
		}
	}

	private final boolean acquireProcess(final char traceCode) {
		synchronized (this) {
			if (ENABLE_TRACE != 0) { this.traceBuffer.append('{').append(traceCode).append(this.state); }

			switch (this.state) {
			case STATE_CLOSED:
			case STATE_INVALID: case STATE_CANCELLED_0: case STATE_CANCELLED_1:

			case STATE_INACTIVE:

				return false;

			case STATE_PROCESSING:
				return true;

			case STATE_PENDING:
				return true;

			default: // STATE_SELECTED, STATE_TIMEOUT, STATE_IDLE
				this.state = STATE_PROCESSING;

				if (ENABLE_TRACE != 0) {
					this.traceBuffer.append('~').append(this.state);
				}
			}

			return true;
		}
	}

	private final void traceStatus(final int status) {
		if (ENABLE_TRACE != 0) synchronized (this) {
			this.traceBuffer.append(' ').append('=').append(status);
		}
	}

	private final void traceEnd() {
		if (ENABLE_TRACE != 0) synchronized (this) {
			this.traceBuffer.append(' ').append('}');
		}
	}

	private final boolean releaseProcess(final int status) {
		int ops = 0;
		int modCount = 0;

		synchronized (this) {
			final SelectionKey key = this.selectionKey;

			if (ENABLE_TRACE != 0) {
				this.traceBuffer.append(' ').append('=').append(status);
				this.traceBuffer.append('}').append(this.state);
			}

			switch (this.state) {
			case STATE_CLOSED:
			case STATE_INVALID: case STATE_CANCELLED_0: case STATE_CANCELLED_1:
				return false;

			case STATE_PENDING:
				if (key != null) {
					break;
				}

			case STATE_TIMEOUT: case STATE_SELECTED:

			case STATE_IDLE:

			case STATE_INACTIVE:
				return true;

			default: // STATE_PROCESSING
				break;
			}

			if (key != null) {
				this.state = STATE_IDLE;

				ops = this.interestOps;

				if (ENABLE_TRACE != 0) {
					this.traceBuffer.append('~').append(this.state);
					this.traceBuffer.append('?').append(ops);
				}

				if (ops != 0 && !doSetInterestOps(ops)) {
					return true;
				}

				modCount = setExpectedTimerModCount();
			} else {
				this.state = STATE_PENDING;
				if (ENABLE_TRACE != 0) {
					this.traceBuffer.append('~').append(this.state);
				}

				return true;
			}
		}

		if (modCount != 0) {
			scheduleTimer(ops, modCount);
		}

		return true;
	}

	protected final boolean advanceSelect() {
		return releaseProcess(0);
	}


	public final int getInterestOps() {
		synchronized (this) {
			return this.interestOps;
		}
	}

	public final boolean setInterestOps(final int ops, final boolean toEffect) {
		int newOps = 0;
		int modCount = 0;

		synchronized (this) {
			if ((this.interestOps & ops) != ops) {
				this.interestOps |= ops;

				if (ENABLE_TRACE != 0) { traceSetOps('+', ops); }

				if (toEffect && this.state == STATE_IDLE) {
					if (!doSetInterestOps(this.interestOps)) {
						return true;
					}

					newOps = this.interestOps;
					modCount = setExpectedTimerModCount();
				}
			} else {
				return false;
			}
		}

		if (modCount != 0) {
			scheduleTimer(newOps, modCount);
		}
		return true;
	}

	public final boolean clearInterestOps(final int ops, final boolean toEffect) {
		int newOps = 0;
		int modCount = 0;

		synchronized (this) {
			if ((this.interestOps & ops) != 0) {
				this.interestOps &= ~ops;

				if (ENABLE_TRACE != 0) { traceSetOps('-', ops); }

				if (toEffect && this.state == STATE_IDLE) {
					if (!doSetInterestOps(this.interestOps)) {
						return true;
					}

					newOps = this.interestOps;
					modCount = setExpectedTimerModCount();
				}
			} else {
				return false;
			}
		}

		if (modCount != 0) {
			scheduleTimer(newOps, modCount);
		}
		return true;
	}

	private final void traceSetOps(final char cmd, final int ops) {
		if (ENABLE_TRACE != 0) {
			this.traceBuffer.append(' ').append('?').append(cmd).append(ops);
			this.traceBuffer.append('=').append(this.interestOps);
			this.traceBuffer.append('@').append(this.state);
		}
	}

	final void traceSetOps(final int ops) {
		if (ENABLE_TRACE != 0) { this.traceBuffer.append('o').append(ops); }
	}

	private final boolean doSetInterestOps(final int ops) {
		final SelectionKey key = this.selectionKey;
		if (key != null) {
			final EventHandlerCallback callback = this.callback;
			if (callback != null) {
				return callback.eventHandlerChangedInterestOps(this, ops);
			} else {
				key.selector().wakeup();

				// Never throw CancelledKeyException
				key.interestOps(ops);

				return true;
			}
		}
		return false;
	}

	final void toSetInterestOps() {
		int ops = 0;
		int modCount = 0;

		synchronized (this) {
			switch (this.state) {
			case STATE_IDLE:
				final SelectionKey key = this.selectionKey;
				if (key != null) {
					ops = this.interestOps;
					if (ENABLE_TRACE != 0) { this.traceBuffer.append('o').append(ops); }

					if (ops != 0) {
						// Never throw CancelledKeyException
						key.interestOps(ops);
					}

					modCount = setExpectedTimerModCount();
				}
				break;

			default:
				if (ENABLE_TRACE != 0) {
					this.traceBuffer.append('o').append('@').append(this.state);
				}
				return;
			}
		}

		if (modCount != 0) {
			scheduleTimer(ops, modCount);
		}
	}

	private final void scheduleTimer(final int interestOps, final int modCount) {
		if (interestOps != 0) {
			this.timerEntry.schedule(modCount);
			toScheduleTimer(interestOps, modCount);
		} else {
			this.timerEntry.cancel(modCount);
			toCancelTimer(modCount);
		}
	}

	private final int doRegister(final Selector selector) throws ClosedChannelException {
		int ops = 0;
		int modCount = 0;

		synchronized (this) {
			if (ENABLE_TRACE != 0) {
				this.traceBuffer.append('r').append(this.state);
			}

			switch (this.state) {
			case STATE_CLOSED: case STATE_INVALID:
				return -1;

			case STATE_CANCELLED_0: case STATE_CANCELLED_1:
				return 0;

			case STATE_PENDING:
				this.state = STATE_REGISTERED;

			case STATE_IDLE:
				ops = this.interestOps;

			default:
				if (this.selectionKey == null) {
					if (ENABLE_TRACE != 0) {
						this.traceBuffer.append('~').append(this.state);
						this.traceBuffer.append('?').append(ops);
					}

					try {
						doRegister(selector, ops);

						modCount = setExpectedTimerModCount();
					} catch (ClosedChannelException ex) {
						final int state = this.state;
						this.state = STATE_INVALID;
						if (state == STATE_IDLE) throw ex;
						return -1;
					} catch (RuntimeException ex) {
						final int state = this.state;
						this.state = STATE_INVALID;
						if (state == STATE_IDLE) throw ex;
						return -1;
					}
				} else {
					return 1;
				}
			}
		}

		if (modCount != 0) {
			scheduleTimer(ops, modCount);
		}
		return 1;
	}

	private final void doRegister(final Selector selector, final int ops) throws ClosedChannelException {
		final SelectableChannel channel = selectableChannel();
		if (channel != null) {
			final SelectionKey key = channel.register(selector, ops);

			this.selectionKey = key;

			key.attach(this);

		} else {
			throw new IllegalStateException("Null Selectable Channel");
		}
	}


	public final void setTimeout(final long timeout) {
		int modCount = 0;

		synchronized (this) {
			if (this.timerEntry.getTimeout() == timeout) {
				return;
			}

			this.timerEntry.setTimeout(timeout);

			if (this.state == STATE_IDLE) {
				modCount = generateTimerModCount(false);
				this.timerEntry.setExpectedModCount(modCount);
			} else {
				return;
			}
		}

		if (modCount != 0) {
			this.timerEntry.cancel(modCount);
			this.timerEntry.schedule(modCount);
		}
	}


	private final synchronized int getState() {
		return this.state;
	}

	private final int checkTimeout(final long now, final long[] delayTime) {
		switch (getState()) {
		case STATE_TIMEOUT:
			return  1;

		case STATE_IDLE:
			final int status = this.timerEntry.doCheckTimeout(now, delayTime);
			if (status > 0) {
				synchronized (this) {
					switch (this.state) {
					case STATE_TIMEOUT:
						return  1;

					case STATE_IDLE:
						this.state = STATE_TIMEOUT;

						if (ENABLE_TRACE != 0) { this.traceBuffer.append(' ').append('T'); }

						final SelectionKey key = this.selectionKey;
						if (key != null) {
							// Never throw CancelledKeyException
							key.interestOps(0);
						}
						break;

					default:
						return -1;
					}
				}
			}
			return status;

		default:
			return -1;
		}
	}

	private final void runTimeout() {
		try {
			if (acquireProcess('T')) {
				final int status = onTimeout();

				if (status >= 0) {
					if (!releaseProcess(status)) {
						close();
					}
				} else {
					traceStatus(status);
				}
				return;
			}

			traceEnd();

			if (!isActive()) {
				close();
			}
		} catch (CancelledKeyException ex) {
			close();
		} catch (RuntimeException ex) { // handler's callback runtime-exceptions
			fail(ex);
		} catch (Throwable error) {
			fail(error);
		}
	}


	protected abstract void onClearSelectionKey();

	protected abstract void onCloseChannel();

	protected abstract int process(final SelectionKey key);


	protected void toSetTimerExpectedModCount(final int modCount) {
	}

	protected void toScheduleTimer(final int interestOps, final int modCount) {
	}

	protected void toCancelTimer(final int modCount) {
	}

	protected void onSelected(final int readyOps, final int modCount) {
	}

	protected int onTimeout() {
		return 0;
	}

	protected void onCancelled() {
	}

	protected void onFailed(final Throwable cause) {
	}

	protected void onClosed() {
	}

	protected void onReleased() {
	}


	private static final void cancel(final SelectionKey key) {
		key.cancel();

		final SelectableChannel channel = key.channel();

		if (channel != null) {
			try {
				channel.close();
			} catch (IOException ignore) {
			}
		}
	}

	protected final boolean doClose(final int atCase) {
		EventHandlerCallback callback = null;
		boolean onProcessing = true;

		synchronized (this) {
			if (ENABLE_TRACE != 0) {
				this.traceBuffer.append(' ').append('Z').append(this.state);
				this.traceBuffer.append('#').append(atCase);
			}

			switch (this.state) {
			case STATE_CLOSED:
				return false;

			case STATE_CANCELLED_0: case STATE_CANCELLED_1:
				onProcessing = false;

			default:
				this.state = STATE_CLOSED;
			}

			final SelectionKey key = this.selectionKey;
			if (key != null) {
				this.selectionKey = null;

				cancel(key);

				key.attach(null);
			}

			onCloseChannel();

			onClearSelectionKey();

			callback = this.callback;
			this.callback = null;
		}

		this.timerEntry.unregister();
		toCancelTimer(0);

		this.activeNode.dequeue();

		if (callback != null) {
			callback.eventHandlerClosed(this, onProcessing, atCase);
		}

		if (ENABLE_TRACE != 0) synchronized (this) {
			this.traceBuffer.append('.');
		}

		return true;
	}


	final void trace(final char traceCode) {
		trace(false, traceCode);
	}

	final void trace(final boolean appendSpace, final char traceCode) {
		if (ENABLE_TRACE != 0) synchronized (this) {
			if (appendSpace) {
				this.traceBuffer.append(' ');
			}
			this.traceBuffer.append(traceCode);
		}
	}

	final void trace(final boolean appendSpace, final char traceCode, final int value) {
		if (ENABLE_TRACE != 0) synchronized (this) {
			if (appendSpace) {
				this.traceBuffer.append(' ');
			}
			this.traceBuffer.append(traceCode).append(value);
		}
	}

	final void printTraceString(final StringBuilder builder) {
		if (ENABLE_TRACE != 0) synchronized (this) {
			builder.append(Constants.CRLF);
			builder.append(this.traceBuffer);
			builder.append(Constants.CRLF);
		}
	}

	@Override
	public String toString() {
		final StringBuilder builder = new StringBuilder();

		builder.append("EventHandler [");
		builder.append("State: ").append(getState());
		builder.append("]");

		printTraceString(builder);

		return builder.toString();
	}

}
