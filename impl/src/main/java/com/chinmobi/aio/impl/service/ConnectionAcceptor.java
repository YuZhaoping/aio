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
package com.chinmobi.aio.impl.service;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import com.chinmobi.aio.AIOAcceptor;
import com.chinmobi.aio.AIOInetEndpoint;
import com.chinmobi.aio.AIONotActiveException;
import com.chinmobi.aio.AIOReactor;
import com.chinmobi.aio.AIOServiceHandler;
import com.chinmobi.aio.AIOSession;
import com.chinmobi.aio.impl.util.WrapperRuntimeException;
import com.chinmobi.aio.scheme.AIOTransportScheme;
import com.chinmobi.aio.service.AIOConnectionAcceptor;
import com.chinmobi.aio.service.AIOServiceCallback;

/**
 * @author <a href="mailto:yuzhaoping1970@gmail.com">Zhaoping Yu</a>
 *
 */
public final class ConnectionAcceptor
	implements AIOConnectionAcceptor, AIOAcceptor.Observer,
		AIOInetEndpoint.Observer {

	//private final ServiceContext context;

	private final AIOReactor reactor;

	private final AIOTransportScheme scheme;

	private final AIOInetEndpoint localEndpoint;

	private final HandlerFactory handlerFactory;

	private volatile AIOServiceCallback.Factory callbackFactory;

	private AIOAcceptor acceptor;

	private volatile Observer observer;

	private int status;


	ConnectionAcceptor(final ServiceContext context,
			final AIOReactor reactor, final AIOTransportScheme scheme) {
		//this.context = context;
		this.reactor = reactor;
		this.scheme = scheme;
		this.localEndpoint = new AIOInetEndpoint(0, (AIOInetEndpoint.Observer)this);
		this.handlerFactory = new HandlerFactory(this);
		this.status = -1;
	}


	/* (non-Javadoc)
	 * @see com.chinmobi.aio.act.AIOConnectionAcceptor#setObserver(Observer observer)
	 */
	public final void setObserver(final Observer observer) {
		this.observer = observer;
	}

	/*
	 * (non-Javadoc)
	 * @see com.chinmobi.aio.service.AIOConnectionAcceptor#getObserver()
	 */
	public final Observer getObserver() {
		return this.observer;
	}

	/* (non-Javadoc)
	 * @see com.chinmobi.aio.act.AIOConnectionAcceptor#transportScheme()
	 */
	public final AIOTransportScheme transportScheme() {
		return this.scheme;
	}

	/* (non-Javadoc)
	 * @see com.chinmobi.aio.act.AIOConnectionAcceptor#localEndpoint()
	 */
	public final AIOInetEndpoint localEndpoint() {
		return this.localEndpoint;
	}

	/* (non-Javadoc)
	 * @see com.chinmobi.aio.act.AIOConnectionAcceptor#start(AIOServiceCallback.Factory callbackFactory)
	 */
	public final void start(final AIOServiceCallback.Factory callbackFactory) throws AIONotActiveException {
		this.callbackFactory = callbackFactory;

		AIOAcceptor acceptor;
		synchronized (this.lock()) {
			acceptor = this.acceptor;
			if (acceptor == null) {
				acceptor = this.reactor.getAcceptor(this.scheme, this.localEndpoint);

				this.status = 0;
				this.acceptor = acceptor;
				acceptor.setObserver(this);
			}
		}

		acceptor.start(this.handlerFactory);
	}

	/* (non-Javadoc)
	 * @see com.chinmobi.aio.act.AIOConnectionAcceptor#stop()
	 */
	public final void stop() {
		doStop(true);
	}

	/* (non-Javadoc)
	 * @see com.chinmobi.aio.act.AIOConnectionAcceptor#isAccepting()
	 */
	public final boolean isAccepting() {
		synchronized (this.lock()) {
			return (this.status > 0);
		}
	}

	/* (non-Javadoc)
	 * @see com.chinmobi.aio.act.AIOConnectionAcceptor#waitForAccepting()
	 */
	public final boolean waitForAccepting() throws InterruptedException {
		synchronized (this.lock()) {
			while (this.status == 0) {
				this.lock().wait();
			}
			return (this.status > 0);
		}
	}

	/* (non-Javadoc)
	 * @see com.chinmobi.aio.act.AIOConnectionAcceptor#waitForStopped()
	 */
	public final void waitForStopped() throws InterruptedException {
		synchronized (this.lock()) {
			while (this.status >= 0) {
				this.lock().wait();
			}
		}
	}

	/* (non-Javadoc)
	 * @see com.chinmobi.aio.act.AIOConnectionAcceptor#getServiceCallbackFactory()
	 */
	public final AIOServiceCallback.Factory getServiceCallbackFactory() {
		return this.callbackFactory;
	}

	/* (non-Javadoc)
	 * @see com.chinmobi.aio.act.AIOConnectionAcceptor#reactor()
	 */
	public final AIOReactor reactor() {
		return this.reactor;
	}


	/* (non-Javadoc)
	 * @see com.chinmobi.aio.AIOInetEndpoint.Observer#onAIOEndpointChanged(...)
	 */
	public final void onAIOEndpointChanged(final AIOInetEndpoint endpoint, final boolean isSocketAddress) {
		if (!isSocketAddress) {
			doStop(false);
		}
	}

	private final void doStop(final boolean isClose) {
		final AIOAcceptor acceptor;
		synchronized (this.lock()) {
			acceptor = this.acceptor;
			if (acceptor != null) {
				release();
			}
		}

		if (acceptor != null) {
			acceptor.stop();

			if (isClose) {
				acceptor.close();
			}
		}
	}

	private final void release() {
		this.callbackFactory = null;
		this.acceptor = null;
	}

	private final Object lock() {
		return this.handlerFactory;
	}

	@Override
	public final String toString() {
		final AIOAcceptor acceptor;
		synchronized (this.lock()) {
			acceptor = this.acceptor;
		}

		final StringBuilder builder = new StringBuilder();
		builder.append("ConnectionAcceptor [");

		if (acceptor != null) {
			builder.append(acceptor.toString());
		} else {
			printString(builder);
		}

		builder.append("]");
		return builder.toString();
	}

	private final void printString(final StringBuilder builder) {
		builder.append("scheme: ").append(this.scheme.name());
		builder.append(", bind: ").append(this.localEndpoint.getHostName());
		builder.append(":").append(this.localEndpoint.getPort());
	}


	/* (non-Javadoc)
	 * @see com.chinmobi.aio.AIOAcceptor.Observer#onAIOAcceptorStarted(...)
	 */
	public final void onAIOAcceptorStarted(final AIOAcceptor acceptor) {
		synchronized (this.lock()) {
			if (this.acceptor != null && this.acceptor != acceptor) {
				return;
			}
		}

		final Observer observer = this.observer;
		if (observer != null) {
			observer.aioAcceptorStarted(this);
		}
	}

	/* (non-Javadoc)
	 * @see com.chinmobi.aio.AIOAcceptor.Observer#onAIOAcceptorAccepting(...)
	 */
	public final void onAIOAcceptorAccepting(final AIOAcceptor acceptor) {
		synchronized (this.lock()) {
			if (this.acceptor == null || this.acceptor == acceptor) {
				this.status = 1;
				this.lock().notifyAll();
			} else {
				return;
			}
		}

		final Observer observer = this.observer;
		if (observer != null) {
			observer.aioAcceptorAccepting(this);
		}
	}

	/* (non-Javadoc)
	 * @see com.chinmobi.aio.AIOAcceptor.Observer#onAIOAcceptorStopped(...)
	 */
	public final void onAIOAcceptorStopped(final AIOAcceptor acceptor, final Object msgObj, final Throwable cause) {
		final Observer observer = this.observer;

		synchronized (this.lock()) {
			if (this.acceptor == null || this.acceptor == acceptor) {
				this.status = -1;
				release();

				this.lock().notifyAll();
			} else {
				if (cause != null) {
					throw new WrapperRuntimeException(msgObj.toString(),
							(cause.getCause() != null) ? cause.getCause(): cause);
				}

				return;
			}
		}

		if (observer != null) {
			observer.aioAcceptorStopped(this, msgObj, cause);
		} else
		if (cause != null) {
			throw new WrapperRuntimeException(msgObj.toString(),
					(cause.getCause() != null) ? cause.getCause(): cause);
		}
	}


	static final class WorkService extends Service {

		private WorkService next;

		private final ConnectionAcceptor acceptor;


		WorkService(final ConnectionAcceptor acceptor) {
			super(acceptor.reactor);
			this.acceptor = acceptor;
		}

		@Override
		protected final boolean handleSessionClosed(final Throwable cause) {
			super.handleSessionClosed(cause);

			this.acceptor.handlerFactory.releaseService(this);

			return false;
		}

		@Override
		protected final String internalName() {
			return "Accept";
		}

		@Override
		protected final boolean allowNullServiceCallback() {
			return false;
		}

	}


	static final class HandlerFactory implements AIOServiceHandler.Factory {

		private final ConnectionAcceptor acceptor;

		private final ServiceFactory serviceFactory;


		HandlerFactory(final ConnectionAcceptor acceptor) {
			this.acceptor = acceptor;
			this.serviceFactory = new ServiceFactory();
		}

		/* (non-Javadoc)
		 * @see com.chinmobi.aio.AIOServiceHandler.Factory#createAIOServiceHandler(AIOSession session)
		 */
		public final AIOServiceHandler createAIOServiceHandler(final AIOSession session) {
			final WorkService service = this.serviceFactory.createService(this.acceptor);
			service.handler.attachToSession(session);
			return service.serviceHandler();
		}

		public final void releaseService(final WorkService service) {
			this.serviceFactory.releaseService(service);
		}

	}


	static final class ServiceFactory {

		private static final int MAX_CACHED_SERVICE = 8;

		private final AtomicReference<WorkService> frees;
		private final AtomicInteger freeServicesCount;


		ServiceFactory() {
			this.frees = new AtomicReference<WorkService>();
			this.freeServicesCount = new AtomicInteger(0);
		}


		public final WorkService createService(final ConnectionAcceptor acceptor) {
			WorkService service = pollService();
			if (service == null) {
				service = new WorkService(acceptor);
			}

			final AIOServiceCallback.Factory factory = acceptor.callbackFactory;
			if (factory != null) {
				service.setServiceCallback(factory.createAIOServiceCallback(service));
			} else {
				releaseService(service);
				throw new RuntimeException("Null service callback factory.");
			}

			return service;
		}

		public final WorkService pollService() {
			for (;;) {
				final WorkService service = this.frees.get();

				if (service != null) {
					final WorkService next = service.next;

					if (!this.frees.compareAndSet(service, next)) {
						continue;
					}

					service.next = null;

					this.freeServicesCount.decrementAndGet();
				}

				return service;
			}
		}

		public final void releaseService(final WorkService service) {
			if (this.freeServicesCount.incrementAndGet() < MAX_CACHED_SERVICE) {

				for (;;) {
					final WorkService head = this.frees.get();

					service.next = head; // CAS piggyback
					if (!this.frees.compareAndSet(head, service)) {
						continue;
					}

					break;
				}
			}
		}

	}

}
