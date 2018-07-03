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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

import com.chinmobi.aio.act.entry.AIOFileEntryBase;
import com.chinmobi.aio.act.entry.AIOFileRegion;
import com.chinmobi.aio.pool.AIOConnectionPool;
import com.chinmobi.aio.service.AIOConnection;
import com.chinmobi.aio.service.AIOConnectionAcceptor;
import com.chinmobi.aio.service.AIOService;

/**
 * @author <a href="mailto:yuzhaoping1970@gmail.com">Zhaoping Yu</a>
 *
 */
public final class FileEntryTestAction extends ServiceTestBase {

	private static final class FileEntry extends AIOFileEntryBase {

		private long count;


		FileEntry(final String fileName) throws FileNotFoundException, IOException {
			super(fileName);
			this.count = 0;
		}


		final void setCount(final long count) {
			synchronized (this) {
				this.count = count;
			}
		}

		@Override
		protected final void onCompleted(final AIOFileRegion region, final boolean asInput, final long position, final long count) {
			synchronized (this) {
				if (this.count >= count) {
					this.count -= count;
					if (this.count == 0) {
						notify();
					}
				}
			}
		}

		final void waitForDone() throws InterruptedException {
			synchronized (this) {
				while (this.count > 0) {
					wait();
				}
			}
		}

	}


	private AIOConnectionAcceptor acceptor;


	public FileEntryTestAction() {
		super();
	}


	@Override
	protected final void setUp(final String methodName) throws Exception {
		this.acceptor = this.aioRuntime.getConnectionAcceptor("TCP", 8080);

		this.acceptor.start(new ServiceCallback("Server"));
		this.acceptor.waitForAccepting();
	}

	@Override
	protected final void tearDown() throws Exception {
		this.acceptor.stop();
		this.acceptor.waitForStopped();
	}

	/*
	 * Test methods
	 */

	public final void test0Construct() {
		try {
			final String fileName = "../tmp/filentrytest0.txt";

			final FileEntry entry = new FileEntry(fileName);

			entry.truncate(0);

			assertEquals(0, entry.toInput().position());
			assertEquals(0, entry.toInput().count());

			assertEquals(0, entry.toOutput().position());
			assertEquals(-1, entry.toOutput().count());

			entry.truncate(100);

			assertEquals(100, entry.toOutput().position());
			assertEquals(-1, entry.toOutput().count());

			assertEquals(0, entry.toInput().position());
			assertEquals(100, entry.toInput().count());

			entry.close();

		} catch (Exception ex) {
			fail(ex);
		}
	}

	public final void test1CreateRegion() {
		try {
			final String fileName = "../tmp/filentrytest0.txt";

			final FileEntry entry = new FileEntry(fileName);

			entry.truncate(0);

			final AIOFileRegion region1 = entry.createRegion(0, 50);

			assertEquals(0, region1.toInput().position());
			assertEquals(50, region1.toInput().count());

			assertEquals(0, region1.toOutput().position());
			assertEquals(50, region1.toOutput().count());

			assertEquals(0, entry.toInput().position());
			assertEquals(50, entry.toInput().count());

			assertEquals(50, entry.toOutput().position());
			assertEquals(-1, entry.toOutput().count());

			final AIOFileRegion region2 = entry.createRegion(50, 100);

			assertEquals(50, region2.toInput().position());
			assertEquals(50, region2.toInput().count());

			assertEquals(50, region2.toOutput().position());
			assertEquals(50, region2.toOutput().count());

			assertEquals(0, entry.toInput().position());
			assertEquals(100, entry.toInput().count());

			assertEquals(100, entry.toOutput().position());
			assertEquals(-1, entry.toOutput().count());

			entry.close();

		} catch (Exception ex) {
			fail(ex);
		}
	}

	public final void test2InputAndOutput() {
		AIOService clientService = null;
		AIOService serverService = null;
		try {
			final String fileName0 = "../tmp/filentrytest0.txt";
			final FileEntry entry0 = new FileEntry(fileName0);
			entry0.truncate(0);
			entry0.setCount(100);
			final AIOFileRegion region01 = entry0.createRegion(0, 50);
			final AIOFileRegion region02 = entry0.createRegion(50, 100);

			final String fileName1 = "../tmp/filentrytest1.txt";
			final FileEntry entry1 = new FileEntry(fileName1);


			final ServiceCallback serviceCallback = (ServiceCallback)this.acceptor.getServiceCallbackFactory();
			entry1.truncate(0);
			entry1.setCount(100);
			serviceCallback.setOutputActEntry(entry1.toOutput(100));


			final AIOConnectionPool connPool = this.aioRuntime.getConnectionPool();
			final AIOConnection connection = connPool.leaseConnection("TCP", "localhost", 8080);

			final ServiceCallback clientCallback = new ServiceCallback("Client");
			connection.setServiceCallback(clientCallback);

			connection.connect();
			connection.waitForConnected();
			clientService = connection;

			connection.write(region01.toInput(), null, 0, TimeUnit.MILLISECONDS);
			connection.write(region02.toInput(), null, 0, TimeUnit.MILLISECONDS);

			serverService = serviceCallback.service();

			entry0.waitForDone();
			entry1.waitForDone();

			assertEquals(50, region01.toInput().position());
			assertEquals(0, region01.toInput().count());

			assertEquals(100, region02.toInput().position());
			assertEquals(0, region02.toInput().count());

			assertEquals(100, entry1.toOutput().position());
			assertEquals(0, entry1.toOutput().count());

			entry0.close();
			entry1.close();

		} catch (Exception ex) {
			if (clientService != null) {
				this.out().println(clientService.toString());
			}
			if (serverService != null) {
				this.out().println(serverService.toString());
			}

			fail(ex);
		}
	}

	public final void test3InputAndOutput() {
		AIOService clientService = null;
		AIOService serverService = null;
		try {
			final String fileName0 = "../tmp/filentrytest0.txt";
			final FileEntry entry0 = new FileEntry(fileName0);
			entry0.truncate(0);
			entry0.setCount(100);
			final AIOFileRegion region01 = entry0.createRegion(0, 50);
			final AIOFileRegion region02 = entry0.createRegion(50, 100);

			final String fileName1 = "../tmp/filentrytest1.txt";
			final FileEntry entry1 = new FileEntry(fileName1);


			final ServiceCallback serviceCallback = (ServiceCallback)this.acceptor.getServiceCallbackFactory();
			entry1.truncate(100);
			entry1.setCount(100);


			final AIOConnectionPool connPool = this.aioRuntime.getConnectionPool();
			final AIOConnection connection = connPool.leaseConnection("TCP", "localhost", 8080);

			final ServiceCallback clientCallback = new ServiceCallback("Client");
			connection.setServiceCallback(clientCallback);

			connection.connect();
			connection.waitForConnected();
			clientService = connection;

			connection.read(region01.toOutput(), null, null, 0, TimeUnit.MILLISECONDS);
			connection.read(region02.toOutput(), null, null, 0, TimeUnit.MILLISECONDS);

			serviceCallback.waitForOpened();
			serverService = serviceCallback.service();
			serviceCallback.service().write(entry1.toInput(), null, 0, TimeUnit.MILLISECONDS);

			entry0.waitForDone();
			entry1.waitForDone();

			assertEquals(50, region01.toOutput().position());
			assertEquals(0, region01.toOutput().count());

			assertEquals(100, region02.toOutput().position());
			assertEquals(0, region02.toOutput().count());

			assertEquals(100, entry1.toInput().position());
			assertEquals(0, entry1.toInput().count());

			entry0.close();
			entry1.close();

		} catch (Exception ex) {
			if (clientService != null) {
				this.out().println(clientService.toString());
			}
			if (serverService != null) {
				this.out().println(serverService.toString());
			}

			fail(ex);
		}
	}

}
