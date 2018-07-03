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

import java.util.concurrent.TimeUnit;

import com.chinmobi.aio.act.AIOReadableActEntry;
import com.chinmobi.aio.act.AIOWritableActEntry;
import com.chinmobi.aio.act.entry.AIOBufferEntryBase;
import com.chinmobi.aio.pool.AIOConnectionPool;
import com.chinmobi.aio.service.AIOConnection;
import com.chinmobi.aio.service.AIOConnectionAcceptor;
import com.chinmobi.aio.service.AIOService;

/**
 * @author <a href="mailto:yuzhaoping1970@gmail.com">Zhaoping Yu</a>
 *
 */
public final class ServiceActTestAction extends ServiceTestBase {

	private static final class Entry extends AIOBufferEntryBase {

		private long count;


		public Entry(final int size) {
			super(size);
			this.count = 0;
		}


		final void setCount(final long count) {
			synchronized (this) {
				this.count = count;
			}
		}

		@Override
		protected final void onCompleted(final boolean asInput, final long position, final long count) {
			synchronized (this) {
				if (this.count >= count) {
					this.count -= count;
					if (this.count == 0) {
						notify();
					}
				}
			}
		}

		final long waitForDone() throws InterruptedException {
			synchronized (this) {
				while (this.count > 0) {
					wait();
				}

				return this.count;
			}
		}

		/*final long waitForDone(final long timeout) throws InterruptedException {

			final long startTime = (timeout <= 0) ? 0 : System.currentTimeMillis();
			long waitTime = timeout;

			synchronized (this) {
				while (this.count > 0) {
					if (waitTime <= 0) {
						break;
					}

					wait(waitTime);
					waitTime = timeout - (System.currentTimeMillis() - startTime);
				}

				return this.count;
			}
		}*/

	}


	private static class CallbackBase {

		protected volatile long count;
		protected volatile int status;


		final void setCount(final long count) {
			this.count = count;
			this.status = 0;
		}

		protected final void finished(final int status, final long completedCount) {
			synchronized (this) {
				this.status = status;

				if (this.count >= completedCount) {
					this.count -= completedCount;
					if (this.count == 0) {
						notify();
					}
				}
			}
		}

		final long waitForDone() throws InterruptedException {
			synchronized (this) {
				while (this.count > 0 && this.status == 0) {
					wait();
				}

				return this.count;
			}
		}

		/*final long waitForDone(final long timeout) throws InterruptedException {

			final long startTime = (timeout <= 0) ? 0 : System.currentTimeMillis();
			long waitTime = timeout;

			synchronized (this) {
				while (this.count > 0 && this.status == 0) {
					if (waitTime <= 0) {
						break;
					}

					wait(waitTime);
					waitTime = timeout - (System.currentTimeMillis() - startTime);
				}

				return this.count;
			}
		}*/

	}

	private static final class ReadCallback extends CallbackBase implements AIOService.ReadCallback {


		public final void aioReadInitiate(final AIOService service,
				final AIOWritableActEntry target) {
		}

		public final void aioReadAccomplished(final AIOService service,
				final AIOWritableActEntry target, final long position, final long completedCount, final boolean endOfInput) {
			finished(0, completedCount);
		}

		public final void aioReadTimeout(final AIOService service,
				final AIOWritableActEntry target, final long position, final long completedCount) {
			finished(1, completedCount);
		}

		public final void aioReadFailed(final AIOService service,
				final AIOWritableActEntry target, final long position, final long completedCount, final Throwable cause) {
			finished(-1, completedCount);
		}

		public final void aioReadCancelled(final AIOService service,
				final AIOWritableActEntry target, final long position, final long completedCount) {
			finished(-2, completedCount);
		}

	}

	private static final class WriteCallback extends CallbackBase implements AIOService.WriteCallback {

		public final void aioWriteInitiate(final AIOService service,
				final AIOReadableActEntry target) {
		}

		public final void aioWriteAccomplished(final AIOService service,
				final AIOReadableActEntry target, final long position, final long completedCount) {
			finished(0, completedCount);
		}

		public final void aioWriteTimeout(final AIOService service,
				final AIOReadableActEntry target, final long position, final long completedCount) {
			finished(1, completedCount);
		}

		public final void aioWriteFailed(final AIOService service,
				final AIOReadableActEntry target, final long position, final long completedCount, final Throwable cause) {
			finished(-1, completedCount);
		}

		public final void aioWriteCancelled(final AIOService service,
				final AIOReadableActEntry target, final long position, final long completedCount) {
			finished(-2, completedCount);
		}

	}


	private AIOConnectionAcceptor acceptor;

	private int serviceCallbackID;


	public ServiceActTestAction() {
		super();
		this.serviceCallbackID = 0;
	}


	@Override
	protected final void setUp(final String methodName) throws Exception {
		this.acceptor = this.aioRuntime.getConnectionAcceptor("TCP", 8080);

		this.acceptor.start(new ServiceCallback("Server_" + this.serviceCallbackID));
		this.acceptor.waitForAccepting();
	}

	@Override
	protected final void tearDown() throws Exception {
		++this.serviceCallbackID;
		this.acceptor.stop();
		this.acceptor.waitForStopped();
	}

	/*
	 * Test methods
	 */

	public final void test0ReadAndWrite() {
		final ServiceCallback serverCallback = (ServiceCallback)this.acceptor.getServiceCallbackFactory();
		final ServiceCallback clientCallback = new ServiceCallback("Client_" + this.serviceCallbackID);
		try {
			final Entry outputEntry = new Entry(128);
			outputEntry.setCount(100);
			serverCallback.setOutputActEntry(outputEntry.toOutput(100));

			final AIOConnectionPool connPool = this.aioRuntime.getConnectionPool();

			final AIOConnection connection = connPool.leaseConnection("TCP", "localhost", 8080);

			connection.setServiceCallback(clientCallback);

			connection.connect();
			connection.waitForConnected();

			final Entry inputEntry = new Entry(128);
			inputEntry.toOutput().byteBuffer().position(100);
			inputEntry.setCount(100);

			connection.write(inputEntry.toInput(), null, 0, TimeUnit.MILLISECONDS);

			inputEntry.waitForDone();
			outputEntry.waitForDone();

			connection.read(inputEntry.clear().toOutput(100), null, null, 0, TimeUnit.MILLISECONDS);
			serverCallback.service().write(outputEntry.toInput(), null, 0, TimeUnit.MILLISECONDS);

			outputEntry.waitForDone();
			inputEntry.waitForDone();

			connection.close();

		} catch (Exception ex) {
			this.out().println("serverCallback.status: " + serverCallback.getStatus());
			if (serverCallback.getCause() != null) {
				serverCallback.getCause().printStackTrace(this.out());
			}

			AIOService service = serverCallback.service();
			if (service != null) {
				this.out().println(service.toString());
			}
			service = clientCallback.service();
			if (service != null) {
				this.out().println(service.toString());
			}
			fail(ex);
		}
	}

	public final void test1ReadAndWrite() {
		final ServiceCallback serverCallback = (ServiceCallback)this.acceptor.getServiceCallbackFactory();
		final ServiceCallback clientCallback = new ServiceCallback("Client_" + this.serviceCallbackID);
		try {
			final Entry outputEntry = new Entry(128);
			outputEntry.setCount(100);
			serverCallback.setOutputActEntry(outputEntry.toOutput(100));

			final AIOConnectionPool connPool = this.aioRuntime.getConnectionPool();

			final AIOConnection connection = connPool.leaseConnection("TCP", "localhost", 8080);

			connection.setServiceCallback(clientCallback);

			connection.connect();
			connection.waitForConnected();

			final Entry inputEntry = new Entry(128);
			inputEntry.toOutput().byteBuffer().position(100);
			inputEntry.setCount(100);

			final WriteCallback writeCallback = new WriteCallback();
			writeCallback.setCount(100);
			connection.write(inputEntry.toInput(), writeCallback, 0, TimeUnit.MILLISECONDS);

			writeCallback.waitForDone();
			outputEntry.waitForDone();

			final ReadCallback readCallback = new ReadCallback();
			readCallback.setCount(100);
			connection.read(inputEntry.clear().toOutput(100), readCallback, null, 0, TimeUnit.MILLISECONDS);
			serverCallback.service().write(outputEntry.toInput(), null, 0, TimeUnit.MILLISECONDS);

			outputEntry.waitForDone();
			readCallback.waitForDone();

			connection.close();

		} catch (Exception ex) {
			this.out().println("serverCallback.status: " + serverCallback.getStatus());
			if (serverCallback.getCause() != null) {
				serverCallback.getCause().printStackTrace(this.out());
			}

			AIOService service = serverCallback.service();
			if (service != null) {
				this.out().println(service.toString());
			}
			service = clientCallback.service();
			if (service != null) {
				this.out().println(service.toString());
			}
			fail(ex);
		}
	}

	public final void test2ReadAndWrite() {
		final ServiceCallback serverCallback = (ServiceCallback)this.acceptor.getServiceCallbackFactory();
		final ServiceCallback clientCallback = new ServiceCallback("Client_" + this.serviceCallbackID);
		try {
			final Entry outputEntry = new Entry(128);
			outputEntry.setCount(100);
			serverCallback.setOutputActEntry(outputEntry.toOutput(100));

			final AIOConnectionPool connPool = this.aioRuntime.getConnectionPool();

			final AIOConnection connection = connPool.leaseConnection("TCP", "localhost", 8080);

			connection.setServiceCallback(clientCallback);

			connection.enableRetry(true);

			final Entry inputEntry = new Entry(128);
			inputEntry.toOutput().byteBuffer().position(100);
			inputEntry.setCount(100);

			final WriteCallback writeCallback = new WriteCallback();
			writeCallback.setCount(100);
			connection.write(inputEntry.toInput(), writeCallback, 0, TimeUnit.MILLISECONDS);

			writeCallback.waitForDone();
			outputEntry.waitForDone();

			connection.close();
			serverCallback.waitForClosed();
			serverCallback.reset();

			final ReadCallback readCallback = new ReadCallback();
			readCallback.setCount(100);
			connection.read(inputEntry.clear().toOutput(100), readCallback, null, 0, TimeUnit.MILLISECONDS);

			serverCallback.waitForOpened();
			serverCallback.service().write(outputEntry.toInput(), null, 0, TimeUnit.MILLISECONDS);

			outputEntry.waitForDone();
			readCallback.waitForDone();

			connection.close();

		} catch (Exception ex) {
			this.out().println("serverCallback.status: " + serverCallback.getStatus());
			if (serverCallback.getCause() != null) {
				serverCallback.getCause().printStackTrace(this.out());
			}

			AIOService service = serverCallback.service();
			if (service != null) {
				this.out().println(service.toString());
			}
			service = clientCallback.service();
			if (service != null) {
				this.out().println(service.toString());
			}
			fail(ex);
		}
	}

}
