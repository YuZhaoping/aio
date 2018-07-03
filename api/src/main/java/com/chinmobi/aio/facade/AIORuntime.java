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
package com.chinmobi.aio.facade;

import java.io.IOException;
import java.util.Iterator;

import com.chinmobi.aio.AIOConfiguration;
import com.chinmobi.aio.AIOIllegalReactorException;
import com.chinmobi.aio.AIOReactor;
import com.chinmobi.aio.pool.AIOConnectionPool;
import com.chinmobi.aio.scheme.AIOTransportScheme;
import com.chinmobi.aio.service.AIOConnection;
import com.chinmobi.aio.service.AIOConnectionAcceptor;

/**
 * @author <a href="mailto:yuzhaoping1970@gmail.com">Zhaoping Yu</a>
 *
 */
public final class AIORuntime implements AIOConnectionCreator {

	private final AIOConfiguration config;

	private final AIOSchemeProvider schemeProvider;

	private final AIOComponentFactory componentFactory;

	private final ReactorObserver internalObserver;

	private AIOConnectionPool connectionPool;

	private AcceptorEntry acceptors;

	private volatile AIOReactor.Observer reactorObserver;
	private volatile AIOReactor reactor;


	public AIORuntime(final AIOSchemeProvider schemeProvider) throws AIORuntimeException {
		if (schemeProvider == null) {
			throw new IllegalArgumentException("Null schemeProvider");
		}

		this.config = new AIOConfiguration();

		this.schemeProvider = schemeProvider;

		try {
			final Class<?> factoryClass = Class.forName("com.chinmobi.aio.impl.facade.ComponentFactory");

			this.componentFactory = (AIOComponentFactory)factoryClass.newInstance();
		} catch (ClassNotFoundException ex) {
			throw new AIORuntimeException(ex);
		} catch (InstantiationException ex) {
			throw new AIORuntimeException(ex);
		} catch (IllegalAccessException ex) {
			throw new AIORuntimeException(ex);
		}

		this.internalObserver = new ReactorObserver();
	}


	public final AIOConfiguration configuration() {
		return this.config;
	}

	public final void setReactorObserver(final AIOReactor.Observer reactorObserver) {
		this.reactorObserver = reactorObserver;
	}

	public final AIOReactor.Observer getReactorObserver() {
		return this.reactorObserver;
	}


	public final AIOReactor start() throws IOException, AIORuntimeException {
		for (;;) {
			final AIOReactor reactor = createReactor();
			try {
				reactor.start();
				return reactor;
			} catch (AIOIllegalReactorException ex) {
				shutdown(reactor);
			}
		}
	}

	public final void shutdown() {
		final AIOReactor reactor;
		synchronized (this.lock()) {
			reactor = this.reactor;

			if (this.connectionPool != null) {
				this.connectionPool.clear();
			}

			stopAcceptors();
		}
		shutdown(reactor);
	}

	private final void shutdown(final AIOReactor reactor) {
		synchronized (this.lock()) {
			final AIOReactor curr = this.reactor;

			if (curr != null && reactor == curr) {
				stopAcceptors();

				this.reactor = null;
				reactor.stop(true);
			}
		}
	}


	public final AIOReactor createReactor() throws AIORuntimeException {
		synchronized (this.lock()) {
			AIOReactor reactor = this.reactor;

			if (reactor == null) {
				try {
					reactor = this.componentFactory.createReactor(this.config);
				} catch (IOException ex) {
					throw new AIORuntimeException(ex);
				}
				reactor.setObserver(this.internalObserver);

				this.reactor = reactor;
			}

			return reactor;
		}
	}

	public final AIOReactor getReactor() {
		synchronized (this.lock()) {
			return this.reactor;
		}
	}


	public final AIOConnectionCreator getConnectionCreator() {
		return this;
	}

	/* (non-Javadoc)
	 * @see com.chinmobi.aio.act.AIOConnectionCreator#createConnection(...)
	 */
	public final AIOConnection createConnection(final String schemeName, final AIOConnection.ReleaseCallback callback)
			throws AIORuntimeException {
		try {
			final AIOTransportScheme scheme = this.schemeProvider.getTransportScheme(schemeName);
			if (scheme == null) {
				throw new IllegalArgumentException("Null scheme for: " + schemeName);
			}
			final AIOReactor reactor = start();
			return this.componentFactory.createConnection(reactor, scheme, callback);
		} catch (IOException ex) {
			throw new AIORuntimeException(ex);
		}
	}


	public final AIOConnectionPool getConnectionPool() {
		synchronized (this.lock()) {
			if (this.connectionPool == null) {
				this.connectionPool = this.componentFactory.createConnectionPool((AIOConnectionCreator)this);
			}
			return this.connectionPool;
		}
	}


	public final Iterator<AIOConnectionAcceptor> connectionAcceptors() {
		return new AcceptorsIter(this);
	}

	public final AIOConnectionAcceptor getConnectionAcceptor(final String schemeName, final int port)
			throws AIORuntimeException {
		return getConnectionAcceptor(schemeName, port, null);
	}

	public final AIOConnectionAcceptor getConnectionAcceptor(final String schemeName, final int port, final String bindAddr)
			throws AIORuntimeException {
		AIOConnectionAcceptor acceptor;
		synchronized (this.lock()) {
			AcceptorEntry entry = this.acceptors;
			while (entry != null) {
				acceptor = entry.acceptor;
				if (acceptor.transportScheme().nameEquals(schemeName) &&
					acceptor.localEndpoint().equals(bindAddr, port)) {
					return acceptor;
				}

				entry = entry.next;
			}

			acceptor = createConnectionAcceptor(schemeName);
			acceptor.localEndpoint().set(bindAddr, port);

			entry = new AcceptorEntry(acceptor);

			entry.next = this.acceptors;
			this.acceptors = entry;
		}
		return acceptor;
	}

	private final AIOConnectionAcceptor createConnectionAcceptor(final String schemeName)
			throws AIORuntimeException {
		try {
			final AIOTransportScheme scheme = this.schemeProvider.getTransportScheme(schemeName);
			if (scheme == null) {
				throw new IllegalArgumentException("Null scheme for: " + schemeName);
			}
			final AIOReactor reactor = start();
			return this.componentFactory.createConnectionAcceptor(reactor, scheme);
		} catch (IOException ex) {
			throw new AIORuntimeException(ex);
		}
	}

	private final void stopAcceptors() {
		AcceptorEntry entry = this.acceptors;
		this.acceptors = null;

		AcceptorEntry next;
		while (entry != null) {
			next = entry.next;
			entry.next = null;

			final AIOConnectionAcceptor acceptor = entry.acceptor;
			acceptor.stop();

			entry = next;
		}
	}

	private final Object lock() {
		return this.internalObserver;
	}


	final class ReactorObserver implements AIOReactor.Observer {

		/* (non-Javadoc)
		 * @see com.chinmobi.aio.AIOReactor.Observer#onAIOReactorStarted(...)
		 */
		public final void onAIOReactorStarted(final AIOReactor reactor) {
			final AIOReactor.Observer reactorObserver = AIORuntime.this.reactorObserver;
			if (reactorObserver != null) {
				reactorObserver.onAIOReactorStarted(reactor);
			}
		}

		/* (non-Javadoc)
		 * @see com.chinmobi.aio.AIOReactor.Observer#onAIOReactorStopped(...)
		 */
		public final void onAIOReactorStopped(final AIOReactor reactor, final Throwable cause) {
			AIORuntime.this.reactor = null;

			final AIOReactor.Observer reactorObserver = AIORuntime.this.reactorObserver;
			if (reactorObserver != null) {
				reactorObserver.onAIOReactorStopped(reactor, cause);
			}
		}

	}


	private final void remove(final AcceptorEntry entry) {
		synchronized (this.lock()) {
			AcceptorEntry current = this.acceptors;
			AcceptorEntry pred = null;
			while (current != null) {
				if (current == entry) {
					if (pred != null) {
						pred.next = entry.next;
					} else {
						this.acceptors = entry.next;
					}

					entry.next = null;
					return;
				}

				pred = current;
				current = current.next;
			}
		}
	}


	private static final class AcceptorEntry {

		AcceptorEntry next;

		private final AIOConnectionAcceptor acceptor;


		AcceptorEntry(final AIOConnectionAcceptor acceptor) {
			this.acceptor = acceptor;
		}

	}

	private static final class AcceptorsIter implements Iterator<AIOConnectionAcceptor> {

		private final AIORuntime runtime;

		private AcceptorEntry next;
		private AcceptorEntry current;


		AcceptorsIter(final AIORuntime runtime) {
			this.runtime = runtime;

			synchronized (runtime.lock()) {
				this.next = runtime.acceptors;
			}

			this.current = null;
		}


		public final boolean hasNext() {
			synchronized (this.runtime.lock()) {
				return (this.next != null);
			}
		}

		public final AIOConnectionAcceptor next() {
			synchronized (this.runtime.lock()) {
				this.current = this.next;

				if (this.next != null) {
					this.next = this.next.next;
				}

				if (this.current != null) {
					return this.current.acceptor;
				} else {
					return null;
				}
			}
		}

		public final void remove() {
			final AcceptorEntry entry;
			synchronized (this.runtime.lock()) {
				entry = this.current;
				if (entry != null) {
					this.current = null;
					this.runtime.remove(entry);
				}
			}
			if (entry != null) {
				entry.acceptor.stop();
			}
		}

	}

}
