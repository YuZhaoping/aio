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
import java.net.SocketAddress;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.DatagramChannel;
import java.nio.channels.GatheringByteChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.ScatteringByteChannel;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.WritableByteChannel;
import java.util.concurrent.TimeUnit;

import com.chinmobi.aio.AIOClosedSessionException;
import com.chinmobi.aio.AIOServiceHandler;
import com.chinmobi.aio.AIOSession;
import com.chinmobi.aio.impl.act.DummyInputConsumer;
import com.chinmobi.aio.impl.act.InputActor;
import com.chinmobi.aio.impl.act.OutputActor;

/**
 * @author <a href="mailto:yuzhaoping1970@gmail.com">Zhaoping Yu</a>
 *
 */
public final class Session implements ExtendedSession,
	AIOServiceHandler, HandshakeContext, ExceptionHandler {

	private static final int STATUS_INPUT	= 1;
	private static final int STATUS_OUTPUT	= 2;
	private static final int STATUS_SHUTTINGDOWN = 4;
	private static final int STATUS_CLOSING	= 8;

	private final SessionContext context;

	private final OutputActor outputActor;
	private final InputActor inputActor;

	private final Handler eventHandler;

	private int id;

	private AIOServiceHandler serviceHandler;

	private TransportChannel transportChannel;

	private volatile int status;


	Session(final SessionContext context) {
		this.context = context;

		this.outputActor = new OutputActor(this);
		this.inputActor = new InputActor(this);
		this.eventHandler = new Handler(this);
	}


	final void set(final TransportChannel transportChannel) {
		this.transportChannel = transportChannel;
		this.serviceHandler = null;
		this.status = 0;

		this.eventHandler.reset();

		this.outputActor.reset();
		this.inputActor.reset();

		final HandshakeHandler handshakeHandler = transportChannel.getHandshakeHandler();
		if (handshakeHandler != null) {
			handshakeHandler.setHandshakeContext(this);
		}

		if (transportChannel.isSupportedInput()) {
			this.status |= STATUS_INPUT;
		}
		if (transportChannel.isSupportedOutput()) {
			this.status |= STATUS_OUTPUT;
		}
	}

	public final Session setId(final int id) {
		this.id = id;
		return this;
	}

	public final void setServiceHandler(final AIOServiceHandler handler) {
		this.serviceHandler = handler;
	}


	public final SessionContext context() {
		return this.context;
	}

	public final Object requestLock() {
		return this.eventHandler;
	}

	public final boolean activate() {
		return this.context.demultiplexer().activateHandler(this.eventHandler);
	}

	public final boolean isInterestOpsPending() {
		return this.context.demultiplexer().containsInInterestOpsPendings(this.eventHandler);
	}

	public final int generateTimerModCount() {
		return this.eventHandler.generateTimerModCount(true);
	}

	public final void fail(final Throwable cause) {
		this.eventHandler.fail(cause);
	}

	public final TransportChannel getTransportChannel() {
		return this.transportChannel;
	}

	public final EventHandler getEventHandler() {
		return this.eventHandler;
	}

	final Handler handler() {
		return this.eventHandler;
	}

	public final void onAddInputActRequest() {
		if (EventHandler.ENABLE_TRACE != 0) this.eventHandler.trace(true, '!');
	}

	public final void onAddOutputActRequest() {
		if (EventHandler.ENABLE_TRACE != 0) this.eventHandler.trace(true, '*');
	}


	/* (non-Javadoc)
	 * @see com.chinmobi.aio.AIOSession#isOpen()
	 */
	public final boolean isOpen() {
		return this.eventHandler.isOpen();
	}

	/* (non-Javadoc)
	 * @see com.chinmobi.aio.AIOSession#close()
	 */
	public final void close() {
		close(false);
	}

	/* (non-Javadoc)
	 * @see com.chinmobi.aio.AIOSession#close(boolean forceShutdown)
	 */
	public final void close(final boolean forceShutdown) {
		this.status |= STATUS_CLOSING;

		if (EventHandler.ENABLE_TRACE != 0) this.eventHandler.trace(true, 'Z');

		shutdownInput(forceShutdown);

		if (shutdownOutput(forceShutdown)) {
			doClose();
		}
	}

	public final boolean isClosing() {
		return ((this.status & STATUS_CLOSING) != 0);
	}

	private final void doClose() {
		if (this.eventHandler.cancel() > 0) {
			this.context.demultiplexer().wakeup();
		}
	}

	/* (non-Javadoc)
	 * @see com.chinmobi.aio.AIOSession#isInputShutdown()
	 */
	public final boolean isInputShutdown() {
		return ((this.status & STATUS_INPUT) == 0);
	}

	/* (non-Javadoc)
	 * @see com.chinmobi.aio.AIOSession#isOutputShutdown()
	 */
	public final boolean isOutputShutdown() {
		return ((this.status & STATUS_OUTPUT) == 0);
	}

	/* (non-Javadoc)
	 * @see com.chinmobi.aio.AIOSession#shutdownInput(boolean force)
	 */
	public final boolean shutdownInput(final boolean force) {
		if ((this.status & STATUS_INPUT) != 0) {
			if (EventHandler.ENABLE_TRACE != 0) this.eventHandler.trace(true, '|');

			final TransportChannel transport = this.transportChannel;
			if (transport != null) {
				if (!transport.shutdownInput()) {
					return false;
				}
			}
			this.status &= ~STATUS_INPUT;
			clearInputEvent();
		}
		return true;
	}

	/* (non-Javadoc)
	 * @see com.chinmobi.aio.AIOSession#shutdownOutput(boolean force)
	 */
	public final boolean shutdownOutput(final boolean force) {
		if ((this.status & STATUS_OUTPUT) != 0) {
			if (EventHandler.ENABLE_TRACE != 0) this.eventHandler.trace(true, '&');

			final TransportChannel transport = this.transportChannel;
			if (transport != null) {
				if (!force) {
					if (transport.writableChannel() != null && this.outputActor.hasRequest()) {
						this.status |= STATUS_SHUTTINGDOWN;
						setOutputEvent();
						return false;
					}
				}

				if (!transport.shutdownOutput()) {
					return false;
				}
			}
			this.status &= ~STATUS_OUTPUT;
			clearOutputEvent();
		}
		return true;
	}

	public final boolean isShuttingDown() {
		return ((this.status & STATUS_SHUTTINGDOWN) != 0);
	}

	/* (non-Javadoc)
	 * @see com.chinmobi.aio.AIOSession#getServiceHandler()
	 */
	public final AIOServiceHandler getServiceHandler() {
		return this.serviceHandler;
	}

	/* (non-Javadoc)
	 * @see com.chinmobi.aio.AIOSession#setTimeout(long timeout, TimeUnit unit)
	 */
	public final void setTimeout(final long timeout, final TimeUnit unit) {
		this.eventHandler.setTimeout(unit.toMillis(timeout));
	}

	/* (non-Javadoc)
	 * @see com.chinmobi.aio.AIOSession#inputActor()
	 */
	public final InputActor inputActor() {
		return this.inputActor;
	}

	/* (non-Javadoc)
	 * @see com.chinmobi.aio.AIOSession#outputActor()
	 */
	public final OutputActor outputActor() {
		return this.outputActor;
	}

	/*
	 * (non-Javadoc)
	 * @see com.chinmobi.aio.AIOSession#readableChannel()
	 */
	public final ReadableByteChannel readableChannel() {
		final TransportChannel transport = this.transportChannel;
		if (transport != null) {
			return transport.readableChannel();
		}
		return null;
	}

	/* (non-Javadoc)
	 * @see com.chinmobi.aio.AIOSession#scatteringChannel()
	 */
	public final ScatteringByteChannel scatteringChannel() {
		final TransportChannel transport = this.transportChannel;
		if (transport != null) {
			return transport.scatteringChannel();
		}
		return null;
	}

	/*
	 * (non-Javadoc)
	 * @see com.chinmobi.aio.AIOSession#writableChannel()
	 */
	public final WritableByteChannel writableChannel() {
		final TransportChannel transport = this.transportChannel;
		if (transport != null) {
			return transport.writableChannel();
		}
		return null;
	}

	/* (non-Javadoc)
	 * @see com.chinmobi.aio.AIOSession#gatheringChannel()
	 */
	public final GatheringByteChannel gatheringChannel() {
		final TransportChannel transport = this.transportChannel;
		if (transport != null) {
			return transport.gatheringChannel();
		}
		return null;
	}

	/* (non-Javadoc)
	 * @see com.chinmobi.aio.AIOSession#datagramChannel()
	 */
	public final DatagramChannel datagramChannel() {
		final TransportChannel transport = this.transportChannel;
		if (transport != null) {
			return transport.datagramChannel();
		}
		return null;
	}

	/* (non-Javadoc)
	 * @see com.chinmobi.aio.AIOSession#getInterestEvents()
	 */
	public final int getInterestEvents() {
		return this.eventHandler.getInterestOps();
	}

	/* (non-Javadoc)
	 * @see com.chinmobi.aio.AIOSession#setInputEvent()
	 */
	public final void setInputEvent() {
		setInputEvent(true);
	}

	public final boolean setInputEvent(final boolean toEffect) {
		try {
			return this.eventHandler.setInterestOps(SelectionKey.OP_READ, toEffect);
		} catch (CancelledKeyException ignore) {
			return false;
		}
	}

	/* (non-Javadoc)
	 * @see com.chinmobi.aio.AIOSession#clearInputEvent()
	 */
	public final void clearInputEvent() {
		clearInputEvent(true);
	}

	public final boolean clearInputEvent(final boolean toEffect) {
		try {
			return this.eventHandler.clearInterestOps(SelectionKey.OP_READ, toEffect);
		} catch (CancelledKeyException ignore) {
			return false;
		}
	}

	/* (non-Javadoc)
	 * @see com.chinmobi.aio.AIOSession#setOutputEvent()
	 */
	public final void setOutputEvent() {
		setOutputEvent(true);
	}

	public final boolean setOutputEvent(final boolean toEffect) {
		try {
			return this.eventHandler.setInterestOps(SelectionKey.OP_WRITE, toEffect);
		} catch (CancelledKeyException ignore) {
			return false;
		}
	}

	/* (non-Javadoc)
	 * @see com.chinmobi.aio.AIOSession#clearOutputEvent()
	 */
	public final void clearOutputEvent() {
		clearOutputEvent(true);
	}

	public final boolean clearOutputEvent(final boolean toEffect) {
		try {
			return this.eventHandler.clearInterestOps(SelectionKey.OP_WRITE, toEffect);
		} catch (CancelledKeyException ignore) {
			return false;
		}
	}

	/* (non-Javadoc)
	 * @see com.chinmobi.aio.AIOSession#getRemoteSocketAddress()
	 */
	public final SocketAddress getRemoteSocketAddress() {
		final TransportChannel transport = this.transportChannel;
		if (transport != null) {
			return transport.remoteSocketAddress();
		}
		return null;
	}

	/* (non-Javadoc)
	 * @see com.chinmobi.aio.AIOSession#getLocalSocketAddress()
	 */
	public final SocketAddress getLocalSocketAddress() {
		final TransportChannel transport = this.transportChannel;
		if (transport != null) {
			return transport.localSocketAddress();
		}
		return null;
	}

	/* (non-Javadoc)
	 * @see com.chinmobi.aio.AIOSession#id()
	 */
	public final int id() {
		return this.id;
	}


	/* (non-Javadoc)
	 * @see com.chinmobi.aio.AIOServiceHandler#handleAIOSessionOpened(...)
	 */
	public final void handleAIOSessionOpened(final AIOSession session) throws AIOClosedSessionException {
		final AIOServiceHandler handler = this.serviceHandler;
		if (handler != null) {
			handler.handleAIOSessionOpened(this);
		}
	}

	/* (non-Javadoc)
	 * @see com.chinmobi.aio.AIOServiceHandler#handleAIOSessionInputReady(...)
	 */
	public final boolean handleAIOSessionInputReady(final AIOSession session)
			throws IOException, AIOClosedSessionException {
		final AIOServiceHandler handler = this.serviceHandler;
		if (handler != null) {
			return handler.handleAIOSessionInputReady(this);
		}
		return false;
	}

	/* (non-Javadoc)
	 * @see com.chinmobi.aio.AIOServiceHandler#handleAIOSessionOutputReady(...)
	 */
	public final boolean handleAIOSessionOutputReady(final AIOSession session)
			throws IOException, AIOClosedSessionException {
		final AIOServiceHandler handler = this.serviceHandler;
		if (handler != null) {
			return handler.handleAIOSessionOutputReady(this);
		}
		return false;
	}

	/* (non-Javadoc)
	 * @see com.chinmobi.aio.AIOServiceHandler#handleAIOSessionTimeout(...)
	 */
	public final void handleAIOSessionTimeout(final AIOSession session) throws AIOClosedSessionException {
		final AIOServiceHandler handler = this.serviceHandler;
		if (handler != null) {
			handler.handleAIOSessionTimeout(this);
		}
	}

	/* (non-Javadoc)
	 * @see com.chinmobi.aio.AIOServiceHandler#handleAIOSessionClosed(...)
	 */
	public final void handleAIOSessionClosed(final AIOSession session, final Throwable cause) {
		this.inputActor.handleSessionClosed(cause);
		this.outputActor.handleSessionClosed(cause);

		final AIOServiceHandler handler = this.serviceHandler;

		if (handler != null) {
			try {
				handler.handleAIOSessionClosed(this, cause);
			} catch (Throwable ignore) {
				handleUncaughtException(ignore);
			}
		} else
		if (cause != null) {
			this.context.handleUncaughtException(cause);
		}

		release();
	}

	/*
	 * (non-Javadoc)
	 * @see com.chinmobi.aio.impl.nio.HandshakeContext#handleUncaughtException(Throwable ex)
	 */
	public final void handleUncaughtException(Throwable cause) {
		if (cause != null) {
			cause = new ThrowableWrapper(toString(), cause);
		}
		this.context.handleUncaughtException(cause);
	}

	private final void release() {
		this.serviceHandler = null;

		try {
			this.context.releaseSession(this);
		} catch (RuntimeException ignore) {
			handleUncaughtException(ignore);
		}
	}


	private final int handleInputReady(final boolean isReadable) {
		if (EventHandler.ENABLE_TRACE != 0) {
			this.eventHandler.trace(true, ':');
		}

		final int status = this.inputActor.handleSessionReady(isReadable);
		if (status > 0) {
			return 0;
		} else
		if (status < 0) {
			return status;
		}

		try {
			if (isReadable) {
				if (!handleAIOSessionInputReady(this)) {
					DummyInputConsumer.handleInput(this);
				}
			}
		} catch (IOException ex) {
			handleUncaughtException(ex);

			shutdownInput(true);
			if (isOutputShutdown()) {
				this.eventHandler.close();
				return -1;
			}
		} catch (AIOClosedSessionException ex) {
			this.eventHandler.close();
			return -1;
		}

		if (!this.eventHandler.isActive()) {
			this.eventHandler.close();
			return -1;
		}

		return 0;
	}

	private final int handleOutputReady(final boolean isWritable) {
		if (EventHandler.ENABLE_TRACE != 0) {
			this.eventHandler.trace(true, ';');
		}

		final int status = this.outputActor.handleSessionReady(isWritable);
		if (status > 0) {
			return 0;
		} else
		if (status < 0) {
			return status;
		}

		try {
			if (isWritable) {
				handleAIOSessionOutputReady(this);
			}
		} catch (IOException ex) {
			handleUncaughtException(ex);

			shutdownOutput(true);
			if (isInputShutdown()) {
				this.eventHandler.close();
				return -1;
			}
		} catch (AIOClosedSessionException ex) {
			this.eventHandler.close();
			return -1;
		}

		if (!this.eventHandler.isActive()) {
			this.eventHandler.close();
			return -1;
		}

		return 0;
	}

	private final int handshake(final int readyOps) {
		final TransportChannel channel = this.transportChannel;
		if (channel != null) {
			final HandshakeHandler handshakeHandler = channel.getHandshakeHandler();
			if (handshakeHandler == null) {
				return 0;
			}

			try {
				if (EventHandler.ENABLE_TRACE != 0) {
					this.eventHandler.trace(true, 'H');
				}

				final int status = handshakeHandler.handshake(readyOps);

				if (EventHandler.ENABLE_TRACE != 0) {
					this.eventHandler.trace(true, 'h', status);
				}

				switch (status) {
				case HandshakeHandler.HANDSHAKE_STATUS_UNCOMPLETED:
					return 1;

				case HandshakeHandler.HANDSHAKE_STATUS_CLOSED:
					this.eventHandler.close();
					return -1;

				//case HandshakeHandler.HANDSHAKE_STATUS_FINISHED:
				default:
					return 0;
				}

			} catch (IOException ex) {
				this.eventHandler.fail(ex);
				return -1;
			}
		} else {
			this.eventHandler.close();
			return -1;
		}
	}

	private final void toSetTimerExpectedModCount(final int modCount) {
		this.inputActor.setTimerExpectedModCount(modCount);
		this.outputActor.setTimerExpectedModCount(modCount);
	}

	private final void toScheduleTimer(final int interestOps, final int modCount) {
		if ((interestOps & SelectionKey.OP_READ) != 0) {
			this.inputActor.scheduleTimer(modCount);
		} else {
			this.inputActor.cancelTimer(modCount);
		}

		if ((interestOps & SelectionKey.OP_WRITE) != 0) {
			this.outputActor.scheduleTimer(modCount);
		} else {
			this.outputActor.cancelTimer(modCount);
		}
	}

	private final void toCancelTimer(final int modCount) {
		this.inputActor.cancelTimer(modCount);
		this.outputActor.cancelTimer(modCount);
	}

	private final void onSelected(final int readyOps, final int modCount) {
		if ((readyOps & SelectionKey.OP_READ) != 0) {
			this.inputActor.handleSessionSelected(modCount);
		}
		if ((readyOps & SelectionKey.OP_WRITE) != 0) {
			this.outputActor.handleSessionSelected(modCount);
		} else {
			this.outputActor.cancelTimer(modCount);
		}
	}

	private final void onCancelled() {
		this.inputActor.handleSessionCancelled();
		this.outputActor.handleSessionCancelled();
	}

	@Override
	public final String toString() {
		final StringBuilder builder = new StringBuilder();

		builder.append("Session [");
		builder.append("ID: ").append(this.id());
		final AIOServiceHandler handler = this.serviceHandler;
		if (handler != null) {
			builder.append(", Handler: ").append(handler.toString());
		}
		builder.append("]");

		this.eventHandler.printTraceString(builder);

		this.inputActor.printTraceString('I', builder);
		this.outputActor.printTraceString('O', builder);

		return builder.toString();
	}


	static final class Handler extends EventHandler {

		private final Session session;


		Handler(final Session session) {
			super();
			this.session = session;
		}


		public final Session session() {
			return this.session;
		}

		@Override
		public final SelectableChannel selectableChannel() {
			final TransportChannel channel = this.session.transportChannel;
			if (channel != null) {
				return channel.selectableChannel();
			}
			return null;
		}

		@Override
		protected final void onClearSelectionKey() {
			this.session.transportChannel = null;
		}

		@Override
		protected final void onCloseChannel() {
			final TransportChannel channel = this.session.transportChannel;
			if (channel != null) {
				channel.close();
			}
		}

		@Override
		protected final int process(final SelectionKey key) {
			final int readyOps = key.readyOps();

			final boolean isWritable = ((readyOps & SelectionKey.OP_WRITE) != 0);
			if (isWritable) {
				this.session.clearOutputEvent(false);
			}

			int status = this.session.handshake(readyOps);

			if (status == 0) {
				final boolean isReadable = ((readyOps & SelectionKey.OP_READ) != 0);
				status = this.session.handleInputReady(isReadable);

				if (status == 0) {
					status = this.session.handleOutputReady(isWritable);
				}
			}

			return status;
		}

		@Override
		protected final void toSetTimerExpectedModCount(final int modCount) {
			this.session.toSetTimerExpectedModCount(modCount);
		}

		@Override
		protected final void toScheduleTimer(final int interestOps, final int modCount) {
			this.session.toScheduleTimer(interestOps, modCount);
		}

		@Override
		protected final void toCancelTimer(final int modCount) {
			this.session.toCancelTimer(modCount);
		}

		@Override
		protected final void onSelected(final int readyOps, final int modCount) {
			this.session.onSelected(readyOps, modCount);
		}

		@Override
		protected final int onTimeout() {
			try {
				this.session.handleAIOSessionTimeout(this.session);
			} catch (AIOClosedSessionException ex) {
				close();
				return -1;
			}
			return 0;
		}

		@Override
		protected final void onCancelled() {
			this.session.onCancelled();
		}

		@Override
		protected final void onFailed(final Throwable cause) {
			this.session.handleAIOSessionClosed(this.session, cause);
		}

		@Override
		protected final void onClosed() {
			this.session.handleAIOSessionClosed(this.session, null);
		}

		@Override
		public final String toString() {
			return this.session.toString();
		}

	}

}
