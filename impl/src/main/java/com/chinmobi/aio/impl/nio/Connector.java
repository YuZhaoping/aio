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

import com.chinmobi.aio.AIOConnector;
import com.chinmobi.aio.AIOFuture;
import com.chinmobi.aio.AIOFutureCallback;
import com.chinmobi.aio.AIOInetEndpoint;
import com.chinmobi.aio.AIONotActiveException;
import com.chinmobi.aio.AIOServiceHandler;
import com.chinmobi.aio.AIOSession;
import com.chinmobi.aio.impl.util.ConcurrentLinkedQueue;
import com.chinmobi.aio.impl.util.IllegalQueueNodeStateException;
import com.chinmobi.aio.scheme.AIOTransportScheme;

/**
 * @author <a href="mailto:yuzhaoping1970@gmail.com">Zhaoping Yu</a>
 *
 */
public final class Connector implements AIOConnector {

	private static final int MAX_CACHED_REQUEST = 8;

	private final Dispatcher dispatcher;

	private final ConcurrentLinkedQueue<EventHandler.ActiveNode> freeConnectRequests;
	private final AtomicInteger freeConnectRequestsCount;


	public Connector(final Dispatcher dispatcher) {
		super();
		this.dispatcher = dispatcher;

		this.freeConnectRequests = new ConcurrentLinkedQueue<EventHandler.ActiveNode>();
		this.freeConnectRequestsCount = new AtomicInteger(0);
	}

	/* (non-Javadoc)
	 * @see com.chinmobi.aio.AIOConnector#connect(...)
	 */
	public final AIOFuture<AIOSession> connect(final AIOTransportScheme scheme,
			final AIOInetEndpoint remote, final AIOInetEndpoint local,
			final AIOServiceHandler.Factory serviceFactory,
			final AIOFutureCallback<AIOSession> callback, final long timeout, final TimeUnit unit, final Object attachment)
					throws AIONotActiveException {
		if (scheme == null) {
			throw new IllegalArgumentException("Null transport scheme.");
		}
		if (remote == null) {
			throw new IllegalArgumentException("Null remote endpoint.");
		}

		final ConnectRequest request = createConnectRequest();

		request.set(scheme, remote, local, serviceFactory, callback, unit.toMillis(timeout), attachment);

		try {
			putConnectRequest(request);

			return request.future();

		} catch (AIONotActiveException ex) {
			request.release();
			throw ex;
		} catch (RuntimeException ex) { // IllegalQueueNodeStateException
			request.release();
			throw ex;
		}
	}


	// For test
	public final int freeConnectRequestsCount() {
		return this.freeConnectRequestsCount.get();
	}

	private final void putConnectRequest(final ConnectRequest request)
			throws AIONotActiveException, IllegalQueueNodeStateException {

		final SessionContext sessionContext = this.dispatcher.dispatch(null);

		request.setSessionContext(sessionContext);

		final Demultiplexer demultiplexer = sessionContext.demultiplexer();

		final EventHandler handler = request.connectHandler();

		handler.toPendingState();

		demultiplexer.putHandler(handler);
		demultiplexer.putRunnable(handler.activeNode(), 'p', "REQUEST_CONNCET", true);
	}

	private final ConnectRequest createConnectRequest() {
		final EventHandler.ActiveNode node = this.freeConnectRequests.poll();
		if (node != null) {
			this.freeConnectRequestsCount.decrementAndGet();

			final ConnectRequest.Handler handler = (ConnectRequest.Handler)node.handler();
			return handler.connectRequest();
		}
		return new ConnectRequest(this);
	}

	final void releaseConnectRequest(final ConnectRequest request)
			throws IllegalQueueNodeStateException {
		if (this.freeConnectRequestsCount.incrementAndGet() < MAX_CACHED_REQUEST) {

			final EventHandler.ActiveNode node = request.connectHandler().activeNode();
			this.freeConnectRequests.add(node);
		}
	}

}
