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
public final class DemultiplexerTestAction extends BaseTestAction
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
			return 0;
		}

		@Override
		protected final void onSelected(final int readyOps, final int modCount) {
			DemultiplexerTestAction.this.selectedCount++;
		}

		@Override
		protected final void onCancelled() {
			DemultiplexerTestAction.this.cancelledCount++;
		}

		@Override
		protected final void onClosed() {
			DemultiplexerTestAction.this.closedCount++;
		}

	}


	private TimerEntrySet timerEntries;
	private Demultiplexer demultiplexer;

	private Pipe pipe;

	private int selectedCount;
	private int cancelledCount;
	private int closedCount;


	public DemultiplexerTestAction() {
		super();
		this.demultiplexer = null;
	}


	@Override
	public final void init(final ActionContext context) {
		super.init(context);

		Selector selector = (Selector)context.getAttribute(TEST_SELECTOR);
		if (selector == null || !selector.isOpen()) {

			try {
				selector = Selector.open();
			} catch (IOException ex) {
				throw new RuntimeException(ex);
			}

			context.setAttribute(TEST_SELECTOR, selector);
		}
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

	@Override
	protected final void setUp(final String methodName) throws Exception {
		if (this.demultiplexer == null) {
			final Selector selector = (Selector)context().getAttribute(TEST_SELECTOR);

			this.timerEntries = new TimerEntrySet(this);
			this.demultiplexer = new Demultiplexer(selector, this.timerEntries, this, true, false);
		}
		this.pipe = Pipe.open();
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

	public final void testRegisterChannel() {
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

			handler.close();

			assertFalse(handler.isOpen());
			assertFalse(this.demultiplexer.contains(handler));
			assertFalse(this.demultiplexer.containsInInterestOpsPendings(handler));
			assertNull(handler.selectableChannel());
		} catch (Exception ex) {
			fail(ex);
		}
	}

	public final void testTransferHandler() {
		try {
			final int size = this.demultiplexer.selector().keys().size();

			final SelectableChannel channel = this.pipe.source();
			final EventHandler handler0 = this.demultiplexer.registerChannel(channel, SelectionKey.OP_READ, this, channel, false);

			assertNotNull(handler0);
			assertTrue(handler0.isOpen());
			assertTrue(this.demultiplexer.contains(handler0));
			assertTrue(this.demultiplexer.containsInInterestOpsPendings(handler0));
			assertTrue(channel == handler0.selectableChannel());

			assertEquals(size + 1, this.demultiplexer.selector().keys().size());

			final EventHandler handler1 = this.demultiplexer.transferHandler(handler0, SelectionKey.OP_READ, this, channel, false);

			assertNotNull(handler1);
			assertTrue(handler1.isOpen());
			assertTrue(this.demultiplexer.contains(handler1));
			assertTrue(this.demultiplexer.containsInInterestOpsPendings(handler1));
			assertTrue(channel == handler1.selectableChannel());

			assertFalse(handler0.isOpen());
			assertFalse(this.demultiplexer.contains(handler0));
			assertFalse(this.demultiplexer.containsInInterestOpsPendings(handler0));
			assertNull(handler0.selectableChannel());

			assertEquals(size + 1, this.demultiplexer.selector().keys().size());

			handler1.close();

			assertFalse(handler1.isOpen());
			assertFalse(this.demultiplexer.contains(handler1));
			assertFalse(this.demultiplexer.containsInInterestOpsPendings(handler1));
			assertNull(handler1.selectableChannel());
		} catch (Exception ex) {
			fail(ex);
		}
	}

	public final void testSelect() {
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

			this.selectedCount = 0;

			while (this.demultiplexer.select() <= 0);

			assertEquals(1, this.selectedCount);

			assertFalse(this.demultiplexer.containsInInterestOpsPendings(handler));
			assertTrue(this.demultiplexer.containsInSelectedHandlers(handler));

			assertTrue(this.demultiplexer.hasSelectedHandler());

			final EventHandler selectedHandler = this.demultiplexer.pollSelectedHandler();
			assertTrue(selectedHandler == handler);

			assertFalse(this.demultiplexer.containsInSelectedHandlers(handler));
			assertFalse(this.demultiplexer.hasSelectedHandler());

			handler.close();

			assertFalse(handler.isOpen());
			assertFalse(this.demultiplexer.contains(handler));
			assertNull(handler.selectableChannel());
		} catch (Exception ex) {
			fail(ex);
		}
	}

	public final void testSelect2() {
		try {
			final int size = this.demultiplexer.selector().keys().size();

			final SelectableChannel channel0 = this.pipe.sink();
			final EventHandler handler0 = this.demultiplexer.registerChannel(channel0, SelectionKey.OP_WRITE, this, channel0, false);

			assertNotNull(handler0);
			assertTrue(handler0.isOpen());
			assertTrue(this.demultiplexer.contains(handler0));
			assertTrue(this.demultiplexer.containsInInterestOpsPendings(handler0));
			assertTrue(channel0 == handler0.selectableChannel());

			assertEquals(size + 1, this.demultiplexer.selector().keys().size());

			final SelectableChannel channel1 = this.pipe.source();
			final EventHandler handler1 = this.demultiplexer.registerChannel(channel1, SelectionKey.OP_READ, this, channel1, false);

			assertNotNull(handler1);
			assertTrue(handler1.isOpen());
			assertTrue(this.demultiplexer.contains(handler1));
			assertTrue(this.demultiplexer.containsInInterestOpsPendings(handler1));
			assertTrue(channel1 == handler1.selectableChannel());

			assertEquals(size + 2, this.demultiplexer.selector().keys().size());

			// ---------------------------------------------
			this.selectedCount = 0;

			while (this.demultiplexer.select() <= 0);

			assertEquals(1, this.selectedCount);

			assertFalse(this.demultiplexer.containsInInterestOpsPendings(handler0));
			assertTrue(this.demultiplexer.containsInSelectedHandlers(handler0));

			assertFalse(this.demultiplexer.containsInInterestOpsPendings(handler1));
			assertFalse(this.demultiplexer.containsInSelectedHandlers(handler1));

			assertTrue(this.demultiplexer.hasSelectedHandler());

			EventHandler selectedHandler = this.demultiplexer.pollSelectedHandler();

			assertTrue(selectedHandler == handler0);

			assertFalse(this.demultiplexer.containsInSelectedHandlers(handler0));
			assertFalse(this.demultiplexer.hasSelectedHandler());

			final ByteBuffer buffer = ByteBuffer.allocate(8);
			buffer.put((byte)0);
			buffer.flip();

			this.pipe.sink().write(buffer);

			// ---------------------------------------------
			this.selectedCount = 0;

			while (this.demultiplexer.select() <= 0);

			assertEquals(1, this.selectedCount);

			assertFalse(this.demultiplexer.containsInInterestOpsPendings(handler0));
			assertFalse(this.demultiplexer.containsInSelectedHandlers(handler0));

			assertFalse(this.demultiplexer.containsInInterestOpsPendings(handler1));
			assertTrue(this.demultiplexer.containsInSelectedHandlers(handler1));

			assertTrue(this.demultiplexer.hasSelectedHandler());

			selectedHandler = this.demultiplexer.pollSelectedHandler();

			assertTrue(selectedHandler == handler1);

			assertFalse(this.demultiplexer.containsInSelectedHandlers(handler1));
			assertFalse(this.demultiplexer.hasSelectedHandler());

			// ---------------------------------------------
			handler0.close();
			assertFalse(handler0.isOpen());
			assertFalse(this.demultiplexer.contains(handler0));
			assertNull(handler0.selectableChannel());

			handler1.close();
			assertFalse(handler1.isOpen());
			assertFalse(this.demultiplexer.contains(handler1));
			assertNull(handler1.selectableChannel());
		} catch (Exception ex) {
			fail(ex);
		}
	}

	public final void testRunnables() {
		assertFalse(this.demultiplexer.hasRunnable());
		assertNull(this.demultiplexer.pollRunnable());

		final EventHandler.ActiveNode node1 = new Handler(null).activeNode();
		assertFalse(node1.isQueued());

		this.demultiplexer.putRunnable(node1, true);
		assertTrue(node1.isQueued());

		assertTrue(this.demultiplexer.hasRunnable());

		final EventHandler.ActiveNode node2 = new Handler(null).activeNode();
		assertFalse(node2.isQueued());

		this.demultiplexer.putRunnable(node2, true);
		assertTrue(node2.isQueued());

		assertTrue(this.demultiplexer.hasRunnable());
		assertTrue(node1 == this.demultiplexer.pollRunnable());
		assertFalse(node1.isQueued());

		assertTrue(this.demultiplexer.hasRunnable());
		assertTrue(node2 == this.demultiplexer.pollRunnable());
		assertFalse(node2.isQueued());

		assertFalse(this.demultiplexer.hasRunnable());
	}

	public final void testShuttingDown() {
		try {
			final SelectableChannel channel0 = this.pipe.sink();
			final EventHandler handler0 = this.demultiplexer.registerChannel(channel0, SelectionKey.OP_WRITE, this, channel0, false);

			assertNotNull(handler0);
			assertTrue(handler0.isOpen());
			assertTrue(this.demultiplexer.contains(handler0));
			assertTrue(this.demultiplexer.containsInInterestOpsPendings(handler0));

			final SelectableChannel channel1 = this.pipe.source();
			final EventHandler handler1 = this.demultiplexer.registerChannel(channel1, SelectionKey.OP_READ, this, channel1, false);

			assertNotNull(handler1);
			assertTrue(handler1.isOpen());
			assertTrue(this.demultiplexer.contains(handler1));
			assertTrue(this.demultiplexer.containsInInterestOpsPendings(handler1));


			assertFalse(this.demultiplexer.hasRunnable());
			assertNull(this.demultiplexer.pollRunnable());


			this.cancelledCount = 0;
			this.closedCount = 0;

			this.demultiplexer.shuttingDown();

			assertEquals(2, this.cancelledCount);
			assertEquals(0, this.closedCount);

			assertFalse(handler0.isOpen());
			assertTrue(this.demultiplexer.contains(handler0));
			assertFalse(this.demultiplexer.containsInInterestOpsPendings(handler0));

			assertFalse(handler1.isOpen());
			assertTrue(this.demultiplexer.contains(handler1));
			assertFalse(this.demultiplexer.containsInInterestOpsPendings(handler1));


			assertTrue(this.demultiplexer.hasRunnable());

			RunnableQueueNode runnableNode = this.demultiplexer.pollRunnable();
			assertNotNull(runnableNode);

			runnableNode.getRunnable().run();

			runnableNode = this.demultiplexer.pollRunnable();
			assertNotNull(runnableNode);

			runnableNode.getRunnable().run();

			assertEquals(2, this.closedCount);

			assertFalse(this.demultiplexer.contains(handler0));
			assertFalse(this.demultiplexer.contains(handler1));

		} catch (Exception ex) {
			fail(ex);
		}
	}

	public final void testClear() {
		try {
			final SelectableChannel channel0 = this.pipe.sink();
			final EventHandler handler0 = this.demultiplexer.registerChannel(channel0, SelectionKey.OP_WRITE, this, channel0, false);

			assertNotNull(handler0);
			assertTrue(handler0.isOpen());
			assertTrue(this.demultiplexer.contains(handler0));
			assertTrue(this.demultiplexer.containsInInterestOpsPendings(handler0));

			final SelectableChannel channel1 = this.pipe.source();
			final EventHandler handler1 = this.demultiplexer.registerChannel(channel1, SelectionKey.OP_READ, this, channel1, false);

			assertNotNull(handler1);
			assertTrue(handler1.isOpen());
			assertTrue(this.demultiplexer.contains(handler1));
			assertTrue(this.demultiplexer.containsInInterestOpsPendings(handler1));


			this.closedCount = 0;

			this.demultiplexer.clear();

			assertEquals(2, this.closedCount);

			assertFalse(handler0.isOpen());
			assertFalse(this.demultiplexer.contains(handler0));

			assertFalse(handler1.isOpen());
			assertFalse(this.demultiplexer.contains(handler1));

		} catch (Exception ex) {
			fail(ex);
		}
	}

}
