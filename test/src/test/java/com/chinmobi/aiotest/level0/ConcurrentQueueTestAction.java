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

import java.util.Iterator;

import com.chinmobi.aio.impl.util.ConcurrentLinkedQueue;
import com.chinmobi.aio.impl.util.ConcurrentLinkedQueue.Entry;
import com.chinmobi.aio.impl.util.IterableLinkedQueue;
import com.chinmobi.testapp.BaseTestAction;

/**
 * @author <a href="mailto:yuzhaoping1970@gmail.com">Zhaoping Yu</a>
 *
 */
public class ConcurrentQueueTestAction extends BaseTestAction {


	public final class TestEntry extends Entry {

		private final int value;


		public TestEntry(final int value) {
			super();
			this.value = value;
		}


		public final int value() {
			return this.value;
		}

		@Override
		protected final void dequeued(final boolean onClear, final ConcurrentLinkedQueue<?> queue,
				final char traceCode, final Object attachment) {
			if (onClear) {
				ConcurrentQueueTestAction.this.clearCount++;
			}
		}

	}


	private int clearCount;


	public ConcurrentQueueTestAction() {
		super();
	}


	@Override
	protected final void setUp(final String methodName) throws Exception {
		this.clearCount = 0;
	}

	/*
	 * Test methods
	 */

	public final void testAdd() {
		final IterableLinkedQueue<TestEntry> queue = new IterableLinkedQueue<TestEntry>();

		assertNull(queue.peek());

		int i = 0;
		for (; i < 5; ++i) {
			queue.add(new TestEntry(i));
		}

		assertNotNull(queue.peek());


		i = 0;
		Iterator<TestEntry> iter = queue.iterator();
		TestEntry entry = iter.next();
		while (entry != null) {
			assertEquals((int)i, entry.value());
			assertTrue(entry.isQueued());

			++i;

			entry = iter.next();
		}
		assertEquals(5, i);


		i = 0;
		entry = queue.poll();
		while (entry != null) {
			assertEquals((int)i, entry.value());
			assertFalse(entry.isQueued());

			++i;

			entry = queue.poll();
		}
		assertEquals(5, i);

		assertNull(queue.peek());
	}

	public final void testDequeue() {
		final IterableLinkedQueue<TestEntry> queue = new IterableLinkedQueue<TestEntry>();

		Iterator<TestEntry> iter;
		TestEntry entry;

		for (int j = 0; j < 5; j++) {
			assertNull(queue.peek());

			int i = 0;
			for (; i < 5; ++i) {
				queue.add(new TestEntry(i));
			}

			assertNotNull(queue.peek());


			i = 0;

			iter = queue.iterator();
			while (iter.hasNext()) {
				entry = iter.next();

				if (entry != null) {
					assertTrue(entry.isQueued());
					if (entry.value() >= j) {
						++i;
						entry.dequeue();
						assertFalse(entry.isQueued());
						break;
					}
				}
			}

			while (queue.peek() != null) {
				iter = queue.iterator();

				while (iter.hasNext()) {
					entry = iter.next();

					if (entry != null) {
						assertTrue(entry.isQueued());
						++i;
						entry.dequeue();
						assertFalse(entry.isQueued());
					}
				}
			}

			assertEquals(5, i);
		}

	}

	public final void testRemove() {
		final IterableLinkedQueue<TestEntry> queue = new IterableLinkedQueue<TestEntry>();

		Iterator<TestEntry> iter;
		TestEntry entry;

		for (int j = 0; j < 5; j++) {
			assertNull(queue.peek());

			int i = 0;
			for (; i < 5; ++i) {
				queue.add(new TestEntry(i));
			}

			assertNotNull(queue.peek());


			i = 0;

			iter = queue.iterator();
			while (iter.hasNext()) {
				entry = iter.next();

				if (entry != null) {
					assertTrue(entry.isQueued());
					if (entry.value() >= j) {
						++i;
						iter.remove();
						assertFalse(entry.isQueued());
						break;
					}
				}
			}

			while (queue.peek() != null) {
				iter = queue.iterator();

				while (iter.hasNext()) {
					entry = iter.next();

					if (entry != null) {
						assertTrue(entry.isQueued());
						++i;
						iter.remove();
						assertFalse(entry.isQueued());
					}
				}
			}

			assertEquals(5, i);
		}

	}

	public final void testClear() {
		final IterableLinkedQueue<TestEntry> queue = new IterableLinkedQueue<TestEntry>();

		assertNull(queue.peek());

		int i = 0;
		for (; i < 5; ++i) {
			queue.add(new TestEntry(i));
		}

		assertNotNull(queue.peek());

		this.clearCount = 0;
		queue.clear();

		assertNull(queue.peek());
		assertEquals((int)5, this.clearCount);
	}


}
