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

import java.util.concurrent.atomic.AtomicReference;

/**
 * @author <a href="mailto:yuzhaoping1970@gmail.com">Zhaoping Yu</a>
 *
 */
public final class SchemePool {

	private final AtomicReference<Entry> entries;

	private final ConnectionPool pool;


	SchemePool(final ConnectionPool pool) {
		this.entries = new AtomicReference<Entry>();

		this.pool = pool;
	}


	public final PoolEntryGroup getPoolEntryGroup(final String schemeName) {
		Entry newEntry = null;
		for (;;) {
			final Entry first = this.entries.get();

			Entry entry = first;
			while (entry != null) {
				if (entry.schemeName.equalsIgnoreCase(schemeName)) {
					return entry.group;
				}

				entry = entry.next;
			}

			if (newEntry == null) {
				newEntry = new Entry(schemeName, this.pool);
			}
			newEntry.next = first; // CAS piggyback
			if (!this.entries.compareAndSet(first, newEntry)) {
				continue;
			}

			return newEntry.group;
		}
	}

	public final void clear() {
		Entry entry = popEntry();

		while (entry != null) {
			entry.group.clear();

			entry = popEntry();
		}
	}

	private final Entry popEntry() {
		for (;;) {
			final Entry first = this.entries.get();

			if (first != null) {
				final Entry next = first.next;

				if (!this.entries.compareAndSet(first, next)) {
					continue;
				}

				first.next = null;
			}

			return first;
		}
	}


	private static final class Entry {

		private Entry next;

		private final String schemeName;

		private final PoolEntryGroup group;


		Entry(final String schemeName, final ConnectionPool pool) {
			this.schemeName = schemeName;
			this.group = new PoolEntryGroup(pool);
		}

	}

}
