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

import java.util.Iterator;

import com.chinmobi.aio.AIOAcceptor;
import com.chinmobi.aio.AIOInetEndpoint;
import com.chinmobi.aio.AIONotActiveException;
import com.chinmobi.aio.scheme.AIOTransportScheme;

/**
 * @author <a href="mailto:yuzhaoping1970@gmail.com">Zhaoping Yu</a>
 *
 */
public final class AcceptorSetImpl implements AcceptorSet {

	static class Entry {

		volatile boolean isQueued;

		Entry next;


		protected Entry() {
			this.isQueued = true;
		}


		public final boolean isQueued() {
			return this.isQueued;
		}

	}


	private final Dispatcher dispatcher;

	private Acceptor acceptors;

	private Iter iter;


	public AcceptorSetImpl(final Dispatcher dispatcher) {
		this.dispatcher = dispatcher;
	}


	public final AIOAcceptor getAcceptor(final AIOTransportScheme scheme, final AIOInetEndpoint endpoint)
			throws AIONotActiveException {
		if (scheme == null) {
			throw new IllegalArgumentException("Null transport scheme.");
		}

		if (endpoint == null) {
			throw new IllegalArgumentException("Null endpoint.");
		}

		synchronized (this) {
			Acceptor acceptor = this.acceptors;

			while (acceptor != null) {
				if (acceptor.getTransportScheme().equals(scheme) &&
					acceptor.getLocalEndpoint().equals(endpoint)) {
					return acceptor;
				}

				acceptor = (Acceptor)acceptor.next;
			}

			final SessionContext sessionContext = this.dispatcher.dispatch(null);
			sessionContext.demultiplexer().activeChecker().assertActive();
			acceptor = new Acceptor(this, sessionContext, scheme, endpoint);

			acceptor.next = this.acceptors;
			this.acceptors = acceptor;

			return acceptor;
		}
	}

	public final Iterator<AIOAcceptor> iterator() {
		synchronized (this) {
			if (this.iter == null) {
				this.iter = new Iter(this);
			}

			this.iter.toFirst();

			return this.iter;
		}
	}

	final void removeAcceptor(final Acceptor acceptor) {
		synchronized (this) {
			Acceptor pred = null;
			Acceptor curr = this.acceptors;

			while (curr != null) {
				final Acceptor next = (Acceptor)curr.next;

				if (acceptor == curr) {
					acceptor.isQueued = false;

					curr.next = null;

					if (pred != null) {
						pred.next = next;
					} else {
						this.acceptors = next;
					}

					if (this.iter != null) {
						if (curr == this.iter.next) {
							this.iter.next = next;
						}
					}

					return;
				}

				pred = curr;
				curr = next;
			}
		}
	}

	public final void clear() {
		Acceptor acceptor = removeAll();

		while (acceptor != null) {
			try {
				acceptor.isQueued = false;

				if (acceptor.cancel() >= 0) {
					acceptor.free();
				}

			} catch (RuntimeException ex) {
			}

			acceptor = (Acceptor)acceptor.next;
		}
	}

	private final Acceptor removeAll() {
		synchronized (this) {
			final Acceptor acceptor = this.acceptors;
			this.acceptors = null;

			if (this.iter != null) {
				this.iter.toFirst();
			}

			return acceptor;
		}
	}


	private static final class Iter implements Iterator<AIOAcceptor> {

		private final AcceptorSetImpl acceptorSet;

		private Acceptor next;

		private Acceptor current;


		Iter(final AcceptorSetImpl acceptorSet) {
			this.acceptorSet = acceptorSet;
		}


		private final void toFirst() {
			synchronized (this.acceptorSet) {
				this.next = this.acceptorSet.acceptors;
				this.current = null;
			}
		}

		public final boolean hasNext() {
			synchronized (this.acceptorSet) {
				return (this.next != null);
			}
		}

		public final AIOAcceptor next() {
			synchronized (this.acceptorSet) {
				this.current = this.next;

				if (this.next != null) {
					this.next = (Acceptor)this.next.next;
				}

				return this.current;
			}
		}

		public final void remove() {
			final Acceptor acceptor;
			synchronized (this.acceptorSet) {
				acceptor = this.current;
				this.current = null;
			}

			if (acceptor != null) {
				acceptor.free();
			}
		}

	}

}
