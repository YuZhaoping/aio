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
package com.chinmobi.aiotest.level2;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import com.chinmobi.aio.AIOClosedSessionException;
import com.chinmobi.aio.act.AIOWritableActEntry;
import com.chinmobi.aio.facade.AIORuntime;
import com.chinmobi.aio.facade.AIOSchemeProvider;
import com.chinmobi.aio.scheme.AIOSocketScheme;
import com.chinmobi.aio.scheme.AIOTransportScheme;
import com.chinmobi.aio.service.AIOService;
import com.chinmobi.aio.service.AIOServiceCallback;
import com.chinmobi.app.action.ActionContext;
import com.chinmobi.testapp.BaseTestAction;

/**
 * @author <a href="mailto:yuzhaoping1970@gmail.com">Zhaoping Yu</a>
 *
 */
public class ServiceTestBase extends BaseTestAction {

	protected static final class SchemeProvider implements AIOSchemeProvider {

		private AIOSocketScheme socketScheme;


		public AIOTransportScheme getTransportScheme(final String schemeName) {
			synchronized (this) {
				if ("TCP".equalsIgnoreCase(schemeName)) {
					if (this.socketScheme == null) {
						this.socketScheme = new AIOSocketScheme();
					}
					return this.socketScheme;
				}
			}
			return null;
		}

	}

	protected static final class ServiceCallback implements AIOServiceCallback, AIOServiceCallback.Factory {

		private final String name;

		private volatile AIOService service;

		private volatile int status;
		private volatile Throwable cause;

		private volatile AIOWritableActEntry outputActEntry;


		ServiceCallback(final String name) {
			this.name = name;
			reset();
		}


		final void reset() {
			this.status = 0;
			this.cause = null;
			this.outputActEntry = null;
		}

		final void setOutputActEntry(final AIOWritableActEntry outputActEntry) {
			this.outputActEntry = outputActEntry;
		}

		final AIOService service() {
			return this.service;
		}

		public final void aioServiceOpened(final AIOService service) throws AIOClosedSessionException {
			synchronized (this) {
				this.service = service;
				this.status = 1;

				if (this.outputActEntry != null) {
					this.status = 2;
					service.read(this.outputActEntry, null, null, 0, TimeUnit.MILLISECONDS);
					this.outputActEntry = null;
				}

				notify();
			}
		}

		public final boolean aioServiceInputReady(final AIOService service) throws IOException, AIOClosedSessionException {
			return false;
		}

		public final boolean aioServiceOutputReady(final AIOService service) throws IOException, AIOClosedSessionException {
			return false;
		}

		public final void aioServiceTimeout(final AIOService service) throws AIOClosedSessionException {
			service.close();
		}

		public final void aioServiceClosed(final AIOService service, final Object msgObj, final Throwable cause) {
			synchronized (this) {
				this.status = -1;
				this.cause = cause;
				notify();
			}
		}

		public final AIOServiceCallback createAIOServiceCallback(final AIOService service) {
			return this;
		}

		final int getStatus() {
			return this.status;
		}

		final Throwable getCause() {
			return this.cause;
		}

		final void waitForOpened() throws InterruptedException {
			synchronized (this) {
				while (this.status == 0) {
					wait();
				}
			}
		}

		final void waitForClosed() throws InterruptedException {
			synchronized (this) {
				while (this.status > 0) {
					wait();
				}
			}
		}

		final void close() {
			final AIOService service = this.service;
			if (service != null) {
				this.service = null;
				service.close();
			}
		}

		@Override
		public final String toString() {
			return this.name;
		}

	}


	protected final SchemeProvider schemeProvider;

	protected AIORuntime aioRuntime;


	protected ServiceTestBase() {
		super();
		this.schemeProvider = new SchemeProvider();
	}


	@Override
	public void init(final ActionContext context) {
		super.init(context);

		if (this.aioRuntime == null) {
			this.aioRuntime = new AIORuntime(this.schemeProvider);
			this.aioRuntime.configuration().setReactorGroupSize(3);
		}
	}

	@Override
	public void destroy() {
		if (this.aioRuntime != null) {
			this.aioRuntime.shutdown();
		}
	}

}
