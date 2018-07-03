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

import com.chinmobi.aio.act.AIOInputActResult;
import com.chinmobi.aio.act.AIOOutputActResult;
import com.chinmobi.aio.impl.nio.SessionContext;
import com.chinmobi.aio.impl.util.ConcurrentLinkedQueue;
import com.chinmobi.aio.impl.util.IllegalQueueNodeStateException;


/**
 * @author <a href="mailto:yuzhaoping1970@gmail.com">Zhaoping Yu</a>
 *
 */
public final class ActRequestFactory {

	private static final int MAX_CACHED_REQUEST = 8;

	private final SessionContext sessionContext;

	private final ConcurrentLinkedQueue<BaseActRequest<AIOInputActResult>> freeInputRequests;
	private final AtomicInteger freeInputRequestsCount;

	private final ConcurrentLinkedQueue<BaseActRequest<AIOOutputActResult>> freeOutputRequests;
	private final AtomicInteger freeOutputRequestsCount;

	private final LegacyChannelFactory legacyChannelFactory;


	public ActRequestFactory(final SessionContext sessionContext) {
		this.sessionContext = sessionContext;

		this.freeInputRequests = new ConcurrentLinkedQueue<BaseActRequest<AIOInputActResult>>();
		this.freeInputRequestsCount = new AtomicInteger(0);

		this.freeOutputRequests = new ConcurrentLinkedQueue<BaseActRequest<AIOOutputActResult>>();
		this.freeOutputRequestsCount = new AtomicInteger(0);

		this.legacyChannelFactory = new LegacyChannelFactory();
	}


	final SessionContext sessionContext() {
		return this.sessionContext;
	}

	final LegacyChannelFactory legacyChannelFactory() {
		return this.legacyChannelFactory;
	}

	// For test
	public final boolean containsFree(final InputActRequest request) {
		return (request.belongsQueue() == this.freeInputRequests);
	}

	// For test
	public final boolean containsFree(final OutputActRequest request) {
		return (request.belongsQueue() == this.freeOutputRequests);
	}

	final InputActRequest createInputActRequest() {
		InputActRequest request = (InputActRequest)this.freeInputRequests.poll();
		if (request != null) {
			this.freeInputRequestsCount.decrementAndGet();
		} else {
			request = new InputActRequest(this);
		}

		return request;
	}

	final OutputActRequest createOutputActRequest() {
		OutputActRequest request = (OutputActRequest)this.freeOutputRequests.poll();
		if (request != null) {
			this.freeOutputRequestsCount.decrementAndGet();
		} else {
			request = new OutputActRequest(this);
		}

		return request;
	}

	final void releaseActRequest(final InputActRequest request)
			throws IllegalQueueNodeStateException {
		if (this.freeInputRequestsCount.incrementAndGet() < MAX_CACHED_REQUEST) {
			this.freeInputRequests.add(request);
		}
	}

	final void releaseActRequest(final OutputActRequest request)
			throws IllegalQueueNodeStateException {
		if (this.freeOutputRequestsCount.incrementAndGet() < MAX_CACHED_REQUEST) {
			this.freeOutputRequests.add(request);
		}
	}

}
