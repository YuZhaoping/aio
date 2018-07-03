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
package com.chinmobi.aiotest.level1;

import java.io.IOException;
import java.nio.channels.Selector;

import com.chinmobi.aio.AIOClosedSessionException;
import com.chinmobi.aio.AIONotActiveException;
import com.chinmobi.aio.AIOServiceHandler;
import com.chinmobi.aio.AIOSession;
import com.chinmobi.aio.impl.nio.Demultiplexer;
import com.chinmobi.aio.impl.nio.RunnableQueueNode;
import com.chinmobi.aio.impl.nio.SessionContext;
import com.chinmobi.aio.impl.util.TimerEntry;
import com.chinmobi.aio.impl.util.TimerEntrySet;
import com.chinmobi.app.action.ActionContext;
import com.chinmobi.logging.LogManager;
import com.chinmobi.testapp.BaseTestAction;

/**
 * @author <a href="mailto:yuzhaoping1970@gmail.com">Zhaoping Yu</a>
 *
 */
public class ConnectorAcceptorTestBase extends BaseTestAction
	implements TimerEntrySet.Observer, Demultiplexer.ActiveChecker, AIOServiceHandler.Factory {

	private static final String TEST_SELECTOR = "test.selector";


	protected static final class ServiceHandler implements AIOServiceHandler {

		final void reset() {

		}

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


	protected TimerEntrySet timerEntries;
	protected Demultiplexer demultiplexer;
	protected SessionContext sessionContext;

	protected final ServiceHandler serviceHandler;


	protected ConnectorAcceptorTestBase() {
		super();
		this.serviceHandler = new ServiceHandler();
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

		this.serviceHandler.reset();

		doSetUp();
	}

	@Override
	protected final void tearDown() throws Exception {
		doTearDown();

		this.demultiplexer.clear();
		this.timerEntries.clear();
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


	protected void doSetUp() throws Exception {
	}

	protected void doTearDown() throws Exception {
	}


	protected final void runAllRunnables() {
		RunnableQueueNode runnableNode = this.demultiplexer.pollRunnable();
		while (runnableNode != null) {
			runnableNode.getRunnable().run();

			runnableNode = this.demultiplexer.pollRunnable();
		}
	}

	/*
	 * Test methods
	 */

}
