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
package com.chinmobi.aio.impl.act;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author <a href="mailto:yuzhaoping1970@gmail.com">Zhaoping Yu</a>
 *
 */
public final class LegacyChannelFactory {

	private static final int MAX_CACHED_LEGACY = 8;

	private final AtomicReference<LegacyChannel> freeLegacyChannels;
	private final AtomicInteger freeLegacyChannelsCount;


	LegacyChannelFactory() {
		this.freeLegacyChannels = new AtomicReference<LegacyChannel>();
		this.freeLegacyChannelsCount = new AtomicInteger(0);
	}


	final LegacyChannel createLegacyChannel() {
		for (;;) {
			final LegacyChannel top = this.freeLegacyChannels.get();

			if (top != null) {
				final LegacyChannel next = top.next;

				if (!this.freeLegacyChannels.compareAndSet(top, next)) {
					continue;
				}

				top.next = null;

				this.freeLegacyChannelsCount.decrementAndGet();
			}

			if (top != null) {
				return top;
			} else {
				return new LegacyChannel(this);
			}
		}
	}

	final void releaseLegacyChannel(final LegacyChannel legacyChannel) {
		if (this.freeLegacyChannelsCount.incrementAndGet() < MAX_CACHED_LEGACY) {

			for (;;) {
				final LegacyChannel top = this.freeLegacyChannels.get();

				legacyChannel.next = top; // CAS piggyback
				if (!this.freeLegacyChannels.compareAndSet(top, legacyChannel)) {
					continue;
				}

				break;
			}
		}
	}

}
