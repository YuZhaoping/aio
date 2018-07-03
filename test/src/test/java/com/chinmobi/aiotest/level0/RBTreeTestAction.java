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
package com.chinmobi.aiotest.level0;

import com.chinmobi.aio.impl.util.RedBlackTree;
import com.chinmobi.aio.impl.util.RedBlackTreeIter;
import com.chinmobi.testapp.BaseTestAction;

/**
 * @author <a href="mailto:yuzhaoping1970@gmail.com">Zhaoping Yu</a>
 *
 */
public final class RBTreeTestAction extends BaseTestAction {

	public final class TestEntry extends RedBlackTree.Entry {

		private final int value;


		public TestEntry(final int value) {
			this.value = value;
		}


		public final int value() {
			return this.value;
		}

		@Override
		protected final int compareTo(final RedBlackTree.Entry entry) {
			final TestEntry o = (TestEntry)entry;

			return (this.value - o.value);
		}

		@Override
		protected final RedBlackTree<TestEntry> belongsTree() {
			return RBTreeTestAction.this.tree;
		}

		@Override
		protected final void free() {
			RBTreeTestAction.this.count++;
		}

		public final boolean isInTree() {
			return isAddedToTree();
		}

		public final void remove() {
			removeFromTree();
		}

	}


	private final RedBlackTree<TestEntry> tree;

	private int count;


	public RBTreeTestAction() {
		super();
		this.tree = new RedBlackTree<TestEntry>();
		this.count = 0;
	}


	/*
	 * Test methods
	 */

	public final void testPut0() {
		final RedBlackTree<TestEntry> tree = this.tree;

		TestEntry entry;

		assertNull(tree.firstEntry());

		RedBlackTreeIter<TestEntry> iter = tree.iterator();
		assertFalse(iter.hasNext());

		int i = 0;
		for (; i < 9; ++i) {
			tree.put(new TestEntry(i));
		}

		assertNotNull(tree.firstEntry());
		iter = tree.iterator();
		assertTrue(iter.hasNext());


		i = 0;
		entry = iter.next();
		while (entry != null) {
			assertEquals((int)i, entry.value());
			assertTrue(entry.isInTree());

			++i;

			entry = iter.next();
		}
		assertEquals((int)9, i);
		assertFalse(iter.hasNext());


		i = 0;
		iter = tree.synchronizer().iterator();
		entry = iter.next();
		while (entry != null) {
			assertEquals((int)i, entry.value());
			assertTrue(entry.isInTree());

			++i;

			entry = iter.next();
		}
		assertEquals((int)9, i);
		assertFalse(iter.hasNext());


		this.count = 0;
		tree.clear();
		assertEquals((int)9, this.count);

		assertNull(tree.firstEntry());
		iter = tree.iterator();
		assertFalse(iter.hasNext());
	}

	public final void testPut1() {
		final RedBlackTree<TestEntry> tree = this.tree;

		TestEntry entry;

		assertNull(tree.firstEntry());

		RedBlackTreeIter<TestEntry> iter = tree.iterator();
		assertFalse(iter.hasNext());

		int i = 8;
		for (; i >= 0; --i) {
			tree.put(new TestEntry(i));
		}

		assertNotNull(tree.firstEntry());
		iter = tree.iterator();
		assertTrue(iter.hasNext());


		i = 0;
		entry = iter.next();
		while (entry != null) {
			assertEquals((int)i, entry.value());
			assertTrue(entry.isInTree());

			++i;

			entry = iter.next();
		}
		assertEquals((int)9, i);
		assertFalse(iter.hasNext());


		i = 0;
		iter = tree.synchronizer().iterator();
		entry = iter.next();
		while (entry != null) {
			assertEquals((int)i, entry.value());
			assertTrue(entry.isInTree());

			++i;

			entry = iter.next();
		}
		assertEquals((int)9, i);
		assertFalse(iter.hasNext());


		this.count = 0;
		tree.clear();
		assertEquals((int)9, this.count);

		assertNull(tree.firstEntry());
		iter = tree.iterator();
		assertFalse(iter.hasNext());
	}

	public final void testPut2() {
		final RedBlackTree<TestEntry> tree = this.tree;

		TestEntry entry;

		assertNull(tree.firstEntry());

		RedBlackTreeIter<TestEntry> iter = tree.iterator();
		assertFalse(iter.hasNext());

		int i = 0;
		for (; i < 5; ++i) {
			if (i < 4) {
				tree.put(new TestEntry(5+i));
			}
			tree.put(new TestEntry(i));
		}

		assertNotNull(tree.firstEntry());
		iter = tree.iterator();
		assertTrue(iter.hasNext());


		i = 0;
		entry = iter.next();
		while (entry != null) {
			assertEquals((int)i, entry.value());
			assertTrue(entry.isInTree());

			++i;

			entry = iter.next();
		}
		assertEquals((int)9, i);
		assertFalse(iter.hasNext());


		i = 0;
		iter = tree.synchronizer().iterator();
		entry = iter.next();
		while (entry != null) {
			assertEquals((int)i, entry.value());
			assertTrue(entry.isInTree());

			++i;

			entry = iter.next();
		}
		assertEquals((int)9, i);
		assertFalse(iter.hasNext());


		this.count = 0;
		tree.clear();
		assertEquals((int)9, this.count);

		assertNull(tree.firstEntry());
		iter = tree.iterator();
		assertFalse(iter.hasNext());
	}

	public final void testPut3() {
		final RedBlackTree<TestEntry> tree = this.tree;

		TestEntry entry;

		assertNull(tree.firstEntry());

		RedBlackTreeIter<TestEntry> iter = tree.iterator();
		assertFalse(iter.hasNext());

		int i = 0;
		for (; i <= 5; ++i) {
			if (i < 4) {
				tree.put(new TestEntry(5+i));
			}
			if (i > 0) {
				tree.put(new TestEntry(5-i));
			}
		}

		assertNotNull(tree.firstEntry());
		iter = tree.iterator();
		assertTrue(iter.hasNext());


		i = 0;
		entry = iter.next();
		while (entry != null) {
			assertEquals((int)i, entry.value());
			assertTrue(entry.isInTree());

			++i;

			entry = iter.next();
		}
		assertEquals((int)9, i);
		assertFalse(iter.hasNext());


		i = 0;
		iter = tree.synchronizer().iterator();
		entry = iter.next();
		while (entry != null) {
			assertEquals((int)i, entry.value());
			assertTrue(entry.isInTree());

			++i;

			entry = iter.next();
		}
		assertEquals((int)9, i);
		assertFalse(iter.hasNext());


		this.count = 0;
		tree.clear();
		assertEquals((int)9, this.count);

		assertNull(tree.firstEntry());
		iter = tree.iterator();
		assertFalse(iter.hasNext());
	}

	public final void testPut4() {
		final RedBlackTree<TestEntry> tree = this.tree;

		TestEntry entry;

		assertNull(tree.firstEntry());

		RedBlackTreeIter<TestEntry> iter = tree.iterator();
		assertFalse(iter.hasNext());

		int i = 0;
		for (; i <= 5; ++i) {
			if (i > 0) {
				tree.put(new TestEntry(5-i));
			}
			if (i < 4) {
				tree.put(new TestEntry(5+i));
			}
		}

		assertNotNull(tree.firstEntry());
		iter = tree.iterator();
		assertTrue(iter.hasNext());


		i = 0;
		entry = iter.next();
		while (entry != null) {
			assertEquals((int)i, entry.value());
			assertTrue(entry.isInTree());

			++i;

			entry = iter.next();
		}
		assertEquals((int)9, i);
		assertFalse(iter.hasNext());


		i = 0;
		iter = tree.synchronizer().iterator();
		entry = iter.next();
		while (entry != null) {
			assertEquals((int)i, entry.value());
			assertTrue(entry.isInTree());

			++i;

			entry = iter.next();
		}
		assertEquals((int)9, i);
		assertFalse(iter.hasNext());


		this.count = 0;
		tree.clear();
		assertEquals((int)9, this.count);

		assertNull(tree.firstEntry());
		iter = tree.iterator();
		assertFalse(iter.hasNext());
	}

	public final void testPut5() {
		final RedBlackTree<TestEntry> tree = this.tree;

		TestEntry entry;

		assertNull(tree.firstEntry());

		RedBlackTreeIter<TestEntry> iter = tree.iterator();
		assertFalse(iter.hasNext());

		int i = 1;
		for (; i <= 5; ++i) {
			if (i > 0) {
				tree.put(new TestEntry(5-i));
			}
			if (i < 4) {
				tree.put(new TestEntry(5+i));
			}
		}
		tree.put(new TestEntry(5));

		assertNotNull(tree.firstEntry());
		iter = tree.iterator();
		assertTrue(iter.hasNext());


		i = 0;
		entry = iter.next();
		while (entry != null) {
			assertEquals((int)i, entry.value());
			assertTrue(entry.isInTree());

			++i;

			entry = iter.next();
		}
		assertEquals((int)9, i);
		assertFalse(iter.hasNext());


		i = 0;
		iter = tree.synchronizer().iterator();
		entry = iter.next();
		while (entry != null) {
			assertEquals((int)i, entry.value());
			assertTrue(entry.isInTree());

			++i;

			entry = iter.next();
		}
		assertEquals((int)9, i);
		assertFalse(iter.hasNext());


		this.count = 0;
		tree.clear();
		assertEquals((int)9, this.count);

		assertNull(tree.firstEntry());
		iter = tree.iterator();
		assertFalse(iter.hasNext());
	}

	public final void testRemove0() {
		final RedBlackTree<TestEntry> tree = this.tree;

		TestEntry entry = null;

		for (int j = 0; j < 9; ++j) {
			final String msg = "At step: [" + j + "]";

			assertNull(msg, tree.firstEntry());

			RedBlackTreeIter<TestEntry> iter = tree.iterator();
			assertFalse(msg, iter.hasNext());

			int i = 0;
			for (; i < 9; ++i) {
				if (i == j) {
					entry = new TestEntry(i);
					tree.put(entry);
				} else {
					tree.put(new TestEntry(i));
				}
			}

			assertNotNull(msg, tree.firstEntry());
			iter = tree.iterator();
			assertTrue(msg, iter.hasNext());

			assertNotNull(entry);
			if (entry != null) {
				assertTrue(msg, entry.isInTree());
				assertTrue(msg, tree.remove(entry));
				assertFalse(msg, entry.isInTree());
			}

			i = 0;
			entry = iter.next();
			while (entry != null) {
				assertTrue(msg, entry.isInTree());

				++i;

				entry = iter.next();
			}
			assertEquals(msg, (int)8, i);

			i = 0;
			iter = tree.iterator();
			entry = iter.next();
			while (entry != null) {
				assertTrue(msg, entry.isInTree());
				iter.remove();
				assertFalse(msg, entry.isInTree());

				++i;

				entry = iter.next();
			}
			assertEquals(msg, (int)8, i);
		}
	}

	public final void testRemove1() {
		final RedBlackTree<TestEntry> tree = this.tree;

		TestEntry entry = null;

		for (int j = 0; j < 9; ++j) {
			final String msg = "At step: [" + j + "]";

			assertNull(msg, tree.firstEntry());

			RedBlackTreeIter<TestEntry> iter = tree.iterator();
			assertFalse(msg, iter.hasNext());

			int i = 0;
			for (; i < 9; ++i) {
				if (i == j) {
					entry = new TestEntry(i);
					tree.put(entry);
				} else {
					tree.put(new TestEntry(i));
				}
			}

			assertNotNull(msg, tree.firstEntry());
			iter = tree.iterator();
			assertTrue(msg, iter.hasNext());

			i = 0;
			TestEntry tmp = iter.next();
			while (tmp != null) {
				if (tmp.value() == j) {
					break;
				}

				++i;

				tmp = iter.next();
			}

			assertNotNull(entry);
			if (entry != null) {
				assertTrue(msg, entry.isInTree());
				assertTrue(msg, tree.remove(entry));
				assertFalse(msg, entry.isInTree());
			}

			entry = iter.next();
			while (entry != null) {
				assertTrue(msg, entry.isInTree());

				++i;

				entry = iter.next();
			}
			assertEquals(msg, (int)8, i);

			i = 0;
			iter = tree.iterator();
			entry = iter.next();
			while (entry != null) {
				assertTrue(msg, entry.isInTree());
				iter.remove();
				assertFalse(msg, entry.isInTree());

				++i;

				entry = iter.next();
			}
			assertEquals(msg, (int)8, i);
		}
	}

	public final void testRemove2() {
		final RedBlackTree<TestEntry> tree = this.tree;

		TestEntry entry = null;

		for (int j = 0; j < 9; ++j) {
			final String msg = "At step: [" + j + "]";

			assertNull(msg, tree.firstEntry());

			RedBlackTreeIter<TestEntry> iter = tree.iterator();
			assertFalse(msg, iter.hasNext());

			int i = 0;
			for (; i < 9; ++i) {
				if (i == j) {
					entry = new TestEntry(i);
					tree.put(entry);
				} else {
					tree.put(new TestEntry(i));
				}
			}

			assertNotNull(msg, tree.firstEntry());
			iter = tree.iterator();
			assertTrue(msg, iter.hasNext());

			i = 0;
			TestEntry tmp = iter.next();
			while (tmp != null) {
				if (tmp.value() == j) {
					if (iter.hasNext()) ++i;
					iter.next();
					break;
				}

				++i;

				tmp = iter.next();
			}

			assertNotNull(entry);
			if (entry != null) {
				assertTrue(msg, entry.isInTree());
				assertTrue(msg, tree.remove(entry));
				assertFalse(msg, entry.isInTree());
			}

			entry = iter.next();
			while (entry != null) {
				assertTrue(msg, entry.isInTree());

				++i;

				entry = iter.next();
			}
			assertEquals(msg, (int)8, i);

			i = 0;
			iter = tree.iterator();
			entry = iter.next();
			while (entry != null) {
				assertTrue(msg, entry.isInTree());
				iter.remove();
				assertFalse(msg, entry.isInTree());

				++i;

				entry = iter.next();
			}
			assertEquals(msg, (int)8, i);
		}
	}

	public final void testRemove3() {
		final RedBlackTree<TestEntry> tree = this.tree;

		TestEntry entry = null;

		for (int j = 0; j < 9; ++j) {
			final String msg = "At step: [" + j + "]";

			assertNull(msg, tree.firstEntry());

			RedBlackTreeIter<TestEntry> iter = tree.iterator();
			assertFalse(msg, iter.hasNext());

			int i = 0;
			for (; i < 9; ++i) {
				if (i == j) {
					entry = new TestEntry(i);
					tree.put(entry);
				} else {
					tree.put(new TestEntry(i));
				}
			}

			assertNotNull(msg, tree.firstEntry());
			iter = tree.iterator();
			assertTrue(msg, iter.hasNext());

			i = 0;
			TestEntry tmp = iter.next();
			while (tmp != null) {
				if (j == 0) break;

				++i;

				if (tmp.value() == (j - 1)) {
					break;
				}

				tmp = iter.next();
			}

			assertNotNull(entry);
			if (entry != null) {
				assertTrue(msg, entry.isInTree());
				assertTrue(msg, tree.remove(entry));
				assertFalse(msg, entry.isInTree());
			}

			entry = iter.next();
			while (entry != null) {
				assertTrue(msg, entry.isInTree());

				++i;

				entry = iter.next();
			}
			assertEquals(msg, (int)8, i);

			i = 0;
			iter = tree.iterator();
			entry = iter.next();
			while (entry != null) {
				assertTrue(msg, entry.isInTree());
				iter.remove();
				assertFalse(msg, entry.isInTree());

				++i;

				entry = iter.next();
			}
			assertEquals(msg, (int)8, i);
		}
	}

	public final void testRemove4() {
		final RedBlackTree<TestEntry> tree = this.tree;

		TestEntry entry;

		for (int j = 0; j < 9; ++j) {
			final String msg = "At step: [" + j + "]";

			assertNull(msg, tree.firstEntry());

			RedBlackTreeIter<TestEntry> iter = tree.iterator();
			assertFalse(msg, iter.hasNext());

			int i = 0;
			for (; i < 9; ++i) {
				tree.put(new TestEntry(i));
			}

			assertNotNull(msg, tree.firstEntry());
			iter = tree.iterator();
			assertTrue(msg, iter.hasNext());


			i = 0;
			entry = iter.next();
			while (entry != null) {
				assertTrue(msg, entry.isInTree());

				if (i == j) {
					iter.remove();
					assertFalse(msg, entry.isInTree());
				}

				++i;

				entry = iter.next();
			}
			assertEquals(msg, (int)9, i);


			i = 0;
			iter = tree.iterator();
			entry = iter.next();
			while (entry != null) {
				assertTrue(msg, entry.isInTree());
				assertTrue(msg, tree.remove(entry));
				assertFalse(msg, entry.isInTree());

				++i;

				entry = iter.next();
			}
			assertEquals(msg, (int)8, i);
		}
	}

}
