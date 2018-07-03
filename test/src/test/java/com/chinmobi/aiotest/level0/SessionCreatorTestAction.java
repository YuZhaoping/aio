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
import java.nio.channels.Pipe;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;

import com.chinmobi.aio.AIOClosedSessionException;
import com.chinmobi.aio.AIONotActiveException;
import com.chinmobi.aio.AIOServiceHandler;
import com.chinmobi.aio.AIOSession;
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
public final class SessionCreatorTestAction extends BaseTestAction
	implements TimerEntrySet.Observer, Demultiplexer.ActiveChecker, AIOServiceHandler.Factory {

	private static final String TEST_SELECTOR = "test.selector";


	private final class ServiceHandler implements AIOServiceHandler {

		public final void handleAIOSessionOpened(final AIOSession session) throws AIOClosedSessionException {
			SessionCreatorTestAction.this.openedCount++;
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
			SessionCreatorTestAction.this.closedCount++;
		}

	}


	private TimerEntrySet timerEntries;
	private Demultiplexer demultiplexer;
	private SessionContext sessionContext;

	private Pipe pipe;

	private int openedCount;
	private int closedCount;


	public SessionCreatorTestAction() {
		super();
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

		this.openedCount = 0;
		this.closedCount = 0;
	}

	@Override
	protected final void tearDown() throws Exception {
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

	private final SessionCreator sessionCreator() {
		return this.sessionContext.sessionCreator();
	}

	/*
	 * Test methods
	 */

	public final void testCreateSession1() {
		try {
			final SelectableChannel channel = this.pipe.sink();

			final Session session = sessionCreator().createSession(channel, SelectionKey.OP_WRITE, this);

			assertNotNull(session);

			assertTrue(session.isOpen());
			assertNotNull(session.getServiceHandler());

			assertEquals(1, this.openedCount);

			// ---------------------------------------------
			assertFalse(this.sessionContext.containsFree(session));

			this.demultiplexer.clear();

			assertFalse(session.isOpen());
			assertNull(session.getServiceHandler());

			assertEquals(1, this.closedCount);

			assertTrue(this.sessionContext.containsFree(session));

		} catch (Exception ex) {
			fail(ex);
		}
	}

	public final void testCreateSession2() {
		try {
			final SelectableChannel channel = this.pipe.sink();

			final Session session1 = sessionCreator().createSession(channel, SelectionKey.OP_WRITE, this);

			assertNotNull(session1);

			assertTrue(session1.isOpen());
			assertNotNull(session1.getServiceHandler());

			assertTrue(session1.getEventHandler().selectableChannel() == channel);

			assertEquals(1, this.openedCount);

			// ---------------------------------------------
			final Session session2 = sessionCreator().createSession(session1.getEventHandler(),
					session1.getTransportChannel(), SelectionKey.OP_WRITE,
					SessionCreator.defaultExecutor(this));

			assertNotNull(session2);

			assertTrue(session1 != session2);
			assertTrue(session2.getEventHandler().selectableChannel() == channel);

			assertTrue(session2.isOpen());
			assertNotNull(session2.getServiceHandler());

			assertEquals(2, this.openedCount);

			assertFalse(session1.isOpen());
			assertNotNull(session1.getServiceHandler());
			assertNull(session1.getEventHandler().selectableChannel());

			assertTrue(session1.id() != session2.id());

			// ---------------------------------------------
			assertFalse(this.sessionContext.containsFree(session1));
			assertFalse(this.sessionContext.containsFree(session2));

			this.demultiplexer.clear();

			assertFalse(session2.isOpen());
			assertNull(session2.getServiceHandler());

			assertEquals(1, this.closedCount);

			assertNotNull(session1.getServiceHandler());

			session1.getEventHandler().close();
			assertNull(session1.getServiceHandler());

			assertEquals(2, this.closedCount);

			assertTrue(this.sessionContext.containsFree(session1));
			assertTrue(this.sessionContext.containsFree(session2));

		} catch (Exception ex) {
			fail(ex);
		}
	}

}
