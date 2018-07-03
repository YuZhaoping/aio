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
import java.nio.channels.Pipe;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.concurrent.TimeUnit;

import com.chinmobi.aio.AIOClosedSessionException;
import com.chinmobi.aio.AIONotActiveException;
import com.chinmobi.aio.AIOServiceHandler;
import com.chinmobi.aio.AIOSession;
import com.chinmobi.aio.impl.nio.Demultiplexer;
import com.chinmobi.aio.impl.nio.EventHandler;
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
public final class SessionTestAction extends BaseTestAction
	implements TimerEntrySet.Observer, Demultiplexer.ActiveChecker, AIOServiceHandler.Factory {

	private static final String TEST_SELECTOR = "test.selector";


	private final class ServiceHandler implements AIOServiceHandler {

		public final void handleAIOSessionOpened(final AIOSession session) throws AIOClosedSessionException {
			SessionTestAction.this.openedCount++;
		}

		public final boolean handleAIOSessionInputReady(final AIOSession session)
				throws IOException, AIOClosedSessionException {
			SessionTestAction.this.inputCount++;

			final ByteBuffer buffer = ByteBuffer.allocate(8);
			buffer.clear();

			try {
				session.readableChannel().read(buffer);
			} catch (Exception ex) {
				SessionTestAction.fail(ex);
			}

			return true;
		}

		public final boolean handleAIOSessionOutputReady(final AIOSession session)
				throws IOException, AIOClosedSessionException {
			SessionTestAction.this.outputCount++;

			final ByteBuffer buffer = ByteBuffer.allocate(8);
			buffer.put((byte)0);
			buffer.flip();

			try {
				session.writableChannel().write(buffer);
			} catch (Exception ex) {
				SessionTestAction.fail(ex);
			}

			if (SessionTestAction.this.testCase != 1) {
				session.setOutputEvent();
			}

			return true;
		}

		public final void handleAIOSessionTimeout(final AIOSession session) throws AIOClosedSessionException {
			SessionTestAction.this.timeoutCount++;
		}

		public final void handleAIOSessionClosed(final AIOSession session, final Throwable cause) {
			SessionTestAction.this.closedCount++;
		}

	}


	private TimerEntrySet timerEntries;
	private Demultiplexer demultiplexer;
	private SessionContext sessionContext;

	private Pipe pipe;

	private int testCase;

	private int openedCount;
	private int inputCount;
	private int outputCount;
	private int timeoutCount;
	private int closedCount;


	public SessionTestAction() {
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

		this.testCase = 0;

		this.openedCount = 0;
		this.inputCount = 0;
		this.outputCount = 0;
		this.timeoutCount = 0;
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

	public final void testCreate0() {
		try {
			final SelectableChannel channel = this.pipe.sink();

			final Session session = sessionCreator().createSession(channel, SelectionKey.OP_WRITE, this);

			assertNotNull(session);

			assertTrue(session.isOpen());
			assertNotNull(session.getServiceHandler());
			assertTrue(session.getEventHandler().selectableChannel() == channel);

			assertEquals(1, this.openedCount);

			// ---------------------------------------------
			assertNull(session.readableChannel());
			assertNotNull(session.writableChannel());

			assertNull(session.scatteringChannel());
			assertNotNull(session.gatheringChannel());

			assertNull(session.getLocalSocketAddress());
			assertNull(session.getRemoteSocketAddress());

			// ---------------------------------------------
			assertFalse(this.sessionContext.containsFree(session));

			this.demultiplexer.clear();

			assertFalse(session.isOpen());
			assertNull(session.getServiceHandler());
			assertNull(session.getEventHandler().selectableChannel());

			assertEquals(1, this.closedCount);

			assertTrue(this.sessionContext.containsFree(session));

		} catch (Exception ex) {
			fail(ex);
		}
	}

	public final void testCreate1() {
		try {
			final SelectableChannel channel = this.pipe.source();

			final Session session = sessionCreator().createSession(channel, SelectionKey.OP_READ, this);

			assertNotNull(session);

			assertTrue(session.isOpen());
			assertNotNull(session.getServiceHandler());
			assertTrue(session.getEventHandler().selectableChannel() == channel);

			assertEquals(1, this.openedCount);

			// ---------------------------------------------
			assertNotNull(session.readableChannel());
			assertNull(session.writableChannel());

			assertNotNull(session.scatteringChannel());
			assertNull(session.gatheringChannel());

			assertNull(session.getLocalSocketAddress());
			assertNull(session.getRemoteSocketAddress());

			// ---------------------------------------------
			assertFalse(this.sessionContext.containsFree(session));

			this.demultiplexer.clear();

			assertFalse(session.isOpen());
			assertNull(session.getServiceHandler());
			assertNull(session.getEventHandler().selectableChannel());

			assertEquals(1, this.closedCount);

			assertTrue(this.sessionContext.containsFree(session));

		} catch (Exception ex) {
			fail(ex);
		}
	}

	public final void testCreate2() {
		try {
			final SelectableChannel channel0 = this.pipe.sink();

			final Session session0 = sessionCreator().createSession(channel0, SelectionKey.OP_WRITE, this);

			assertNotNull(session0);

			assertTrue(session0.isOpen());
			assertNotNull(session0.getServiceHandler());
			assertTrue(session0.getEventHandler().selectableChannel() == channel0);

			final SelectableChannel channel1 = this.pipe.source();

			final Session session1 = sessionCreator().createSession(channel1, SelectionKey.OP_READ, this);

			assertNotNull(session1);

			assertTrue(session1.isOpen());
			assertNotNull(session1.getServiceHandler());
			assertTrue(session1.getEventHandler().selectableChannel() == channel1);

			assertEquals(2, this.openedCount);

			// ---------------------------------------------

			assertTrue(session0 != session1);
			assertTrue(session0.id() != session1.id());

			// ---------------------------------------------
			assertFalse(this.sessionContext.containsFree(session0));
			assertFalse(this.sessionContext.containsFree(session1));

			this.demultiplexer.clear();

			assertFalse(session0.isOpen());
			assertNull(session0.getServiceHandler());
			assertNull(session0.getEventHandler().selectableChannel());

			assertFalse(session1.isOpen());
			assertNull(session1.getServiceHandler());
			assertNull(session1.getEventHandler().selectableChannel());

			assertEquals(2, this.closedCount);

			assertTrue(this.sessionContext.containsFree(session0));
			assertTrue(this.sessionContext.containsFree(session1));

		} catch (Exception ex) {
			fail(ex);
		}
	}

	public final void testClose() {
		try {
			final SelectableChannel channel = this.pipe.sink();

			final Session session = sessionCreator().createSession(channel, SelectionKey.OP_WRITE, this);

			assertNotNull(session);

			assertTrue(session.isOpen());
			assertNotNull(session.getServiceHandler());
			assertTrue(session.getEventHandler().selectableChannel() == channel);

			assertEquals(1, this.openedCount);

			// ---------------------------------------------
			assertFalse(this.sessionContext.containsFree(session));

			session.close();

			assertFalse(session.isOpen());
			assertNotNull(session.getServiceHandler());
			assertNotNull(session.getEventHandler().selectableChannel());

			assertFalse(this.sessionContext.containsFree(session));

			// ---------------------------------------------
			this.demultiplexer.clear();

			assertFalse(session.isOpen());
			assertNull(session.getServiceHandler());
			assertNull(session.getEventHandler().selectableChannel());

			assertEquals(1, this.closedCount);

			assertTrue(this.sessionContext.containsFree(session));

		} catch (Exception ex) {
			fail(ex);
		}
	}

	public final void testTimeout0() {
		try {
			final SelectableChannel channel = this.pipe.sink();

			final Session session = sessionCreator().createSession(channel, SelectionKey.OP_WRITE, this);

			assertNotNull(session);

			assertTrue(session.isOpen());
			assertNotNull(session.getServiceHandler());
			assertTrue(session.getEventHandler().selectableChannel() == channel);

			assertEquals(1, this.openedCount);

			// ---------------------------------------------

			assertTrue(this.timerEntries.isEmpty());

			session.setTimeout(1000, TimeUnit.MILLISECONDS);
			assertFalse(this.timerEntries.isEmpty());

			session.setTimeout(0, TimeUnit.MILLISECONDS);
			assertTrue(this.timerEntries.isEmpty());

			session.setTimeout(1000, TimeUnit.MILLISECONDS);
			assertFalse(this.timerEntries.isEmpty());

			session.close();
			assertTrue(this.timerEntries.isEmpty());

			// ---------------------------------------------
			assertFalse(this.sessionContext.containsFree(session));

			this.demultiplexer.clear();

			assertFalse(session.isOpen());
			assertNull(session.getServiceHandler());
			assertNull(session.getEventHandler().selectableChannel());

			assertEquals(1, this.closedCount);

			assertTrue(this.sessionContext.containsFree(session));

		} catch (Exception ex) {
			fail(ex);
		}
	}

	public final void testTimeout1() {
		try {
			final SelectableChannel channel = this.pipe.sink();

			final Session session = sessionCreator().createSession(channel, SelectionKey.OP_WRITE, this);

			assertNotNull(session);

			assertTrue(session.isOpen());
			assertNotNull(session.getServiceHandler());
			assertTrue(session.getEventHandler().selectableChannel() == channel);

			assertEquals(1, this.openedCount);

			// ---------------------------------------------

			assertTrue(this.timerEntries.isEmpty());

			session.setTimeout(1000, TimeUnit.MILLISECONDS);
			assertFalse(this.timerEntries.isEmpty());

			for (int i = 0; i < 5; ++i) {
				while(this.demultiplexer.select() <= 0);

				assertTrue(this.timerEntries.isEmpty());
				assertTrue(this.demultiplexer.hasSelectedHandler());

				assertTrue(session.getEventHandler().runSelected() > 0);

				if (session.isInterestOpsPending()) {
					this.demultiplexer.handleInterestOpsPendings();
				}
				assertFalse(this.timerEntries.isEmpty());
			}

			// ---------------------------------------------
			assertFalse(this.sessionContext.containsFree(session));

			this.demultiplexer.clear();

			assertFalse(session.isOpen());
			assertNull(session.getServiceHandler());
			assertNull(session.getEventHandler().selectableChannel());

			assertEquals(1, this.closedCount);

			assertTrue(this.sessionContext.containsFree(session));

		} catch (Exception ex) {
			fail(ex);
		}
	}

	public final void testTimeout2() {
		try {
			final SelectableChannel channel = this.pipe.source();

			final Session session = sessionCreator().createSession(channel, SelectionKey.OP_READ, this);

			assertNotNull(session);

			assertTrue(session.isOpen());
			assertNotNull(session.getServiceHandler());
			assertTrue(session.getEventHandler().selectableChannel() == channel);

			assertEquals(1, this.openedCount);

			// ---------------------------------------------

			assertTrue(this.timerEntries.isEmpty());

			session.setTimeout(1000, TimeUnit.MILLISECONDS);
			assertFalse(this.timerEntries.isEmpty());

			for (int i = 0; i < 5; ++i) {
				final long now = System.currentTimeMillis() + 1000;

				assertTrue(this.timerEntries.hasTimeoutEntry(now));

				runAllTimeouts(now);

				assertEquals(1 + i, this.timeoutCount);

				if (session.isInterestOpsPending()) {
					this.demultiplexer.handleInterestOpsPendings();
				}
				assertFalse(this.timerEntries.isEmpty());
			}

			// ---------------------------------------------
			assertFalse(this.sessionContext.containsFree(session));

			this.demultiplexer.clear();

			assertFalse(session.isOpen());
			assertNull(session.getServiceHandler());
			assertNull(session.getEventHandler().selectableChannel());

			assertEquals(1, this.closedCount);

			assertTrue(this.sessionContext.containsFree(session));

		} catch (Exception ex) {
			fail(ex);
		}
	}

	public final void testInputOutput() {
		try {
			final SelectableChannel channel0 = this.pipe.sink();

			final Session session0 = sessionCreator().createSession(channel0, SelectionKey.OP_WRITE, this);

			assertNotNull(session0);

			assertTrue(session0.isOpen());
			assertNotNull(session0.getServiceHandler());
			assertTrue(session0.getEventHandler().selectableChannel() == channel0);

			final SelectableChannel channel1 = this.pipe.source();

			final Session session1 = sessionCreator().createSession(channel1, SelectionKey.OP_READ, this);

			assertNotNull(session1);

			assertTrue(session1.isOpen());
			assertNotNull(session1.getServiceHandler());
			assertTrue(session1.getEventHandler().selectableChannel() == channel1);

			assertEquals(2, this.openedCount);

			// ---------------------------------------------
			session0.setTimeout(1000, TimeUnit.MILLISECONDS);
			session1.setTimeout(1000, TimeUnit.MILLISECONDS);

			this.testCase = 1;

			for (int i = 0; i < 5; ++i) {
				session0.setOutputEvent();

				if (session0.isInterestOpsPending()) {
					this.demultiplexer.handleInterestOpsPendings();
				}
				assertFalse(this.timerEntries.isEmpty());

				while(this.demultiplexer.select() <= 0);

				EventHandler selectedHandler = this.demultiplexer.pollSelectedHandler();
				assertNotNull(selectedHandler);

				assertTrue(selectedHandler == session0.getEventHandler());

				selectedHandler.runSelected();

				assertEquals(1 + i, this.outputCount);

				while(this.demultiplexer.select() <= 0);

				selectedHandler = this.demultiplexer.pollSelectedHandler();
				assertNotNull(selectedHandler);

				assertTrue(selectedHandler == session1.getEventHandler());

				selectedHandler.runSelected();

				assertEquals(1 + i, this.inputCount);
			}

			// ---------------------------------------------
			assertFalse(this.sessionContext.containsFree(session0));
			assertFalse(this.sessionContext.containsFree(session1));

			this.demultiplexer.clear();

			assertFalse(session0.isOpen());
			assertNull(session0.getServiceHandler());
			assertNull(session0.getEventHandler().selectableChannel());

			assertFalse(session1.isOpen());
			assertNull(session1.getServiceHandler());
			assertNull(session1.getEventHandler().selectableChannel());

			assertEquals(2, this.closedCount);

			assertTrue(this.sessionContext.containsFree(session0));
			assertTrue(this.sessionContext.containsFree(session1));

		} catch (Exception ex) {
			fail(ex);
		}
	}

	private final void runAllTimeouts(final long now) {
		final boolean[] hasNext = new boolean[1];

		TimerEntry entry = this.timerEntries.checkTimeoutEntry(now, hasNext);
		while (entry != null) {
			entry.getRunnable().run();

			if (hasNext[0]) {
				entry = this.timerEntries.checkTimeoutEntry(now, hasNext);
			} else {
				break;
			}
		}
	}

}
