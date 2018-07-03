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

import java.util.Iterator;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author <a href="mailto:yuzhaoping1970@gmail.com">Zhaoping Yu</a>
 *
 */
public final class IterableLinkedQueue<E extends ConcurrentLinkedQueue.Entry>
	extends ConcurrentLinkedQueue<E> implements Iterable<E> {

	private final Iter<E> iter;


	public IterableLinkedQueue() {
		super();
		this.iter = new Iter<E>(this);
	}


	public final Iterator<E> iterator() {
		this.iter.setFirst();
		return this.iter;
	}


	@Override
	protected final void onHeadChanged(final Node<E> oldValue, final Node<E> newValue) {
		this.iter.updateNextNode(oldValue, newValue);
	}

	@Override
	protected final void onItemRemoved(final E item) {
		this.iter.removeItem(item);
	}


	private static final class Iter<E extends ConcurrentLinkedQueue.Entry>
		implements Iterator<E> {

		private final ConcurrentLinkedQueue<E> queue;

		/**
		 * Next node to return item for.
		 */
		private final AtomicReference<Node<E>> nextNode;

		private final AtomicReference<E> lastRetItem;


		Iter(final ConcurrentLinkedQueue<E> queue) {
			this.queue = queue;

			this.nextNode = new AtomicReference<Node<E>>();
			this.lastRetItem = new AtomicReference<E>();
		}


		private final void setFirst() {
			this.nextNode.set(this.queue.first());
		}

		private final void updateNextNode(final Node<E> oldValue, final Node<E> newValue) {
			this.nextNode.compareAndSet(oldValue, newValue);
		}

		private final void removeItem(final E item) {
			this.lastRetItem.compareAndSet(item, null);
		}


		public final boolean hasNext() {
			return (this.nextNode.get() != null);
		}

		public final E next() {
			final Node<E> curr = this.nextNode.get();
			if (curr != null) {
				return advance(curr);
			}
			return null;
		}

		public final void remove() {
			final E item = this.lastRetItem.getAndSet(null);
			if (item != null) {
				item.dequeue(this.queue);
			}
		}

		/**
		 * Moves to next valid node and returns item to return for
		 * next(), or null if no such.
		 */
		private final E advance(final Node<E> curr) {

			final E x = curr.getItem();

			this.lastRetItem.set(x);

			Node<E> p = curr.getNext();
			for (;;) {
				if (p == null) {
					this.nextNode.compareAndSet(curr, null);
					return x;
				}

				final E item = p.getItem();
				if (item != null) {
					this.nextNode.compareAndSet(curr, p);
					return x;
				} else { // skip over nulls
					if (curr == this.nextNode.get()) {
						p = p.getNext();
					} else {
						p = this.queue.first();
					}
				}
			}
		}

	}

}
