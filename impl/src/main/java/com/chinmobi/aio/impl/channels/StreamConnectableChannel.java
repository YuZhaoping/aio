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
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;

import com.chinmobi.aio.AIOClosedSessionException;
import com.chinmobi.aio.AIOInetEndpoint;
import com.chinmobi.aio.AIOServiceHandler;
import com.chinmobi.aio.AIOSession;
import com.chinmobi.aio.impl.nio.ConnectableChannel;
import com.chinmobi.aio.impl.nio.EventHandler;
import com.chinmobi.aio.impl.nio.Session;
import com.chinmobi.aio.impl.nio.SessionContext;
import com.chinmobi.aio.scheme.AIOStreamScheme;

/**
 * @author <a href="mailto:yuzhaoping1970@gmail.com">Zhaoping Yu</a>
 *
 */
public final class StreamConnectableChannel implements ConnectableChannel {

	private final AIOStreamScheme scheme;

	private InputStreamTransportChannel inputChannel;
	private OutputStreamTransportChannel outputChannel;


	StreamConnectableChannel(final AIOStreamScheme scheme) {
		this.scheme = scheme;
	}


	public final void bind(final AIOInetEndpoint localAddr) throws IOException {
		this.scheme.bind(localAddr);
	}

	public final SelectableChannel selectableChannel() {
		return this.inputChannel.selectableChannel();
	}

	public final int connectOps() {
		return SelectionKey.OP_READ;
	}

	public final boolean connect(final AIOInetEndpoint remoteAddr) throws IOException {

		this.scheme.connect(remoteAddr);

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

	public final boolean isConnectable(final SelectionKey key) {
		return key.isReadable();
	}

	public final boolean isConnected() {
		return true;
	}

	public final boolean finishConnect() throws IOException {
		return true;
	}

	public final void execute(final boolean alreadyRegistered, final EventHandler requestHandler,
			final SessionContext sessionContext, final EventHandler.Executor executor) throws IOException {
		try {
			final int status = new Transport(this).execute(sessionContext, executor);
			if (status <= 0) {
				requestHandler.close();
			}
		} catch (IOException ex) {
			close();
			requestHandler.fail(ex);
			throw ex;
		} catch (RuntimeException ex) {
			close();
			requestHandler.fail(ex);
			throw ex;
		}
	}

	public final void close() {
		final InputStreamTransportChannel inputChannel = this.inputChannel;
		if (inputChannel != null) {
			inputChannel.close();
		}

		final OutputStreamTransportChannel outputChannel = this.outputChannel;
		if (outputChannel != null) {
			outputChannel.close();
		}

		this.scheme.close();
	}


	private static final class Transport implements AIOServiceHandler {

		private final StreamConnectableChannel connectableChannel;

		private Session outputSession;
		private Session inputSession;

		private StreamSession session;


		Transport(final StreamConnectableChannel connectableChannel) {
			this.connectableChannel = connectableChannel;
		}


		final int execute(final SessionContext sessionContext, final EventHandler.Executor executor) throws IOException {
			this.outputSession = sessionContext.sessionCreator().createSession(
					this.connectableChannel.outputChannel, 0, false);

			try {
				this.inputSession = sessionContext.sessionCreator().createSession(
						this.connectableChannel.inputChannel, SelectionKey.OP_READ, true);
			} catch (IOException ex) {
				this.outputSession.getEventHandler().close();
				throw ex;
			} catch (RuntimeException ex) {
				this.outputSession.getEventHandler().close();
				throw ex;
			}

			createStreamSession();

			return this.inputSession.getEventHandler().execute(executor, this.session);
		}

		private final void createStreamSession() {
			final Session outputSession = this.outputSession;

			outputSession.setServiceHandler(new ServiceHandler(this, false, outputSession.id()));
			this.connectableChannel.outputChannel.setName(stringOfSessionID(outputSession.id()));

			final Thread outputChannelThread = new Thread(this.connectableChannel.outputChannel);
			outputChannelThread.setDaemon(true);
			outputChannelThread.start();

			final Session inputSession = this.inputSession;

			inputSession.setServiceHandler(new ServiceHandler(this, true, inputSession.id()));
			this.connectableChannel.inputChannel.setName(stringOfSessionID(inputSession.id()));
			inputSession.setId(outputSession.id());

			final Thread inputChannelThread = new Thread(this.connectableChannel.inputChannel);
			inputChannelThread.setDaemon(true);
			inputChannelThread.start();

			this.session = new StreamSession(outputSession, inputSession);
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

					this.connectableChannel.close();
				}
			}

			if (handler != null) {
				handler.handleAIOSessionClosed(this.session, cause);
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
			return "StreamConnectable";
		}

		private static final String stringOfSessionID(final int sessionId) {
			final StringBuilder builder = new StringBuilder();

			builder.append("Connect SID: ");
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
