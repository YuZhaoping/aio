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

import com.chinmobi.aio.AIOFutureCallback;
import com.chinmobi.aio.AIOFutureCancellable;
import com.chinmobi.aio.act.AIOActResult;
import com.chinmobi.aio.impl.nio.BasicFuture;
import com.chinmobi.aio.impl.nio.ExceptionHandler;
import com.chinmobi.aio.impl.nio.FutureDoAccomplishCallback;
import com.chinmobi.aio.impl.nio.FutureReleaseCallback;

/**
 * @author <a href="mailto:yuzhaoping1970@gmail.com">Zhaoping Yu</a>
 *
 */
public final class ActFuture<T extends AIOActResult> extends BasicFuture<T> {

	ActFuture(final ExceptionHandler exceptionHandler) {
		super(exceptionHandler);
	}


	@Override
	protected final void set(final FutureReleaseCallback releaseCallback,
			final AIOFutureCancellable cancellable,
			final AIOFutureCallback<T> callback, final Object attachment, final T result) {
		super.set(releaseCallback, cancellable, callback, attachment, result);
	}

	@Override
	protected final void internalRelease() {
		super.internalRelease();
	}

	@Override
	protected final boolean initiate() {
		return super.initiate();
	}

	@Override
	protected final boolean cancelled() {
		return super.cancelled();
	}

	@Override
	protected final boolean accomplished(final T result, final FutureDoAccomplishCallback<T> doCallback) {
		return super.accomplished(result, doCallback);
	}

	@Override
	protected final boolean timeout() {
		return super.timeout();
	}

	@Override
	protected final boolean failed(final Throwable cause) {
		return super.failed(cause);
	}

}
