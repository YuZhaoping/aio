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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.Pipe;
import java.nio.channels.Selector;

import com.chinmobi.aio.AIOClosedSessionException;
import com.chinmobi.aio.AIOFuture;
import com.chinmobi.aio.AIOFutureCallback;
import com.chinmobi.aio.AIONotActiveException;
import com.chinmobi.aio.AIOServiceHandler;
import com.chinmobi.aio.AIOSession;
import com.chinmobi.aio.act.AIOActEntry;
import com.chinmobi.aio.act.AIOActResult;
import com.chinmobi.aio.act.AIOReadableActEntry;
import com.chinmobi.aio.act.AIOWritableActEntry;
import com.chinmobi.aio.impl.act.BaseActRequest;
import com.chinmobi.aio.impl.act.BaseActor;
import com.chinmobi.aio.impl.nio.Demultiplexer;
import com.chinmobi.aio.impl.nio.Session;
import com.chinmobi.aio.impl.nio.SessionContext;
import com.chinmobi.aio.impl.nio.SessionCreator;
import com.chinmobi.aio.impl.util.TimerEntry;
import com.chinmobi.aio.impl.util.TimerEntrySet;
import com.chinmobi.app.action.ActionContext;
import com.chinmobi.logging.LogManager;
import com.chinmobi.testapp.BaseTestAction;

/**
 * @author <a href="mailto:yuzhaoping1970@gmail.com">Zhaoping Yu</a>
 *
 */
public abstract class AbstractActTestAction <T extends AIOActResult> extends BaseTestAction
	implements TimerEntrySet.Observer, Demultiplexer.ActiveChecker, AIOServiceHandler.Factory {

	private static final String TEST_SELECTOR = "test.selector";

	protected static final int BUF_INIT_CAP = 4;

	protected static final class BufferActEntry
		implements AIOReadableActEntry, AIOWritableActEntry {

		private ByteBuffer buffer;


		BufferActEntry(final int capacity) {
			this.buffer = ByteBuffer.allocate(capacity);
		}

		BufferActEntry() {
			this(BUF_INIT_CAP);
		}


		public final ByteBuffer byteBuffer() {
			return this.buffer;
		}

		public final FileChannel fileChannel() {
			return null;
		}

		public final long position() {
			return this.buffer.position();
		}

		public final long count() {
			return this.buffer.remaining();
		}

		public final void completed(final long position, final long count) {
			// Nothing to do.
		}

		public final void expand(int length) {
			final int capacity = this.buffer.capacity() + length;
			final ByteBuffer newBuf = ByteBuffer.allocate(capacity);

			this.buffer.flip();
			newBuf.put(this.buffer);

			this.buffer = newBuf;
		}

	}


	private static final class ServiceHandler implements AIOServiceHandler {

		public final void handleAIOSessionOpened(final AIOSession session) throws AIOClosedSessionException {
		}

		public final boolean handleAIOSessionInputReady(final AIOSession session)
				throws IOException, AIOClosedSessionException {
			return false;
		}

		public final boolean handleAIOSessionOutputReady(final AIOSession session)
				throws IOException, AIOClosedSessionException {
			return false;
		}

		public final void handleAIOSessionTimeout(final AIOSession session) throws AIOClosedSessionException {
		}

		public final void handleAIOSessionClosed(final AIOSession session, final Throwable cause) {
		}

	}


	protected static final class Helper<T extends AIOActResult> implements AIOFutureCallback<T> {

		protected int testCase;

		protected int initiateCount;
		protected int accomplishedCount;
		protected int timeoutCount;
		protected int failedCount;
		protected int cancelledCount;

		protected Throwable cause;


		public void reset() {
			this.testCase = 0;

			this.initiateCount = 0;
			this.accomplishedCount = 0;
			this.timeoutCount = 0;
			this.failedCount = 0;
			this.cancelledCount = 0;

			this.cause = null;
		}


		public final void initiate(final AIOFuture<T> future,
				final Object attachment, final T result) {
			this.initiateCount++;
			if (this.testCase < 0) {
				throw new RuntimeException("test");
			}
		}

		public final void accomplished(final AIOFuture<T> future,
				final Object attachment, final T result) {
			this.accomplishedCount++;
			future.release();
		}

		public final void timeout(final AIOFuture<T> future,
				final Object attachment, final T result) {
			this.timeoutCount++;
			future.release();
		}

		public final void failed(final AIOFuture<T> future,
				final Object attachment, final T result, final Throwable cause) {
			this.failedCount++;
			this.cause = cause;
			future.release();
		}

		public final void cancelled(final AIOFuture<T> future,
				final Object attachment, final T result) {
			this.cancelledCount++;
			future.release();
		}

	}


	protected final Helper<T> helper;

	protected TimerEntrySet timerEntries;
	protected Demultiplexer demultiplexer;
	protected SessionContext sessionContext;

	protected Session session;

	protected Pipe pipe;


	protected AbstractActTestAction() {
		super();
		this.helper = new Helper<T>();
		this.sessionContext = null;
	}


	@Override
	public final void init(final ActionContext context) {
		super.init(context);

		getSelector();
	}

	@Override
	public final void destroy() {
		final Selector selector = (Selector)context().removeAttribute(TEST_SELECTOR);
		if (selector != null) {
			try {
				selector.close();
			} catch (IOException ignore) {
			}
		}

		super.destroy();
	}

	private final Selector getSelector() {
		Selector selector = (Selector)context.getAttribute(TEST_SELECTOR);
		if (selector == null || !selector.isOpen()) {

			try {
				selector = Selector.open();
			} catch (IOException ex) {
				throw new RuntimeException(ex);
			}

			context.setAttribute(TEST_SELECTOR, selector);
		}
		return selector;
	}

	@Override
	protected final void setUp(final String methodName) throws Exception {
		if (this.sessionContext == null) {
			final Selector selector = getSelector();

			this.timerEntries = new TimerEntrySet(this);
			this.demultiplexer = new Demultiplexer(selector, this.timerEntries, this, true, false);

			this.sessionContext = new SessionContext(LogManager.getLogger("chinmobi.aio"),
					this.demultiplexer);
		}

		this.pipe = Pipe.open();

		this.session = createSession();

		this.helper.reset();

		doSetUp();
	}

	@Override
	protected final void tearDown() throws Exception {
		this.session.close();
		this.session.getEventHandler().close();

		closePipe(this.pipe);

		this.demultiplexer.clear();
		this.timerEntries.clear();
	}

	private static final void closePipe(final Pipe pipe) {
		try {
			pipe.sink().close();
		} catch (IOException ignore) {
		}

		try {
			pipe.source().close();
		} catch (IOException ignore) {
		}
	}


	public final void notifyTimerEntryAdded(final TimerEntrySet entrySet, final TimerEntry timerEntry) {
		this.demultiplexer.wakeup();
	}

	public final void notifyTimerEntryRuntimeException(final TimerEntry timerEntry, final RuntimeException ex) {
	}

	public final void assertActive() throws AIONotActiveException {
	}

	public final AIOServiceHandler createAIOServiceHandler(final AIOSession session) {
		return new ServiceHandler();
	}


	protected final SessionCreator sessionCreator() {
		return this.sessionContext.sessionCreator();
	}

	protected void doSetUp() throws Exception {
	}

	protected abstract Session createSession() throws Exception;

	protected abstract BaseActRequest<T> createRequest(AIOActEntry entry) throws Exception;

	protected abstract BaseActor<T> sessionActor();

	protected abstract boolean containsFree(BaseActRequest<T> request);


	/*
	 * Test methods
	 */


}
