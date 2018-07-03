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
package com.chinmobi.aio.impl.channels;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;

import com.chinmobi.aio.AIOClosedSessionException;
import com.chinmobi.aio.AIOInetEndpoint;
import com.chinmobi.aio.AIOServiceHandler;
import com.chinmobi.aio.AIOSession;
import com.chinmobi.aio.impl.nio.AcceptableChannel;
import com.chinmobi.aio.impl.nio.AcceptedTransport;
import com.chinmobi.aio.impl.nio.AcceptorHandler;
import com.chinmobi.aio.impl.nio.EventHandler;
import com.chinmobi.aio.impl.nio.Session;
import com.chinmobi.aio.impl.nio.SessionContext;
import com.chinmobi.aio.scheme.AIOStreamScheme;

/**
 * @author <a href="mailto:yuzhaoping1970@gmail.com">Zhaoping Yu</a>
 *
 */
public final class StreamAcceptableChannel implements AcceptableChannel {

	private final AIOStreamScheme scheme;

	private InputStreamTransportChannel inputChannel;
	private OutputStreamTransportChannel outputChannel;


	StreamAcceptableChannel(final AIOStreamScheme scheme) {
		this.scheme = scheme;
	}


	public final boolean bind(final AIOInetEndpoint addr) throws IOException {

		this.scheme.accept(addr);

		final InputStream inputStream = this.scheme.getInputStream();
		final OutputStream outputStream = this.scheme.getOutputStream();

		if (inputStream == null) {
			throw new IllegalArgumentException("Null scheme inputStream.");
		}

		if (outputStream == null) {
			throw new IllegalArgumentException("Null scheme outputStream.");
		}

		this.inputChannel = new InputStreamTransportChannel(inputStream, this.scheme.getBufferSize());
		this.outputChannel = new OutputStreamTransportChannel(outputStream, this.scheme.getBufferSize());

		return true;
	}

	public final SelectableChannel selectableChannel() {
		return this.inputChannel.selectableChannel();
	}

	public final int acceptOps() {
		return 0;
	}

	public final boolean isAcceptable(final SelectionKey key) {
		try {
			return key.isReadable();
		} catch (CancelledKeyException ex) {
			throw ex;
		}
	}

	public final AcceptedTransport accept(final AcceptorHandler acceptorHandler) throws IOException {
		acceptorHandler.clearInterestOps(acceptOps(), true);

		return new Transport(acceptorHandler, this);
	}

	public final void close() {
		final InputStreamTransportChannel inputChannel = this.inputChannel;
		if (inputChannel != null) {
			this.inputChannel = null;
			inputChannel.close();
		}

		final OutputStreamTransportChannel outputChannel = this.outputChannel;
		if (outputChannel != null) {
			this.outputChannel = null;
			outputChannel.close();
		}

		if (!this.scheme.isMultiAcceptable()) {
			this.scheme.close();
		}
	}


	private static final class Transport implements AcceptedTransport,
			AIOServiceHandler, EventHandler.Executor {

		private final AcceptorHandler acceptorHandler;
		private final AIOStreamScheme scheme;

		private final InputStreamTransportChannel inputChannel;
		private final OutputStreamTransportChannel outputChannel;

		private Session outputSession;
		private Session inputSession;

		private StreamSession session;


		Transport(final AcceptorHandler acceptorHandler, final StreamAcceptableChannel acceptableChannel) {
			this.acceptorHandler = acceptorHandler;

			this.inputChannel = acceptableChannel.inputChannel;
			this.outputChannel = acceptableChannel.outputChannel;
			acceptableChannel.inputChannel = null;
			acceptableChannel.outputChannel = null;

			this.scheme = acceptableChannel.scheme;
		}


		public final void execute(final AcceptorHandler acceptorHandler,
				final SessionContext sessionContext, final EventHandler.Executor executor)
				throws IOException {
			try {
				doExecute(sessionContext, executor);
			} catch (IOException ex) {
				close();
				throw ex;
			} catch (RuntimeException ex) {
				close();
				throw ex;
			}
		}

		private final void doExecute(final SessionContext sessionContext, final EventHandler.Executor executor)
				throws IOException {
			try {
				this.outputSession = sessionContext.sessionCreator().createSession(
						this.outputChannel, 0, false);
			} catch (IOException ex) {
				this.acceptorHandler.fail(ex);
				throw ex;
			} catch (RuntimeException ex) {
				this.acceptorHandler.fail(ex);
				throw ex;
			}

			try {
				sessionContext.sessionCreator().createSession(
						this.acceptorHandler, this.inputChannel, SelectionKey.OP_READ, (EventHandler.Executor)this);
			} catch (IOException ex) {
				this.outputSession.getEventHandler().close();
				throw ex;
			} catch (RuntimeException ex) {
				this.outputSession.getEventHandler().close();
				throw ex;
			}
		}

		public final void close() {
			this.inputChannel.close();
			this.outputChannel.close();
		}


		public final int handlerDoExecute(final Object object) {
			final Session inputSession = (Session)object;

			this.inputSession = inputSession;

			this.outputSession.setServiceHandler(new ServiceHandler(this, false, this.outputSession.id()));
			this.outputChannel.setName(stringOfSessionID(this.outputSession.id()));

			final Thread outputChannelThread = new Thread(this.outputChannel);
			outputChannelThread.setDaemon(true);
			outputChannelThread.start();

			inputSession.setServiceHandler(new ServiceHandler(this, true, inputSession.id()));
			this.inputChannel.setName(stringOfSessionID(inputSession.id()));
			inputSession.setId(this.outputSession.id());

			final Thread inputChannelThread = new Thread(this.inputChannel);
			inputChannelThread.setDaemon(true);
			inputChannelThread.start();

			final StreamSession session = new StreamSession(this.outputSession, this.inputSession);
			this.session = session;

			if (this.scheme.isMultiAcceptable()) {
				this.acceptorHandler.toAcceptNext();
			} else {
				this.acceptorHandler.wrapHandler(inputSession.getEventHandler());
			}

			final EventHandler.Executor executor = this.acceptorHandler.getSessionCreatedExecutor();
			if (executor != null) {
				return executor.handlerDoExecute(session);
			} else {
				session.close();
			}

			return 0;
		}

		public final void handleAIOSessionOpened(final AIOSession session) throws AIOClosedSessionException {
			final AIOServiceHandler handler = this.session.getServiceHandler();
			if (handler != null) {
				handler.handleAIOSessionOpened(this.session);
			}
		}

		public final boolean handleAIOSessionInputReady(final AIOSession session)
				throws IOException, AIOClosedSessionException {
			final AIOServiceHandler handler = this.session.getServiceHandler();
			if (handler != null) {
				return handler.handleAIOSessionInputReady(this.session);
			}
			return false;
		}

		public final boolean handleAIOSessionOutputReady(final AIOSession session)
				throws IOException, AIOClosedSessionException {
			final AIOServiceHandler handler = this.session.getServiceHandler();
			if (handler != null) {
				return handler.handleAIOSessionOutputReady(this.session);
			}
			return false;
		}

		public final void handleAIOSessionTimeout(final AIOSession session) throws AIOClosedSessionException {
			final AIOServiceHandler handler = this.session.getServiceHandler();
			if (handler != null) {
				handler.handleAIOSessionTimeout(this.session);
			}
		}

		public final void handleAIOSessionClosed(final AIOSession session, final Throwable cause) {
			boolean isDone = false;
			AIOServiceHandler handler = null;

			synchronized (this) {
				if (session == this.inputSession) {
					this.inputSession = null;

					if (this.outputSession != null) {
						this.outputSession.close();
					}
				} else
				if (session == this.outputSession) {
					this.outputSession = null;

					if (this.inputSession != null) {
						this.inputSession.close();
					}
				}

				if (this.inputSession == null && this.outputSession == null) {
					handler = this.session.getServiceHandler();
					this.session.setServiceHandler(null);

					isDone = true;
				}
			}

			if (handler != null) {
				try {
					handler.handleAIOSessionClosed(this.session, cause);
				} catch (Throwable ignore) {
				}
			}

			if (isDone && !this.scheme.isMultiAcceptable()) {
				try {
					this.scheme.close();
				} catch (Throwable ignore) {
				}

				this.acceptorHandler.doStop(cause);
			}
		}

		@Override
		public final String toString() {
			final StreamSession session = this.session;
			if (session != null) {
				final AIOServiceHandler handler = session.getServiceHandler();
				if (handler != null) {
					return handler.toString();
				}
			}
			return "StreamAcceptable";
		}

		private static final String stringOfSessionID(final int sessionId) {
			final StringBuilder builder = new StringBuilder();

			builder.append("Accept SID: ");
			builder.append(sessionId);

			return builder.toString();
		}

	}

	private static final class ServiceHandler implements AIOServiceHandler {

		private final Transport transport;

		private final boolean forInput;
		private final int sessionId;


		ServiceHandler(final Transport transport, final boolean forInput, final int sessionId) {
			this.transport = transport;
			this.forInput = forInput;
			this.sessionId = sessionId;
		}

		public final void handleAIOSessionOpened(final AIOSession session) throws AIOClosedSessionException {
			this.transport.handleAIOSessionOpened(session);
		}

		public final boolean handleAIOSessionInputReady(final AIOSession session)
				throws IOException, AIOClosedSessionException {
			return this.transport.handleAIOSessionInputReady(session);
		}

		public final boolean handleAIOSessionOutputReady(final AIOSession session)
				throws IOException, AIOClosedSessionException {
			return this.transport.handleAIOSessionOutputReady(session);
		}

		public final void handleAIOSessionTimeout(final AIOSession session) throws AIOClosedSessionException {
			this.transport.handleAIOSessionTimeout(session);
		}

		public final void handleAIOSessionClosed(final AIOSession session, final Throwable cause) {
			this.transport.handleAIOSessionClosed(session, cause);
		}

		@Override
		public final String toString() {
			final StringBuilder builder = new StringBuilder();

			if (this.forInput) {
				builder.append("[Input SID: ");
			} else {
				builder.append("[Output SID: ");
			}
			builder.append(this.sessionId);
			builder.append(", [");
			builder.append(this.transport.toString());
			builder.append("]]");

			return builder.toString();
		}

	}

}
