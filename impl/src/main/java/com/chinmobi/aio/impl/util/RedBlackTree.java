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

import java.util.concurrent.locks.Lock;

/**
 * @author <a href="mailto:yuzhaoping1970@gmail.com">Zhaoping Yu</a>
 *
 */
public class RedBlackTree<E extends RedBlackTree.Entry> {

	private static final boolean RED = false;
	private static final boolean BLACK = true;


	public static abstract class Entry {

		private Node<?> node;

		private Node<?> cachedNode;


		protected Entry() {
		}


		protected abstract int compareTo(RedBlackTree.Entry o);

		protected abstract RedBlackTree<?> belongsTree();


		protected void free() {
		}

		protected final boolean isAddedToTree() {
			return (this.node != null);
		}

		protected final boolean removeFromTree() {
			final Node<?> node = this.node;
			if (node != null) {
				final RedBlackTree<?> tree = belongsTree();
				if (tree != null) {
					return tree.doRemove(this);
				}
			}
			return false;
		}

		protected final boolean syncRemoveFromTree(final RedBlackTreeEntryModifiedVerifier verifier, final int modCount) {
			final Node<?> node = this.node;
			if (node != null) {
				final RedBlackTree<?> tree = belongsTree();
				if (tree != null) {
					final Lock lock = tree.lock();
					lock.lock();
					try {
						if (verifier == null || verifier.modificationVerified(modCount)) {
							return tree.doRemove(this);
						}
					} finally {
						lock.unlock();
					}
				}
			}
			return false;
		}

		protected final boolean syncRemoveFromTree() {
			return syncRemoveFromTree(null, 0);
		}

	}


	private static final class WrapperIter<E extends RedBlackTree.Entry>
		implements RedBlackTreeIter<E> {

		private final Synchronizer<E> synchronizer;


		WrapperIter(final Synchronizer<E> synchronizer) {
			this.synchronizer = synchronizer;
		}


		public final boolean hasNext() {
			final Lock lock = this.synchronizer.lock;
			lock.lock();
			try {
				return this.synchronizer.tree.iter.hasNext();
			} finally {
				lock.unlock();
			}
		}

		public final E next() {
			final Lock lock = this.synchronizer.lock;
			lock.lock();
			try {
				return this.synchronizer.tree.iter.next();
			} finally {
				lock.unlock();
			}
		}

		public final void remove() {
			final Lock lock = this.synchronizer.lock;
			lock.lock();
			try {
				this.synchronizer.tree.iter.remove();
			} finally {
				lock.unlock();
			}
		}

	}

	public static final class Synchronizer<E extends RedBlackTree.Entry> {

		private final RedBlackTree<E> tree;

		private final WrapperIter<E> iter;

		private final Lock lock;


		private Synchronizer(final RedBlackTree<E> tree) {
			this.tree = tree;
			this.iter = new WrapperIter<E>(this);

			this.lock = new SmartLock();
		}


		public final RedBlackTreeIter<E> iterator() {
			final Lock lock = this.lock;
			lock.lock();
			try {
				this.tree.iter.init();
			} finally {
				lock.unlock();
			}
			return this.iter;
		}

		public final boolean isEmpty() {
			final Lock lock = this.lock;
			lock.lock();
			try {
				return this.tree.isEmpty();
			} finally {
				lock.unlock();
			}
		}

		public final int size() {
			final Lock lock = this.lock;
			lock.lock();
			try {
				return this.tree.size();
			} finally {
				lock.unlock();
			}
		}

		public final E firstEntry() {
			final Lock lock = this.lock;
			lock.lock();
			try {
				return this.tree.firstEntry();
			} finally {
				lock.unlock();
			}
		}

		public final void put(final E entry) throws IllegalTreeNodeStateException {
			final Lock lock = this.lock;
			lock.lock();
			try {
				this.tree.put(entry);
			} finally {
				lock.unlock();
			}
		}

		public final boolean remove(final Entry entry) throws IllegalTreeNodeStateException {
			final Lock lock = this.lock;
			lock.lock();
			try {
				return this.tree.remove(entry);
			} finally {
				lock.unlock();
			}
		}

		public final void clear() {
			final Lock lock = this.lock;
			lock.lock();
			try {
				this.tree.clear();
			} finally {
				lock.unlock();
			}
		}

	}

	private static final class Node<E extends RedBlackTree.Entry> {

		private E entry;

		private Node<E> parent;

		private Node<E> left;
		private Node<E> right;

		private boolean color;


		private Node() {
		}

		private final void set(final E entry, final Node<E> parent) {
			this.entry = entry;

			this.parent = parent;
			this.left = null;
			this.right = null;

			this.color = BLACK;

			entry.node = this;
		}

	}

	private static final class Iter<E extends RedBlackTree.Entry>
		implements RedBlackTreeIter<E> {

		private final RedBlackTree<E> tree;

		private Node<E> lastReturned;
		private Node<E> next;


		private Iter(final RedBlackTree<E> tree) {
			this.tree = tree;
		}


		public final boolean hasNext() {
			return (this.next != null);
		}

		public final E next() {
			final Node<E> node = this.next;
			this.lastReturned = node;
			if (node != null) {
				this.next = this.tree.successor(node);
				return node.entry;
			} else {
				return null;
			}
		}

		public final void remove() {
			if (this.lastReturned != null) {
				final Node<E> node = this.lastReturned;
				this.lastReturned = null;

				if (node.left != null && node.right != null) {
					this.next = node;
				}

				this.tree.deleteEntryNode(node.entry, node);
			}
		}

		private final void init() {
			this.lastReturned = null;
			this.next = this.tree.firstNode();
		}

	}


	private Node<E> root;

	private final Synchronizer<E> synchronizer;
	private final Iter<E> iter;

	private int size;


	public RedBlackTree() {
		this.synchronizer = new Synchronizer<E>(this);
		this.iter = new Iter<E>(this);
	}


	public final Synchronizer<E> synchronizer() {
		return this.synchronizer;
	}

	public final Lock lock() {
		return this.synchronizer.lock;
	}

	public final RedBlackTreeIter<E> iterator() {
		this.iter.init();
		return this.iter;
	}

	public final boolean isEmpty() {
		return (this.size == 0);
	}

	public final int size() {
		return this.size;
	}

	public final E firstEntry() {
		final Node<E> node = firstNode();
		if (node != null) {
			return node.entry;
		}
		return null;
	}

	public final void put(final E entry) throws IllegalTreeNodeStateException {
		if (entry.node != null) {
			if (entry.belongsTree() == this) {
				return;
			} else {
				throw new IllegalTreeNodeStateException("The adding entry has already belonged to another tree.");
			}
		}

		++this.size;

		Node<E> t = this.root;

		if (t == null) {
			this.root = createEntryNode(entry, null);
			return;
		}

		for (;;) {
			final int cmp = entry.compareTo(t.entry);
			if (cmp < 0) {
				if (t.left != null) {
					t = t.left;
				} else {
					t.left = createEntryNode(entry, t);
					fixAfterInsertion(t.left);
					return;
				}
			} else {
				if (t.right != null) {
					t = t.right;
				} else {
					t.right = createEntryNode(entry, t);
					fixAfterInsertion(t.right);
					return;
				}
			}
		}
	}

	private final Node<E> createEntryNode(final E entry, final Node<E> parent) {
		@SuppressWarnings("unchecked")
		Node<E> node = (Node<E>)entry.cachedNode;
		if (node == null) {
			node = new Node<E>();
		}

		node.set(entry, parent);
		return node;
	}

	public final boolean remove(final Entry entry) throws IllegalTreeNodeStateException {
		if (entry.belongsTree() == this) {
			return doRemove(entry);
		} else {
			throw new IllegalTreeNodeStateException("The deleting entry is not belonging to this tree.");
		}
	}

	private final boolean doRemove(final Entry entry) {
		@SuppressWarnings("unchecked")
		final Node<E> node = (Node<E>)entry.node;

		if (node != null) {
			if (node == this.iter.lastReturned) {
				this.iter.remove();
			} else {
				if (node == this.iter.next) {
					if (node.left == null || node.right == null) {
						this.iter.next = successor(node);
					}
				}

				deleteEntryNode(entry, node);
			}
			return true;
		}

		return false;
	}

	public final void clear() {
		final RedBlackTreeIter<E> iter = iterator();
		Entry entry = iter.next();
		while (entry != null) {
			entry.node.entry = null;
			entry.node = null;
			entry.cachedNode = null;

			entry.free();

			entry = iter.next();
		}

		this.size = 0;
		this.root = null;
		this.iter.init();
	}

	private final void deleteEntryNode(final Entry entry, Node<E> node) {
		node = deleteNode(node);
		entry.cachedNode = node;

		--this.size;
	}


	/**
	 * Returns the first Node in the Tree, null if the Tree is empty.
	 */
	private final Node<E> firstNode() {
		Node<E> p = this.root;
		if (p != null) {
			while (p.left != null) p = p.left;
		}
		return p;
	}

	/**
	 * Returns the last Node in the Tree, null if the Tree is empty.
	 */
	/*private final Node<E> lastNode() {
		Node<E> p = this.root;
		if (p != null) {
			while (p.right != null) p = p.right;
		}
		return p;
	}*/

	/**
	 * Returns the successor of the specified Node, or null if no such.
	 */
	private final Node<E> successor(final Node<E> t) {
		if (t == null) return null;
		else if (t.right != null) {
			Node<E> p = t.right;
			while (p.left != null) p = p.left;
			return p;
		} else {
			Node<E> p = t.parent;
			Node<E> ch = t;
			while (p != null && ch == p.right) {
				ch = p;
				p = p.parent;
			}
			return p;
		}
	}

	/**
	 * Balancing operations.
	 *
	 * Implementations of rebalancings during insertion and deletion are
	 * slightly different than the CLR version.  Rather than using dummy
	 * nilnodes, we use a set of accessors that deal properly with null.  They
	 * are used to avoid messiness surrounding nullness checks in the main
	 * algorithms.
	 */

	private /*static*/ final boolean colorOf(final Node<E> p) {
		return (p == null ? BLACK : p.color);
	}

	private /*static*/ final Node<E> parentOf(final Node<E> p) {
		return (p == null ? null: p.parent);
	}

	private /*static*/ final void setColor(final Node<E> p, final boolean c) {
		if (p != null) p.color = c;
	}

	private /*static*/ final Node<E> leftOf(final Node<E> p) {
		return (p == null) ? null: p.left;
	}

	private /*static*/ final Node<E> rightOf(final Node<E> p) {
		return (p == null) ? null: p.right;
	}


	private final void rotateLeft(final Node<E> p) {
		final Node<E> r = p.right;

		p.right = r.left;
		if (r.left != null) r.left.parent = p;
		r.parent = p.parent;

		if (p.parent == null) this.root = r;
		else if (p.parent.left == p) p.parent.left = r;
		else p.parent.right = r;

		r.left = p;
		p.parent = r;
	}

	private final void rotateRight(final Node<E> p) {
		final Node<E> l = p.left;

		p.left = l.right;
		if (l.right != null) l.right.parent = p;
		l.parent = p.parent;

		if (p.parent == null) this.root = l;
		else if (p.parent.right == p) p.parent.right = l;
		else p.parent.left = l;

		l.right = p;
		p.parent = l;
	}


	private final void fixAfterInsertion(Node<E> x) {
		x.color = RED;

		while (x != null && x != this.root && x.parent.color == RED) {
			if (parentOf(x) == leftOf(parentOf(parentOf(x)))) {
				final Node<E> y = rightOf(parentOf(parentOf(x)));
				if (colorOf(y) == RED) {
					setColor(parentOf(x), BLACK);
					setColor(y, BLACK);
					setColor(parentOf(parentOf(x)), RED);
					x = parentOf(parentOf(x));
				} else {
					if (x == rightOf(parentOf(x))) {
						x = parentOf(x);
						rotateLeft(x);
					}
					setColor(parentOf(x), BLACK);
					setColor(parentOf(parentOf(x)), RED);
					if (parentOf(parentOf(x)) != null) rotateRight(parentOf(parentOf(x)));
				}
			} else {
				final Node<E> y = leftOf(parentOf(parentOf(x)));
				if (colorOf(y) == RED) {
					setColor(parentOf(x), BLACK);
					setColor(y, BLACK);
					setColor(parentOf(parentOf(x)), RED);
					x = parentOf(parentOf(x));
				} else {
					if (x == leftOf(parentOf(x))) {
						x = parentOf(x);
						rotateRight(x);
					}
					setColor(parentOf(x), BLACK);
					setColor(parentOf(parentOf(x)), RED);
					if (parentOf(parentOf(x)) != null) rotateLeft(parentOf(parentOf(x)));
				}
			}
		}
		this.root.color = BLACK;
	}

	/**
	 * Delete node p, and then rebalance the tree.
	 */

	private final Node<E> deleteNode(Node<E> p) {
		p.entry.node = null;

		// If strictly internal, copy successor's element to p and then make p
		// point to successor.
		if (p.left != null && p.right != null) {
			final Node<E> s = successor(p);
			p.entry = s.entry;
			p.entry.node = p;
			p = s;
		} // p has 2 children

		p.entry = null;

		// Start fixup at replacement node, if it exists.
		final Node<E> child = (p.left != null ? p.left : p.right);

		if (child != null) {
			// Link replacement to parent
			child.parent = p.parent;

			if (p.parent == null) this.root = child;
			else if (p == p.parent.left) p.parent.left = child;
			else p.parent.right = child;

			// Null out links so they are OK to use by fixAfterDeletion.
			p.left = p.right = p.parent = null;

			// Fix replacement
			if (p.color == BLACK) fixAfterDeletion(child);
		} else if (p.parent == null) { // return if we are the only node.
			this.root = null;
		} else { // No children. Use self as phantom replacement and unlink.
			if (p.color == BLACK) fixAfterDeletion(p);

			if (p.parent != null) {
				if (p == p.parent.left) p.parent.left = null;
				else if (p == p.parent.right) p.parent.right = null;
				p.parent = null;
			}
		}

		return p;
	}

	private final void fixAfterDeletion(Node<E> x) {
		while (x != this.root && colorOf(x) == BLACK) {
			if (x == leftOf(parentOf(x))) {
				Node<E> sib = rightOf(parentOf(x));

				if (colorOf(sib) == RED) {
					setColor(sib, BLACK);
					setColor(parentOf(x), RED);
					rotateLeft(parentOf(x));
					sib = rightOf(parentOf(x));
				}

				if (colorOf(leftOf(sib)) == BLACK && colorOf(rightOf(sib)) == BLACK) {
					setColor(sib, RED);
					x = parentOf(x);
				} else {
					if (colorOf(rightOf(sib)) == BLACK) {
						setColor(leftOf(sib), BLACK);
						setColor(sib, RED);
						rotateRight(sib);
						sib = rightOf(parentOf(x));
					}
					setColor(sib, colorOf(parentOf(x)));
					setColor(parentOf(x), BLACK);
					setColor(rightOf(sib), BLACK);
					rotateLeft(parentOf(x));
					x = this.root;
				}
			} else { // symmetric
				Node<E> sib = leftOf(parentOf(x));

				if (colorOf(sib) == RED) {
					setColor(sib, BLACK);
					setColor(parentOf(x), RED);
					rotateRight(parentOf(x));
					sib = leftOf(parentOf(x));
				}

				if (colorOf(rightOf(sib)) == BLACK && colorOf(leftOf(sib)) == BLACK) {
					setColor(sib, RED);
					x = parentOf(x);
				} else {
					if (colorOf(leftOf(sib)) == BLACK) {
						setColor(rightOf(sib), BLACK);
						setColor(sib, RED);
						rotateLeft(sib);
						sib = leftOf(parentOf(x));
					}
					setColor(sib, colorOf(parentOf(x)));
					setColor(parentOf(x), BLACK);
					setColor(leftOf(sib), BLACK);
					rotateRight(parentOf(x));
					x = this.root;
				}
			}
		}

		setColor(x, BLACK);
	}

}
