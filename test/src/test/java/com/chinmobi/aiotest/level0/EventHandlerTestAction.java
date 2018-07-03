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

import com.chinmobi.aio.AIONotActiveException;
import com.chinmobi.aio.impl.nio.Demultiplexer;
import com.chinmobi.aio.impl.nio.EventHandler;
import com.chinmobi.aio.impl.nio.RunnableQueueNode;
import com.chinmobi.aio.impl.util.TimerEntry;
import com.chinmobi.aio.impl.util.TimerEntrySet;
import com.chinmobi.app.action.ActionContext;
import com.chinmobi.testapp.BaseTestAction;

/**
 * @author <a href="mailto:yuzhaoping1970@gmail.com">Zhaoping Yu</a>
 *
 */
public final class EventHandlerTestAction extends BaseTestAction
	implements TimerEntrySet.Observer, Demultiplexer.ActiveChecker, EventHandler.Factory {

	private static final String TEST_SELECTOR = "test.selector";


	private final class Handler extends EventHandler {

		private volatile SelectableChannel selectableChannel;


		Handler(final SelectableChannel selectableChannel) {
			super();
			this.selectableChannel = selectableChannel;
		}


		@Override
		public final SelectableChannel selectableChannel() {
			return this.selectableChannel;
		}

		@Override
		protected final void onClearSelectionKey() {
			this.selectableChannel = null;
		}

		@Override
		protected final void onCloseChannel() {
			final SelectableChannel channel = this.selectableChannel;
			if (channel !=null) {
				try {
					channel.close();
				} catch (IOException ignore) {
				}
			}
		}

		@Override
		protected final int process(final SelectionKey key) {
			EventHandlerTestAction.assertEquals(0, this.runSelected());

			EventHandlerTestAction.this.processCount++;

			switch (EventHandlerTestAction.this.testCase) {
			case -1:
				this.cancel();
				break;

			case  1:
				this.advanceSelect();
				return 1;

			case  2:
				this.close();
				return -1;

			case  3:
				key.cancel();
				key.isWritable(); // to throw CancelledKeyException
				break;

			default:
			}
			return 0;
		}

		@Override
		protected final void onSelected(final int readyOps, final int modCount) {
			EventHandlerTestAction.this.selectedCount++;
		}

		@Override
		protected final int onTimeout() {
			EventHandlerTestAction.this.timeoutCount++;
			return 0;
		}

		@Override
		protected final void onCancelled() {
			EventHandlerTestAction.this.cancelledCount++;
		}

		@Override
		protected final void onFailed(final Throwable cause) {
			EventHandlerTestAction.this.failedCount++;
		}

		@Override
		protected final void onClosed() {
			EventHandlerTestAction.this.closedCount++;
		}

		@Override
		protected final void onReleased() {
			EventHandlerTestAction.this.releasedCount++;
		}

	}


	private TimerEntrySet timerEntries;
	private Demultiplexer demultiplexer;

	private Pipe pipe;

	private int processCount;
	private int timeoutCount;
	private int selectedCount;
	private int cancelledCount;
	private int failedCount;
	private int closedCount;
	private int releasedCount;

	private int testCase;


	public EventHandlerTestAction() {
		super();
		this.demultiplexer = null;
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
		if (this.demultiplexer == null) {
			final Selector selector = getSelector();

			this.timerEntries = new TimerEntrySet(this);
			this.demultiplexer = new Demultiplexer(selector, this.timerEntries, this, true, false);
		}
		this.pipe = Pipe.open();

		this.processCount = 0;
		this.timeoutCount = 0;
		this.selectedCount = 0;
		this.cancelledCount = 0;
		this.failedCount = 0;
		this.closedCount = 0;
		this.releasedCount = 0;

		this.testCase = 0;
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

	public final EventHandler createEventHandler(final Object token) {
		final SelectableChannel channel = (SelectableChannel)token;
		return new Handler(channel);
	}

	/*
	 * Test methods
	 */

	public final void testRegister() {
		try {
			final int size = this.demultiplexer.selector().keys().size();

			final SelectableChannel channel = this.pipe.sink();
			final EventHandler handler = this.demultiplexer.registerChannel(channel, SelectionKey.OP_WRITE, this, channel, false);

			assertNotNull(handler);
			assertTrue(handler.isOpen());
			assertTrue(this.demultiplexer.contains(handler));
			assertTrue(this.demultiplexer.containsInInterestOpsPendings(handler));
			assertTrue(channel == handler.selectableChannel());

			assertEquals(size + 1, this.demultiplexer.selector().keys().size());

			// ---------------------------------------------

			// ---------------------------------------------
			handler.close();

			assertEquals(0, this.selectedCount);
			assertEquals(0, this.cancelledCount);
			assertEquals(1, this.closedCount);

			assertFalse(handler.isOpen());
			assertFalse(this.demultiplexer.contains(handler));
			assertFalse(this.demultiplexer.containsInInterestOpsPendings(handler));
			assertNull(handler.selectableChannel());
		} catch (Exception ex) {
			fail(ex);
		}
	}

	public final void testClose() {
		try {
			final int size = this.demultiplexer.selector().keys().size();

			final SelectableChannel channel = this.pipe.sink();
			final EventHandler handler = this.demultiplexer.registerChannel(channel, SelectionKey.OP_WRITE, this, channel, false);

			assertNotNull(handler);
			assertTrue(handler.isOpen());
			assertTrue(this.demultiplexer.contains(handler));
			assertTrue(this.demultiplexer.containsInInterestOpsPendings(handler));
			assertTrue(channel == handler.selectableChannel());

			assertEquals(size + 1, this.demultiplexer.selector().keys().size());

			// ---------------------------------------------

			handler.setTimeout(5);
			assertFalse(this.timerEntries.isEmpty());

			handler.close();

			assertTrue(this.timerEntries.isEmpty());

			assertFalse(handler.release());
			assertFalse(handler.fail(null));

			assertEquals(0, this.selectedCount);
			assertEquals(0, this.cancelledCount);
			assertEquals(1, this.closedCount);
			assertEquals(0, this.failedCount);
			assertEquals(0, this.releasedCount);

			assertFalse(handler.isOpen());
			assertFalse(this.demultiplexer.contains(handler));
			assertFalse(this.demultiplexer.containsInInterestOpsPendings(handler));
			assertNull(handler.selectableChannel());

			// ---------------------------------------------
			handler.close();
			assertEquals(1, this.closedCount);
		} catch (Exception ex) {
			fail(ex);
		}
	}

	public final void testFree() {
		try {
			final int size = this.demultiplexer.selector().keys().size();

			final SelectableChannel channel = this.pipe.sink();
			final EventHandler handler = this.demultiplexer.registerChannel(channel, SelectionKey.OP_WRITE, this, channel, false);

			assertNotNull(handler);
			assertTrue(handler.isOpen());
			assertTrue(this.demultiplexer.contains(handler));
			assertTrue(this.demultiplexer.containsInInterestOpsPendings(handler));
			assertTrue(channel == handler.selectableChannel());

			assertEquals(size + 1, this.demultiplexer.selector().keys().size());

			// ---------------------------------------------

			handler.setTimeout(5);
			assertFalse(this.timerEntries.isEmpty());

			handler.close();

			assertTrue(this.timerEntries.isEmpty());

			assertFalse(handler.release());
			assertFalse(handler.fail(null));

			assertEquals(0, this.selectedCount);
			assertEquals(0, this.cancelledCount);
			assertEquals(1, this.closedCount);
			assertEquals(0, this.failedCount);
			assertEquals(0, this.releasedCount);

			assertFalse(handler.isOpen());
			assertFalse(this.demultiplexer.contains(handler));
			assertFalse(this.demultiplexer.containsInInterestOpsPendings(handler));
			assertNull(handler.selectableChannel());

			// ---------------------------------------------
			handler.close();
			assertEquals(1, this.closedCount);
		} catch (Exception ex) {
			fail(ex);
		}
	}

	public final void testRelease() {
		try {
			final int size = this.demultiplexer.selector().keys().size();

			final SelectableChannel channel = this.pipe.sink();
			final EventHandler handler = this.demultiplexer.registerChannel(channel, SelectionKey.OP_WRITE, this, channel, false);

			assertNotNull(handler);
			assertTrue(handler.isOpen());
			assertTrue(this.demultiplexer.contains(handler));
			assertTrue(this.demultiplexer.containsInInterestOpsPendings(handler));
			assertTrue(channel == handler.selectableChannel());

			assertEquals(size + 1, this.demultiplexer.selector().keys().size());

			// ---------------------------------------------

			handler.setTimeout(5);
			assertFalse(this.timerEntries.isEmpty());

			assertTrue(handler.release());

			assertTrue(this.timerEntries.isEmpty());

			// ---------------------------------------------
			assertFalse(handler.release());
			assertFalse(handler.fail(null));

			handler.close();

			assertEquals(0, this.selectedCount);
			assertEquals(0, this.cancelledCount);
			assertEquals(0, this.closedCount);
			assertEquals(0, this.failedCount);
			assertEquals(1, this.releasedCount);

			assertFalse(handler.isOpen());
			assertFalse(this.demultiplexer.contains(handler));
			assertFalse(this.demultiplexer.containsInInterestOpsPendings(handler));
			assertNull(handler.selectableChannel());
		} catch (Exception ex) {
			fail(ex);
		}
	}

	public final void testFail() {
		try {
			final int size = this.demultiplexer.selector().keys().size();

			final SelectableChannel channel = this.pipe.sink();
			final EventHandler handler = this.demultiplexer.registerChannel(channel, SelectionKey.OP_WRITE, this, channel, false);

			assertNotNull(handler);
			assertTrue(handler.isOpen());
			assertTrue(this.demultiplexer.contains(handler));
			assertTrue(this.demultiplexer.containsInInterestOpsPendings(handler));
			assertTrue(channel == handler.selectableChannel());

			assertEquals(size + 1, this.demultiplexer.selector().keys().size());

			// ---------------------------------------------

			handler.setTimeout(5);
			assertFalse(this.timerEntries.isEmpty());

			assertTrue(handler.fail(null));

			assertTrue(this.timerEntries.isEmpty());

			// ---------------------------------------------
			assertFalse(handler.release());
			assertFalse(handler.fail(null));

			handler.close();

			assertEquals(0, this.selectedCount);
			assertEquals(0, this.cancelledCount);
			assertEquals(0, this.closedCount);
			assertEquals(1, this.failedCount);
			assertEquals(0, this.releasedCount);

			assertFalse(handler.isOpen());
			assertFalse(this.demultiplexer.contains(handler));
			assertFalse(this.demultiplexer.containsInInterestOpsPendings(handler));
			assertNull(handler.selectableChannel());
		} catch (Exception ex) {
			fail(ex);
		}
	}

	public final void testCancel0() {
		try {
			final int size = this.demultiplexer.selector().keys().size();

			final SelectableChannel channel = this.pipe.sink();
			final EventHandler handler = this.demultiplexer.registerChannel(channel, SelectionKey.OP_WRITE, this, channel, false);

			assertNotNull(handler);
			assertTrue(handler.isOpen());
			assertTrue(this.demultiplexer.contains(handler));
			assertTrue(this.demultiplexer.containsInInterestOpsPendings(handler));
			assertTrue(channel == handler.selectableChannel());

			assertEquals(size + 1, this.demultiplexer.selector().keys().size());

			// ---------------------------------------------

			assertFalse(this.demultiplexer.hasRunnable());

			assertTrue(handler.cancel()  > 0);

			assertEquals(1, this.cancelledCount);

			assertFalse(handler.isOpen());

			assertTrue(this.demultiplexer.hasRunnable());

			assertFalse(this.demultiplexer.containsInInterestOpsPendings(handler));

			assertTrue(handler.cancel() == 0);

			assertEquals(1, this.cancelledCount);

			runAllRunnables();

			assertEquals(1, this.closedCount);

			assertTrue(handler.cancel() == 0);

			// ---------------------------------------------
			assertFalse(handler.release());
			assertFalse(handler.fail(null));

			handler.close();

			assertEquals(0, this.selectedCount);
			assertEquals(1, this.cancelledCount);
			assertEquals(1, this.closedCount);
			assertEquals(0, this.failedCount);
			assertEquals(0, this.releasedCount);

			assertFalse(handler.isOpen());
			assertFalse(this.demultiplexer.contains(handler));
			assertNull(handler.selectableChannel());
		} catch (Exception ex) {
			fail(ex);
		}
	}

	public final void testCancel1() {
		try {
			final int size = this.demultiplexer.selector().keys().size();

			final SelectableChannel channel = this.pipe.sink();
			final EventHandler handler = this.demultiplexer.registerChannel(channel, SelectionKey.OP_WRITE, this, channel, false);

			assertNotNull(handler);
			assertTrue(handler.isOpen());
			assertTrue(this.demultiplexer.contains(handler));
			assertTrue(this.demultiplexer.containsInInterestOpsPendings(handler));
			assertTrue(channel == handler.selectableChannel());

			assertEquals(size + 1, this.demultiplexer.selector().keys().size());

			// ---------------------------------------------

			while (this.demultiplexer.select() <= 0);

			assertEquals(1, this.selectedCount);

			assertFalse(this.demultiplexer.containsInInterestOpsPendings(handler));
			assertTrue(this.demultiplexer.containsInSelectedHandlers(handler));

			assertTrue(this.demultiplexer.hasSelectedHandler());


			assertTrue(handler.cancel()  < 0);

			assertFalse(handler.isOpen());

			runAllSelecteds();

			assertEquals(1, this.closedCount);

			// ---------------------------------------------
			assertFalse(handler.release());
			assertFalse(handler.fail(null));

			handler.close();

			assertEquals(1, this.selectedCount);
			assertEquals(0, this.cancelledCount);
			assertEquals(1, this.closedCount);
			assertEquals(0, this.failedCount);
			assertEquals(0, this.releasedCount);

			assertFalse(handler.isOpen());
			assertFalse(this.demultiplexer.contains(handler));
			assertNull(handler.selectableChannel());
		} catch (Exception ex) {
			fail(ex);
		}
	}

	public final void testCancel2() {
		try {
			final int size = this.demultiplexer.selector().keys().size();

			final SelectableChannel channel = this.pipe.sink();
			final EventHandler handler = this.demultiplexer.registerChannel(channel, SelectionKey.OP_WRITE, this, channel, false);

			assertNotNull(handler);
			assertTrue(handler.isOpen());
			assertTrue(this.demultiplexer.contains(handler));
			assertTrue(this.demultiplexer.containsInInterestOpsPendings(handler));
			assertTrue(channel == handler.selectableChannel());

			assertEquals(size + 1, this.demultiplexer.selector().keys().size());

			// ---------------------------------------------

			while (this.demultiplexer.select() <= 0);

			assertEquals(1, this.selectedCount);

			assertFalse(this.demultiplexer.containsInInterestOpsPendings(handler));
			assertTrue(this.demultiplexer.containsInSelectedHandlers(handler));

			assertTrue(this.demultiplexer.hasSelectedHandler());

			this.testCase = -1;

			runAllSelecteds();

			assertEquals(1, this.closedCount);

			// ---------------------------------------------
			assertFalse(handler.release());
			assertFalse(handler.fail(null));

			handler.close();

			assertEquals(1, this.selectedCount);
			assertEquals(0, this.cancelledCount);
			assertEquals(1, this.closedCount);
			assertEquals(0, this.failedCount);
			assertEquals(0, this.releasedCount);

			assertFalse(handler.isOpen());
			assertFalse(this.demultiplexer.contains(handler));
			assertNull(handler.selectableChannel());
		} catch (Exception ex) {
			fail(ex);
		}
	}

	public final void testRunSelected0() {
		try {
			final int size = this.demultiplexer.selector().keys().size();

			final SelectableChannel channel = this.pipe.sink();
			final EventHandler handler = this.demultiplexer.registerChannel(channel, SelectionKey.OP_WRITE, this, channel, false);

			assertNotNull(handler);
			assertTrue(handler.isOpen());
			assertTrue(this.demultiplexer.contains(handler));
			assertTrue(this.demultiplexer.containsInInterestOpsPendings(handler));
			assertTrue(channel == handler.selectableChannel());

			assertEquals(size + 1, this.demultiplexer.selector().keys().size());

			// ---------------------------------------------

			for (int i = 0; i < 5; ++i) {

				while (this.demultiplexer.select() <= 0);

				assertEquals(1 + i, this.selectedCount);

				assertFalse(this.demultiplexer.containsInInterestOpsPendings(handler));
				assertTrue(this.demultiplexer.containsInSelectedHandlers(handler));

				assertTrue(this.demultiplexer.hasSelectedHandler());

				runAllSelecteds();

				assertEquals(1 + i, this.processCount);

				assertEquals(0, this.closedCount);

				assertFalse(this.demultiplexer.hasSelectedHandler());

				assertTrue(this.demultiplexer.containsInInterestOpsPendings(handler));
			}

			// ---------------------------------------------
			handler.close();

			assertFalse(handler.release());
			assertFalse(handler.fail(null));

			handler.close();

			assertEquals(0, this.cancelledCount);
			assertEquals(1, this.closedCount);
			assertEquals(0, this.failedCount);
			assertEquals(0, this.releasedCount);

			assertFalse(handler.isOpen());
			assertFalse(this.demultiplexer.contains(handler));
			assertNull(handler.selectableChannel());
		} catch (Exception ex) {
			fail(ex);
		}
	}

	public final void testRunSelected1() {
		try {
			final int size = this.demultiplexer.selector().keys().size();

			final SelectableChannel channel = this.pipe.sink();
			final EventHandler handler = this.demultiplexer.registerChannel(channel, SelectionKey.OP_WRITE, this, channel, false);

			assertNotNull(handler);
			assertTrue(handler.isOpen());
			assertTrue(this.demultiplexer.contains(handler));
			assertTrue(this.demultiplexer.containsInInterestOpsPendings(handler));
			assertTrue(channel == handler.selectableChannel());

			assertEquals(size + 1, this.demultiplexer.selector().keys().size());

			// ---------------------------------------------

			for (int i = 0; i < 5; ++i) {

				while (this.demultiplexer.select() <= 0);

				assertEquals(1 + i, this.selectedCount);

				assertFalse(this.demultiplexer.containsInInterestOpsPendings(handler));
				assertTrue(this.demultiplexer.containsInSelectedHandlers(handler));

				assertTrue(this.demultiplexer.hasSelectedHandler());

				this.testCase = 1;

				runAllSelecteds();

				assertEquals(1 + i, this.processCount);

				assertEquals(0, this.closedCount);

				assertFalse(this.demultiplexer.hasSelectedHandler());

				assertTrue(this.demultiplexer.containsInInterestOpsPendings(handler));
			}

			// ---------------------------------------------
			handler.close();

			assertFalse(handler.release());
			assertFalse(handler.fail(null));

			handler.close();

			assertEquals(0, this.cancelledCount);
			assertEquals(1, this.closedCount);
			assertEquals(0, this.failedCount);
			assertEquals(0, this.releasedCount);

			assertFalse(handler.isOpen());
			assertFalse(this.demultiplexer.contains(handler));
			assertNull(handler.selectableChannel());
		} catch (Exception ex) {
			fail(ex);
		}
	}

	public final void testRunSelected2() {
		try {
			final int size = this.demultiplexer.selector().keys().size();

			final SelectableChannel channel = this.pipe.sink();
			final EventHandler handler = this.demultiplexer.registerChannel(channel, SelectionKey.OP_WRITE, this, channel, false);

			assertNotNull(handler);
			assertTrue(handler.isOpen());
			assertTrue(this.demultiplexer.contains(handler));
			assertTrue(this.demultiplexer.containsInInterestOpsPendings(handler));
			assertTrue(channel == handler.selectableChannel());

			assertEquals(size + 1, this.demultiplexer.selector().keys().size());

			// ---------------------------------------------

			while (this.demultiplexer.select() <= 0);

			assertEquals(1, this.selectedCount);

			assertFalse(this.demultiplexer.containsInInterestOpsPendings(handler));
			assertTrue(this.demultiplexer.containsInSelectedHandlers(handler));

			assertTrue(this.demultiplexer.hasSelectedHandler());

			this.testCase = 2;

			runAllSelecteds();

			assertEquals(1, this.processCount);

			assertEquals(1, this.closedCount);

			// ---------------------------------------------
			handler.close();

			assertFalse(handler.release());
			assertFalse(handler.fail(null));

			handler.close();

			assertEquals(0, this.cancelledCount);
			assertEquals(1, this.closedCount);
			assertEquals(0, this.failedCount);
			assertEquals(0, this.releasedCount);

			assertFalse(handler.isOpen());
			assertFalse(this.demultiplexer.contains(handler));
			assertNull(handler.selectableChannel());
		} catch (Exception ex) {
			fail(ex);
		}
	}

	public final void testRunSelected3() {
		try {
			final int size = this.demultiplexer.selector().keys().size();

			final SelectableChannel channel = this.pipe.sink();
			final EventHandler handler = this.demultiplexer.registerChannel(channel, SelectionKey.OP_WRITE, this, channel, false);

			assertNotNull(handler);
			assertTrue(handler.isOpen());
			assertTrue(this.demultiplexer.contains(handler));
			assertTrue(this.demultiplexer.containsInInterestOpsPendings(handler));
			assertTrue(channel == handler.selectableChannel());

			assertEquals(size + 1, this.demultiplexer.selector().keys().size());

			// ---------------------------------------------

			while (this.demultiplexer.select() <= 0);

			assertEquals(1, this.selectedCount);

			assertFalse(this.demultiplexer.containsInInterestOpsPendings(handler));
			assertTrue(this.demultiplexer.containsInSelectedHandlers(handler));

			assertTrue(this.demultiplexer.hasSelectedHandler());

			this.testCase = 3;

			runAllSelecteds();

			assertEquals(1, this.processCount);

			assertEquals(1, this.closedCount);

			// ---------------------------------------------
			handler.close();

			assertFalse(handler.release());
			assertFalse(handler.fail(null));

			handler.close();

			assertEquals(0, this.cancelledCount);
			assertEquals(1, this.closedCount);
			assertEquals(0, this.failedCount);
			assertEquals(0, this.releasedCount);

			assertFalse(handler.isOpen());
			assertFalse(this.demultiplexer.contains(handler));
			assertNull(handler.selectableChannel());
		} catch (Exception ex) {
			fail(ex);
		}
	}

	public final void testSetInterestOps() {
		try {
			final int size = this.demultiplexer.selector().keys().size();

			final SelectableChannel channel = this.pipe.sink();
			final EventHandler handler = this.demultiplexer.registerChannel(channel, 0, this, channel, false);

			assertNotNull(handler);
			assertTrue(handler.isOpen());
			assertTrue(this.demultiplexer.contains(handler));
			assertFalse(this.demultiplexer.containsInInterestOpsPendings(handler));
			assertTrue(channel == handler.selectableChannel());

			assertEquals(size + 1, this.demultiplexer.selector().keys().size());

			// ---------------------------------------------

			handler.setInterestOps(SelectionKey.OP_WRITE | SelectionKey.OP_READ, true);
			assertTrue(this.demultiplexer.containsInInterestOpsPendings(handler));

			handler.clearInterestOps(SelectionKey.OP_READ, true);
			assertTrue(this.demultiplexer.containsInInterestOpsPendings(handler));

			while (this.demultiplexer.select() <= 0);

			assertEquals(1, this.selectedCount);

			assertFalse(this.demultiplexer.containsInInterestOpsPendings(handler));
			assertTrue(this.demultiplexer.containsInSelectedHandlers(handler));

			assertTrue(this.demultiplexer.hasSelectedHandler());

			runAllSelecteds();

			assertEquals(1, this.processCount);

			// ---------------------------------------------
			handler.close();

			assertFalse(handler.release());
			assertFalse(handler.fail(null));

			handler.close();

			assertEquals(0, this.cancelledCount);
			assertEquals(1, this.closedCount);
			assertEquals(0, this.failedCount);
			assertEquals(0, this.releasedCount);

			assertFalse(handler.isOpen());
			assertFalse(this.demultiplexer.contains(handler));
			assertNull(handler.selectableChannel());
		} catch (Exception ex) {
			fail(ex);
		}
	}

	public final void testSetTimeout() {
		try {
			final int size = this.demultiplexer.selector().keys().size();

			final SelectableChannel channel = this.pipe.sink();
			final EventHandler handler = this.demultiplexer.registerChannel(channel, SelectionKey.OP_WRITE, this, channel, false);

			assertNotNull(handler);
			assertTrue(handler.isOpen());
			assertTrue(this.demultiplexer.contains(handler));
			assertTrue(this.demultiplexer.containsInInterestOpsPendings(handler));
			assertTrue(channel == handler.selectableChannel());

			assertEquals(size + 1, this.demultiplexer.selector().keys().size());

			// ---------------------------------------------

			assertTrue(this.timerEntries.isEmpty());

			handler.setTimeout(0);
			assertTrue(this.timerEntries.isEmpty());

			handler.setTimeout(5);
			assertFalse(this.timerEntries.isEmpty());

			int loopCount = 0;
			while (this.demultiplexer.select() <= 0 && !handler.isTimeout()) {
				if (++loopCount > 9) {
					this.out().println(handler.toString());
					break;
				}
			}

			if (!handler.isTimeout()) {
				assertEquals(1, this.selectedCount);

				assertTrue(this.timerEntries.isEmpty());

				assertFalse(this.demultiplexer.containsInInterestOpsPendings(handler));
				assertTrue(this.demultiplexer.containsInSelectedHandlers(handler));

				assertTrue(this.demultiplexer.hasSelectedHandler());

				runAllSelecteds();

				assertEquals(1, this.processCount);
			} else {
				assertEquals(0, this.selectedCount);

				final boolean inOpsPending = this.demultiplexer.containsInInterestOpsPendings(handler);
				if (inOpsPending) {
					this.out().println(handler.toString());
				}
				assertFalse(inOpsPending);
				assertFalse(this.demultiplexer.containsInSelectedHandlers(handler));

				assertFalse(this.timerEntries.isEmpty());
				assertTrue(this.timerEntries.hasTimeoutEntry());

				runAllTimeouts();

				assertEquals(1, this.timeoutCount);
			}

			assertTrue(this.demultiplexer.containsInInterestOpsPendings(handler));
			this.demultiplexer.handleInterestOpsPendings();
			assertFalse(this.timerEntries.isEmpty());

			handler.cancel();
			assertTrue(this.timerEntries.isEmpty());

			handler.setTimeout(5);
			assertTrue(this.timerEntries.isEmpty());

			// ---------------------------------------------
			handler.close();

			assertFalse(handler.release());
			assertFalse(handler.fail(null));

			handler.close();

			assertEquals(1, this.cancelledCount);
			assertEquals(1, this.closedCount);
			assertEquals(0, this.failedCount);
			assertEquals(0, this.releasedCount);

			assertFalse(handler.isOpen());
			assertFalse(this.demultiplexer.contains(handler));
			assertNull(handler.selectableChannel());
		} catch (Exception ex) {
			fail(ex);
		}
	}

	public final void testRunTimeout() {
		try {
			final int size = this.demultiplexer.selector().keys().size();

			final SelectableChannel channel = this.pipe.source();
			final EventHandler handler = this.demultiplexer.registerChannel(channel, SelectionKey.OP_READ, this, channel, false);

			assertNotNull(handler);
			assertTrue(handler.isOpen());
			assertTrue(this.demultiplexer.contains(handler));
			assertTrue(this.demultiplexer.containsInInterestOpsPendings(handler));
			assertTrue(channel == handler.selectableChannel());

			assertEquals(size + 1, this.demultiplexer.selector().keys().size());

			// ---------------------------------------------

			assertTrue(this.timerEntries.isEmpty());

			handler.setTimeout(5);
			assertFalse(this.timerEntries.isEmpty());
			assertFalse(this.timerEntries.hasTimeoutEntry());

			for (int i = 0; i < 1; ++i) {
				while (!this.timerEntries.hasTimeoutEntry()) {
					this.demultiplexer.select();
				}

				assertEquals(0, this.selectedCount);

				assertFalse(this.demultiplexer.containsInInterestOpsPendings(handler));
				assertFalse(this.demultiplexer.containsInSelectedHandlers(handler));

				assertFalse(this.timerEntries.isEmpty());
				assertTrue(this.timerEntries.hasTimeoutEntry());

				runAllTimeouts();

				assertEquals(1 + i, this.timeoutCount);

				assertTrue(this.demultiplexer.containsInInterestOpsPendings(handler));
				this.demultiplexer.handleInterestOpsPendings();
				assertFalse(this.timerEntries.isEmpty());
			}

			// ---------------------------------------------
			handler.close();

			assertTrue(this.timerEntries.isEmpty());

			assertFalse(handler.release());
			assertFalse(handler.fail(null));

			handler.close();

			assertEquals(0, this.cancelledCount);
			assertEquals(1, this.closedCount);
			assertEquals(0, this.failedCount);
			assertEquals(0, this.releasedCount);

			assertFalse(handler.isOpen());
			assertFalse(this.demultiplexer.contains(handler));
			assertNull(handler.selectableChannel());
		} catch (Exception ex) {
			fail(ex);
		}
	}

	private final void runAllRunnables() {
		RunnableQueueNode runnableNode = this.demultiplexer.pollRunnable();
		while (runnableNode != null) {
			runnableNode.getRunnable().run();

			runnableNode = this.demultiplexer.pollRunnable();
		}
	}

	private final void runAllSelecteds() {
		EventHandler handler = this.demultiplexer.pollSelectedHandler();
		while (handler != null) {
			handler.runSelected();

			handler = this.demultiplexer.pollSelectedHandler();
		}
	}

	private final void runAllTimeouts() {
		final boolean[] hasNext = new boolean[1];

		TimerEntry entry = this.timerEntries.checkTimeoutEntry(hasNext);
		while (entry != null) {
			entry.getRunnable().run();

			if (hasNext[0]) {
				entry = this.timerEntries.checkTimeoutEntry(hasNext);
			} else {
				break;
			}
		}
	}

}
