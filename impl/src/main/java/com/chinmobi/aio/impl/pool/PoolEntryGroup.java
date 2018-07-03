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
package com.chinmobi.aio.impl.pool;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import com.chinmobi.aio.AIOInetEndpoint;
import com.chinmobi.aio.service.AIOConnection;

/**
 * @author <a href="mailto:yuzhaoping1970@gmail.com">Zhaoping Yu</a>
 *
 */
public final class PoolEntryGroup {

	private static final int MAX_CACHED_CLOSED = 8;

	private final AtomicReference<Node> aliveNodes;
	private final AtomicReference<PoolEntry> closedEntries;
	private final AtomicInteger closedCount;

	private final ConnectionPool pool;


	PoolEntryGroup(final ConnectionPool pool) {
		this.aliveNodes = new AtomicReference<Node>();
		this.closedEntries = new AtomicReference<PoolEntry>();
		this.closedCount = new AtomicInteger();

		this.pool = pool;
	}


	public final ConnectionPool pool() {
		return this.pool;
	}

	public final AIOConnection leaseConnection(final String hostName, final int port) {
		AIOConnection connection;

		final Node node = getAliveNode(hostName, port);
		if (node != null) {
			connection = pollConnection(node);
			if (connection != null) {
				return connection;
			}
		}

		PoolEntry entry = popClosedEntry();
		if (entry != null) {
			connection = entry.getConnection();
			connection.remoteEndpoint().set(hostName, port);
			entry.setBusy();
			return connection;
		}

		return null;
	}

	private final AIOConnection pollConnection(final Node node) {
		AIOConnection connection;

		PoolEntry pred = null;
		PoolEntry entry = node.popEntry();
		while (entry != null) {
			if (pred != null) {
				pushClosedEntry(pred);
			}

			connection = entry.getConnection();
			if (connection.isOpen()) {
				connection.setSessionTimeout(0, TimeUnit.MILLISECONDS);
				entry.setBusy();
				return connection;
			}

			pred = entry;
			entry = node.popEntry();
		}

		if (pred != null) {
			connection = pred.getConnection();
			pred.setBusy();
			return connection;
		}

		return null;
	}

	final void releaseConnection(final PoolEntry entry,
			final AIOConnection connection, final boolean isOpen) {
		if (isOpen) {
			final String hostName = connection.remoteEndpoint().getHostName();
			final int port = connection.remoteEndpoint().getPort();

			pushAliveEntry(hostName, port, entry);
		} else {
			pushClosedEntry(entry);
		}
	}

	final void closedConnection(final PoolEntry entry,
			final AIOConnection connection) {

		final String hostName = connection.remoteEndpoint().getHostName();
		final int port = connection.remoteEndpoint().getPort();

		final Node node = getAliveNode(hostName, port);
		if (node != null) {
			for (;;) {
				final PoolEntry first = node.firstEntry();

				if (first != null && !first.getConnection().isOpen()) {

					final PoolEntry next = first.next;
					if (node.casFirst(first, next)) {
						first.next = null;
						pushClosedEntry(first);
						continue;
					}
				}

				break;
			}
		}

		clearEmptyNodes();
	}


	public final void clear() {
		Node node = popAliveNode();
		while (node != null) {

			PoolEntry entry = node.popEntry();
			while (entry != null) {
				entry.getConnection().close();

				entry = node.popEntry();
			}

			node = popAliveNode();
		}

		PoolEntry entry = popClosedEntry();
		while (entry != null) {
			entry.getConnection().close();

			entry = popClosedEntry();
		}
	}


	private final Node popAliveNode() {
		for (;;) {
			final Node first = this.aliveNodes.get();

			if (first != null) {
				final Node next = first.next;

				if (!this.aliveNodes.compareAndSet(first, next)) {
					continue;
				}

				first.next = null;
			}

			return first;
		}
	}

	private final Node getAliveNode(final String hostName, final int port) {
		return checkAliveNodes(hostName, port, null);
	}

	private final Node pushAliveEntry(final String hostName, final int port,
			final PoolEntry addEntry) {
		return checkAliveNodes(hostName, port, addEntry);
	}

	private final Node checkAliveNodes(final String hostName, final int port,
			final PoolEntry addEntry) {
		Node newNode = null;
		for (;;) {
			final Node first = this.aliveNodes.get();

			Node node = first;
			while (node != null) {
				if (node.endpoint.equals(hostName, port)) {
					if (addEntry != null) {
						node.pushEntry(addEntry);
						// TODO: there is an occasion to lose the addEntry.
					}
					return node;
				}

				node = node.next;
			}

			if (addEntry != null) {
				if (newNode == null) {
					newNode = new Node(addEntry);
				}
				newNode.next = first; // CAS piggyback
				if (!this.aliveNodes.compareAndSet(first, newNode)) {
					continue;
				}
			}

			break;
		}
		return newNode;
	}

	private final void clearEmptyNodes() {
		for (;;) {
			final Node first = this.aliveNodes.get();

			final PoolEntry entry = first.firstEntry();
			if (entry == null) {
				final Node next = first.next;
				if (this.aliveNodes.compareAndSet(first, next)) {
					first.next = null;
				}
				continue;
			}

			break;
		}
	}


	private final PoolEntry popClosedEntry() {
		final PoolEntry entry = doPopEntry(this.closedEntries);
		if (entry != null) {
			this.closedCount.decrementAndGet();
		}
		return entry;
	}

	private final void pushClosedEntry(final PoolEntry entry) {
		if (this.closedCount.incrementAndGet() < MAX_CACHED_CLOSED) {
			doPushEntry(this.closedEntries, entry);
		}
	}


	private static final PoolEntry doPopEntry(final AtomicReference<PoolEntry> entries) {
		for (;;) {
			final PoolEntry first = entries.get();

			if (first != null) {
				final PoolEntry next = first.next;

				if (!entries.compareAndSet(first, next)) {
					continue;
				}

				first.next = null;
			}

			return first;
		}
	}

	private static final void doPushEntry(final AtomicReference<PoolEntry> entries, final PoolEntry entry) {
		for (;;) {
			final PoolEntry first = entries.get();

			entry.next = first; // CAS piggyback
			if (!entries.compareAndSet(first, entry)) {
				continue;
			}

			break;
		}
	}


	private static final class Node {

		private Node next;

		private final AtomicReference<PoolEntry> entries;

		private final AIOInetEndpoint endpoint;


		Node(final PoolEntry first) {
			this.entries = new AtomicReference<PoolEntry>();
			this.endpoint = new AIOInetEndpoint(first.getConnection().remoteEndpoint());
			this.entries.set(first);
		}


		private final PoolEntry firstEntry() {
			return this.entries.get();
		}

		private final boolean casFirst(final PoolEntry expect, final PoolEntry update) {
			return this.entries.compareAndSet(expect, update);
		}

		private final PoolEntry popEntry() {
			return PoolEntryGroup.doPopEntry(this.entries);
		}

		private final void pushEntry(final PoolEntry entry) {
			PoolEntryGroup.doPushEntry(this.entries, entry);
		}

	}

}
