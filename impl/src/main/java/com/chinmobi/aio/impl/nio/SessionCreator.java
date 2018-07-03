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

import com.chinmobi.aio.AIOClosedSessionException;
import com.chinmobi.aio.AIONotActiveException;
import com.chinmobi.aio.AIOServiceHandler;
import com.chinmobi.aio.impl.channels.ChannelsFactory;
import com.chinmobi.aio.impl.util.IllegalQueueNodeStateException;

/**
 * @author <a href="mailto:yuzhaoping1970@gmail.com">Zhaoping Yu</a>
 *
 */
public class SessionCreator implements EventHandler.Factory {

	protected final SessionContext context;


	protected SessionCreator(final SessionContext context) {
		this.context = context;
	}


	public final SessionContext sessionContext() {
		return this.context;
	}

	public final Session createSession(final TransportChannel transportChannel, final int ops,
			final AIOServiceHandler.Factory serviceFactory) throws ClosedChannelException, IOException {

		return createSession(transportChannel, ops, defaultExecutor(serviceFactory));
	}

	public final Session createSession(final TransportChannel transportChannel, final int ops,
			final boolean toProcess) throws ClosedChannelException, IOException {
		return createSession(transportChannel, ops, (EventHandler.Executor)null, toProcess);
	}

	public final Session createSession(final TransportChannel transportChannel, final int ops,
			final EventHandler.Executor executor) throws ClosedChannelException, IOException {
		return createSession(transportChannel, ops, executor, false);
	}

	private final Session createSession(final TransportChannel transportChannel, int ops,
			final EventHandler.Executor executor, final boolean toProcess) throws ClosedChannelException, IOException {

		try {
			ops |= transportChannel.open();
		} catch (IOException ex) {
			throw ex;
		} catch (RuntimeException ex) {
			throw ex;
		}

		final EventHandler sessionHandler = registerChannel(transportChannel, ops, (executor != null) || toProcess);

		final Session session = ((Session.Handler)sessionHandler).session();

		if (executor != null) {
			sessionHandler.execute(executor, session);
		}

		return session;
	}

	public final Session createSession(final EventHandler source, final TransportChannel transportChannel, final int ops,
			final boolean toProcess) throws ClosedChannelException, IOException {
		return createSession(source, transportChannel, ops, (EventHandler.Executor)null, toProcess);
	}

	public final Session createSession(final EventHandler source, final TransportChannel transportChannel, final int ops,
			final EventHandler.Executor executor) throws ClosedChannelException, IOException {
		return createSession(source, transportChannel, ops, executor, false);
	}

	private final Session createSession(final EventHandler source, final TransportChannel transportChannel, int ops,
			final EventHandler.Executor executor, final boolean toProcess) throws ClosedChannelException, IOException {

		try {
			ops |= transportChannel.open();
		} catch (IOException ex) {
			source.fail(ex);
			throw ex;
		} catch (RuntimeException ex) {
			source.fail(ex);
			throw ex;
		}

		final EventHandler sessionHandler = transferHandler(source, transportChannel, ops, (executor != null) || toProcess);

		final Session session = ((Session.Handler)sessionHandler).session();

		if (executor != null) {
			sessionHandler.execute(executor, session);
		}

		return session;
	}

	public final Session createSession(final SelectableChannel selectableChannel, final int ops,
			final AIOServiceHandler.Factory serviceFactory) throws ClosedChannelException, IOException {

		return createSession(ChannelsFactory.createTransportChannel(selectableChannel), ops, serviceFactory);
	}


	public static EventHandler.Executor defaultExecutor(final AIOServiceHandler.Factory serviceFactory) {
		return new Executor(serviceFactory);
	}


	/*
	 * (non-Javadoc)
	 * @see com.chinmobi.aio.impl.nio.EventHandler.Factory#createEventHandler(...)
	 */
	public final EventHandler createEventHandler(final Object token) {
		return createSessionHandler((TransportChannel)token);
	}

	private final EventHandler createSessionHandler(final TransportChannel transportChannel) {
		final Session session = this.context.createSession();
		session.set(transportChannel);
		return session.getEventHandler();
	}


	protected final EventHandler registerChannel(final TransportChannel transportChannel,
			final int ops, final boolean toProcess) throws ClosedChannelException, IOException {
		try {
			return this.context.demultiplexer().registerChannel(transportChannel.selectableChannel(), ops, this, transportChannel, toProcess);
		} catch (ClosedChannelException ex) {
			throw ex;
		} catch (IOException ex) {
			throw ex;
		} catch (AIONotActiveException ex) {
			throw ex;
		} catch (IllegalQueueNodeStateException ex) {
			throw ex;
		} catch (RuntimeException ex) {	// IllegalBlockingModeException, IllegalSelectorException,
										// CancelledKeyException, IllegalArgumentException
			throw ex;
		}
	}

	private final EventHandler transferHandler(final EventHandler source,
			final TransportChannel transportChannel, final int ops, final boolean toProcess) {
		try {
			return this.context.demultiplexer().transferHandler(source, ops, this, transportChannel, toProcess);
		} catch (AIONotActiveException ex) {
			throw ex;
		} catch (IllegalQueueNodeStateException ex) {
			throw ex;
		} catch (RuntimeException ex) { // CancelledKeyException, IllegalArgumentException
			throw ex;
		}
	}


	private static final class Executor implements EventHandler.Executor {

		private final AIOServiceHandler.Factory serviceFactory;


		Executor(final AIOServiceHandler.Factory serviceFactory) {
			this.serviceFactory = serviceFactory;
		}


		/*
		 * (non-Javadoc)
		 * @see com.chinmobi.aio.impl.nio.EventHandler.Executor#handlerDoExecute(java.lang.Object)
		 */
		public final int handlerDoExecute(final Object object) {
			final Session session = (Session)object;

			final AIOServiceHandler.Factory factory = this.serviceFactory;
			if (factory != null) {
				final AIOServiceHandler service = factory.createAIOServiceHandler(session);
				session.setServiceHandler(service);

				try {
					session.handleAIOSessionOpened(session);
				} catch (AIOClosedSessionException ex) {
					session.close();
				}
			} else {
				session.close();
			}
			return 0;
		}

	}

}
