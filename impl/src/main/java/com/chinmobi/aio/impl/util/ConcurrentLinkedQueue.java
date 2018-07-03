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
package com.chinmobi.aio.impl.util;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author <a href="mailto:yuzhaoping1970@gmail.com">Zhaoping Yu</a>
 *
 */
public class ConcurrentLinkedQueue<E extends ConcurrentLinkedQueue.Entry> {


	public static class Entry {

		private final AtomicReference<Node<?>> node;


		public Entry() {
			this.node = new AtomicReference<Node<?>>();
		}


		/**
		 *
		 * @return the element belongs queue, null if not added any queue
		 */
		public final ConcurrentLinkedQueue<?> belongsQueue() {
			final Node<?> node = this.node.get();
			if (node != null) {
				return node.belongsQueue;
			}
			return null;
		}

		/**
		 *
		 * @return true if the element is added a queue
		 */
		public final boolean isQueued() {
			return (belongsQueue() != null);
		}

		private final int enqueue(final ConcurrentLinkedQueue<?> queue,
				final char traceCode, final Object attachment) {
			//checkNotNull(queue);

			final ConcurrentLinkedQueue<?> belongsQueue = belongsQueue();

			if (belongsQueue != null) {
				return (belongsQueue == queue) ? 0 : -1;
			}

			enqueued(queue, traceCode, attachment);

			queue.doOffer(this, traceCode, attachment);

			return 1;
		}

		protected final int dequeue(final ConcurrentLinkedQueue<?> queue) {
			//checkNotNull(queue);

			final Node<?> node = this.node.get();
			if (node != null) {
				final ConcurrentLinkedQueue<?> belongsQueue = node.belongsQueue;

				if (belongsQueue == queue) {
					if (queue.doRemove(node, this)) {
						return 1;
					} else {
						return 0;
					}
				} else if (belongsQueue != null) {
					return -1;
				}
			}

			return 0;
		}

		/**
		 *
		 * @return 1 - if success;
		 *         0 - if the element is not added any queue;
		 */
		public final int dequeue() {
			for (;;) {
				final ConcurrentLinkedQueue<?> queue = belongsQueue();
				if (queue != null) {
					final int status = dequeue(queue);
					if (status >= 0) {
						return status;
					} else {
						continue;
					}
				}
				return 0;
			}
		}


		protected void enqueued(final ConcurrentLinkedQueue<?> queue,
				final char traceCode, final Object attachment) {
		}

		private final void onEnqueued(final ConcurrentLinkedQueue<?> queue, final Node<?> node) {
			if (this.node.compareAndSet(null, node)) {
				node.state.set(1);
			} else {
				node.state.set(2);
			}
		}

		private final void onDequeued(final ConcurrentLinkedQueue<?> queue, final Node<?> node,
				final boolean onClear) {

			final char traceCode = node.traceCode;
			final Object attachment = node.attachment;
			node.attachment = null;

			for (;;) {
				final int state = node.state.get();
				switch (state) {
				case -1:
					continue;

				case 2:
					node.state.set(0);
				case 0:
					break;

				//case 1:
				default:
					if (!this.node.compareAndSet(node, null)) {
						continue;
					}
					node.state.set(0);
				}

				break;
			}

			dequeued(onClear, queue, traceCode, attachment);
		}

		protected void dequeued(final boolean onClear, final ConcurrentLinkedQueue<?> queue,
				final char traceCode, final Object attachment) {
		}


		Node<?> createNode() {
			return null;
		}

		void freeNode(final Node<?> node) {
		}

	}


	public static class NodeCacheableEntry extends Entry {

		private final AtomicReference<Node<?>> cachedNode;


		public NodeCacheableEntry() {
			super();
			this.cachedNode = new AtomicReference<Node<?>>();
		}


		@Override
		final Node<?> createNode() {
			final Node<?> node = this.cachedNode.get();
			if (node != null) {
				if (node.state.get() != 0 || !this.cachedNode.compareAndSet(node, null)) {
					return null;
				}
			}
			return node;
		}

		@Override
		final void freeNode(final Node<?> node) {
			this.cachedNode.set(node);
		}

	}


	private final Node<E> createEntryNode(final E entry,
			final char traceCode, final Object attachment) {
		@SuppressWarnings("unchecked")
		Node<E> node = (Node<E>)entry.createNode();
		if (node == null) {
			node = new Node<E>();
		}

		node.traceCode = traceCode;
		node.attachment = attachment;

		node.belongsQueue = this;
		node.setItem(entry);
		node.state.set(-1);

		return node;
	}

	private final void freeEntryNode(final Node<E> node) {
		node.setNext(null);
	}


	protected static final class Node<E> {

		private final AtomicReference<Node<E>> next;

		private final AtomicReference<E> item;

		private final AtomicInteger state;

		private ConcurrentLinkedQueue<?> belongsQueue;

		private char traceCode;
		private Object attachment;


		Node() {
			this.next = new AtomicReference<Node<E>>();
			this.item = new AtomicReference<E>();
			this.state = new AtomicInteger(0);
		}


		protected final Node<E> getNext() {
			return this.next.get();
		}

		private final boolean casNext(final Node<E> expect, final Node<E> update) {
			return this.next.compareAndSet(expect, update);
		}

		private final void setNext(final Node<E> newValue) {
			this.next.set(newValue);
		}

		protected final E getItem() {
			return this.item.get();
		}

		private final boolean casItem(final E expect, final E update) {
			return this.item.compareAndSet(expect, update);
		}

		private final void setItem(final E newValue) {
			this.item.set(newValue);
		}

	}


	/**
	 * Pointer to header node, initialized to a dummy node.  The first
	 * actual node is at head.getNext().
	 */
	private final AtomicReference<Node<E>> head;

	/** Pointer to last node on list **/
	private final AtomicReference<Node<E>> tail;


	private final boolean casHead(final Node<E> expect, final Node<E> update) {
		if (this.head.compareAndSet(expect, update)) {
			onHeadChanged(expect, update);
			return true;
		}
		return false;
	}

	private final boolean casTail(final Node<E> expect, final Node<E> update) {
		return this.tail.compareAndSet(expect, update);
	}


	public ConcurrentLinkedQueue() {
		final Node<E> dummy = new Node<E>();
		this.head = new AtomicReference<Node<E>>(dummy);
		this.tail = new AtomicReference<Node<E>>(dummy);
	}


	/**
	 * Inserts the specified element at the tail of this queue.
	 *
	 * @return {@code true} - if success;
	 *         {@code false} - if the element is already added to this queue;
	 * @throws IllegalQueueNodeStateException if the specified element is
	 *         already added to another queue
	 * @throws NullPointerException if the specified element is null
	 */
	public final boolean add(final E e)
			throws IllegalQueueNodeStateException {
		return add(e, ' ', null);
	}

	public final boolean add(final E e, final char traceCode, final Object attachment)
			throws IllegalQueueNodeStateException {
		switch (offer(e, traceCode, attachment)) {
		case -1:
			throw new IllegalQueueNodeStateException();
		case 0:
			return false;
		default:
			return true;
		}
	}

	/**
	 * Inserts the specified element at the tail of this queue.
	 *
	 * @return 1 - if success;
	 *         0 - if the element is already added to this queue;
	 *        -1 - if the element is already added to another queue;
	 * @throws NullPointerException if the specified element is null
	 */
	public final int offer(final E e) {
		return offer(e, ' ', null);
	}

	public final int offer(final E e, final char traceCode, final Object attachment) {
		checkNotNull(e);
		return e.enqueue(this, traceCode, attachment);
	}

	private final void doOffer(final Entry e, final char traceCode, final Object attachment) {
		@SuppressWarnings("unchecked")
		final Node<E> n = createEntryNode((E)e, traceCode, attachment);

		for (;;) {
			final Node<E> t = this.tail.get();
			final Node<E> s = t.getNext();
			if (t == this.tail.get()) {
				if (s == null) {
					if (t.casNext(s, n)) {
						e.onEnqueued(this, n);
						casTail(t, n);
						return;
					}
				} else {
					casTail(t, s);
				}
			}
		}
	}

	public final E poll() {
		return poll(false);
	}

	private final E poll(final boolean onClear) {
		for (;;) {
			final Node<E> h = this.head.get();
			final Node<E> t = this.tail.get();
			final Node<E> first = h.getNext();
			if (h == this.head.get()) {
				if (h == t) {
					if (first == null) {
						return null;
					} else {
						casTail(t, first);
					}
				} else
				if (casHead(h, first)) {
					freeEntryNode(h);

					final E item = first.getItem();

					first.belongsQueue = null;
					if (item != null && first.casItem(item, null)) {

						onItemRemoved(item);

						item.freeNode(h);

						item.onDequeued(this, first, onClear);

						return item;
					}

					// else skip over deleted item,
					// continue loop
				}
			}
		}
	}

	public final E peek() { // same as poll except don't remove item
		for (;;) {
			final Node<E> h = this.head.get();
			final Node<E> t = this.tail.get();
			final Node<E> first = h.getNext();
			if (h == this.head.get()) {
				if (h == t) {
					if (first == null) {
						return null;
					} else {
						casTail(t, first);
					}
				} else {
					final E item = first.getItem();
					if (item != null) {
						return item;
					} else { // remove deleted node and continue
						if (casHead(h, first)) {
							freeEntryNode(h);
						}
					}
				}
			}
		}
	}

	public final void clear() {
		while (poll(true) != null);
	}

	@SuppressWarnings("unchecked")
	private final boolean doRemove(final Node<?> node, final Entry item) {
		return remove((Node<E>)node, (E)item);
	}

	private final boolean remove(final Node<E> node, final E item) {
		node.belongsQueue = null;
		if (node.casItem(item, null)) {

			onItemRemoved(item);

			first(item);

			item.onDequeued(this, node, false);

			return true;
		}

		return false;
	}

	/**
	 * Throws NullPointerException if argument is null.
	 *
	 * @param v the element
	 */
	private static void checkNotNull(final Object v) {
		if (v == null) {
			throw new NullPointerException();
		}
	}

	protected void onHeadChanged(final Node<E> oldValue, final Node<E> newValue) {
	}

	protected void onItemRemoved(final E item) {
	}

	/**
	 * Returns the first actual (non-header) node on list.  This is yet
	 * another variant of poll/peek; here returning out the first
	 * node, not element (so we cannot collapse with peek() without
	 * introducing race.)
	 */
	final Node<E> first() {
		return first(null);
	}

	private final Node<E> first(final E removedItem) {
		for (;;) {
			final Node<E> h = this.head.get();
			final Node<E> t = this.tail.get();
			final Node<E> first = h.getNext();
			if (h == this.head.get()) {
				if (h == t) {
					if (first == null) {
						return null;
					} else {
						casTail(t, first);
					}
				} else {
					if (first.getItem() != null) {
						return first;
					} else { // remove deleted node and continue
						if (casHead(h, first)) {
							freeEntryNode(h);

							if (removedItem != null) {
								removedItem.freeNode(h);
							}
						}
					}
				}
			}
		}
	}

}
