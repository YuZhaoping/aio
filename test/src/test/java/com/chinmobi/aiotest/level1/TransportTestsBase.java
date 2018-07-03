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
import java.util.concurrent.TimeUnit;

import com.chinmobi.aio.AIOAcceptor;
import com.chinmobi.aio.AIOClosedSessionException;
import com.chinmobi.aio.AIOConfiguration;
import com.chinmobi.aio.AIOFuture;
import com.chinmobi.aio.AIOFutureCallback;
import com.chinmobi.aio.AIOServiceHandler;
import com.chinmobi.aio.AIOSession;
import com.chinmobi.aio.act.AIOActDirection;
import com.chinmobi.aio.act.AIOActEntry;
import com.chinmobi.aio.act.AIOInputActResult;
import com.chinmobi.aio.act.AIOInputActStrategy;
import com.chinmobi.aio.act.AIOOutputActResult;
import com.chinmobi.aio.act.AIOReadableActEntry;
import com.chinmobi.aio.act.AIOWritableActEntry;
import com.chinmobi.aio.act.entry.AIOBufferEntryBase;
import com.chinmobi.aio.act.entry.AIODatagramEntryBase;
import com.chinmobi.aio.impl.nio.Reactor;
import com.chinmobi.app.action.ActionContext;
import com.chinmobi.logging.Logger;
import com.chinmobi.testapp.BaseTestAction;

/**
 * @author <a href="mailto:yuzhaoping1970@gmail.com">Zhaoping Yu</a>
 *
 */
public class TransportTestsBase extends BaseTestAction {

	private static final int MAX_TRANSFER_SIZE = 32;


	protected static final class ActEntry extends AIOBufferEntryBase {

		public ActEntry(final int size) {
			super(size);
		}

	}

	protected static final class OutputHelper implements AIOFutureCallback<AIOOutputActResult> {

		private final AIOSession session;

		private final ActEntry actEntry;

		volatile long remainingCount;
		private int doneStatus;

		volatile Throwable cause;

		volatile long elapsedTime;


		OutputHelper(final AIOSession session) {
			this.session = session;

			this.actEntry = new ActEntry(MAX_TRANSFER_SIZE);

			this.doneStatus = 0;
			this.remainingCount = 0;
			this.cause = null;
		}


		public final boolean send(final long count) throws AIOClosedSessionException {
			long remaining = count;
			synchronized (this) {
				if (this.doneStatus == 0 && this.remainingCount <= 0) {
					this.remainingCount = remaining;

					if (remaining > MAX_TRANSFER_SIZE) {
						remaining = MAX_TRANSFER_SIZE;
					}
				} else {
					return false;
				}
			}

			this.actEntry.clear();
			this.actEntry.toOutput((int)remaining).byteBuffer().position((int)remaining);

			final AIOReadableActEntry inputEntry = this.actEntry.toInput();

			this.elapsedTime = System.currentTimeMillis();

			this.session.outputActor().write(this.session.id(),
					inputEntry, this, 10000, TimeUnit.MILLISECONDS, null);

			return true;
		}

		public final void waitForDone() throws InterruptedException {
			synchronized (this) {
				while (this.doneStatus == 0 && this.remainingCount > 0) {
					wait();
				}
			}
		}

		public final long waitForDone(final long timeout) throws InterruptedException {

			final long startTime = (timeout <= 0) ? 0 : System.currentTimeMillis();
			long waitTime = timeout;

			synchronized (this) {
				while (this.doneStatus == 0 && this.remainingCount > 0) {
					if (waitTime <= 0) {
						break;
					}

					wait(waitTime);
					waitTime = timeout - (System.currentTimeMillis() - startTime);
				}

				return this.remainingCount;
			}
		}


		public final void initiate(final AIOFuture<AIOOutputActResult> future,
				final Object attachment, final AIOOutputActResult result) {
		}

		public final void accomplished(final AIOFuture<AIOOutputActResult> future,
				final Object attachment, final AIOOutputActResult result) {
			long remaining = 0;
			synchronized (this) {
				this.remainingCount -= result.completedCount();
				remaining = this.remainingCount;

				if (remaining <= 0) {
					notify();
				}
			}
			future.release();

			if (remaining <= 0) {
				this.elapsedTime = System.currentTimeMillis() - this.elapsedTime;
				return;
			}

			if (remaining > MAX_TRANSFER_SIZE) {
				remaining = MAX_TRANSFER_SIZE;
			}

			this.actEntry.clear();
			this.actEntry.toOutput((int)remaining).byteBuffer().position((int)remaining);

			final AIOReadableActEntry inputEntry = this.actEntry.toInput();

			try {
				this.session.outputActor().write(this.session.id(),
						inputEntry, this, 10000, TimeUnit.MILLISECONDS, null);
			} catch (Throwable ex) {
				synchronized (this) {
					this.doneStatus = -1;
					this.cause = ex;
					notify();
				}
			}
		}

		public final void timeout(final AIOFuture<AIOOutputActResult> future,
				final Object attachment, final AIOOutputActResult result) {
			synchronized (this) {
				this.remainingCount -= result.completedCount();
				this.doneStatus = -1;
				notify();
			}
			future.release();

			this.session.close(true);
		}

		public final void failed(final AIOFuture<AIOOutputActResult> future,
				final Object attachment, final AIOOutputActResult result, final Throwable cause) {
			synchronized (this) {
				this.remainingCount -= result.completedCount();
				this.doneStatus = -1;
				this.cause = cause;
				notify();
			}
			future.release();

			this.session.close(true);
		}

		public final void cancelled(final AIOFuture<AIOOutputActResult> future,
				final Object attachment, final AIOOutputActResult result) {
			synchronized (this) {
				this.remainingCount -= result.completedCount();
				this.doneStatus = 1;
				notify();
			}
			future.release();

			this.session.close(true);
		}

	}

	protected static class InputHelperBase
		implements AIOFutureCallback<AIOInputActResult>, AIOInputActStrategy {

		protected final AIOSession session;

		protected volatile long specificCount;

		volatile long receivedCount;
		protected volatile int doneStatus;

		protected volatile ActEntry currentActEntry;

		volatile Throwable cause;


		protected InputHelperBase(final AIOSession session) {
			this.session = session;

			this.specificCount = Long.MAX_VALUE;
			this.receivedCount = 0;
			this.doneStatus = 0;
			this.cause = null;

			this.currentActEntry = null;
		}


		public final void setSpecificCount(final long count) {
			this.specificCount = count;
		}

		public final void receive() throws AIOClosedSessionException {
			if (this.currentActEntry == null) {
				this.currentActEntry = new ActEntry(MAX_TRANSFER_SIZE);
			}

			this.currentActEntry.clear();

			final AIOWritableActEntry ouputEntry = this.currentActEntry.toOutput(MAX_TRANSFER_SIZE);

			this.session.inputActor().read(this.session.id(),
					ouputEntry, this, 10000, TimeUnit.MILLISECONDS,
					this, null);
		}

		public final void waitForDone(final long count) throws InterruptedException {
			this.specificCount = count;
			synchronized (this) {
				while (this.doneStatus == 0 && this.receivedCount < count) {
					wait();
				}
			}
		}


		public final void initiate(final AIOFuture<AIOInputActResult> future,
				final Object attachment, final AIOInputActResult result) {
		}

		public void accomplished(final AIOFuture<AIOInputActResult> future,
				final Object attachment, final AIOInputActResult result) {
			synchronized (this) {
				this.receivedCount += result.completedCount();
				if (result.endOfInput()) {
					this.doneStatus = 1;
				}
				notify();
			}
			future.release();
		}

		public final void timeout(final AIOFuture<AIOInputActResult> future,
				final Object attachment, final AIOInputActResult result) {
			synchronized (this) {
				this.receivedCount += result.completedCount();
				this.doneStatus = -1;
				notify();
			}
			future.release();
		}

		public final void failed(final AIOFuture<AIOInputActResult> future,
				final Object attachment, final AIOInputActResult result, final Throwable cause) {
			synchronized (this) {
				this.receivedCount += result.completedCount();
				this.doneStatus = -1;
				this.cause = cause;
				notify();
			}
			future.release();
		}

		public final void cancelled(final AIOFuture<AIOInputActResult> future,
				final Object attachment, final AIOInputActResult result) {
			synchronized (this) {
				this.receivedCount += result.completedCount();
				this.doneStatus = -2;
				notify();
			}
			future.release();
		}


		public AIOActDirection determineInputActReads(final AIOActEntry entry, final long origPosition,
				final long totalReadCount, final long readCount) {
			if ((this.receivedCount + totalReadCount) >= this.specificCount) {
				return AIOActDirection.Terminate;
			}
			return null;
		}

	}

	protected static final class ServerInputHelper extends InputHelperBase {

		private final ServerOutputHelper outputHelper;

		volatile long sentCount;


		ServerInputHelper(final AIOSession session) {
			super(session);
			this.outputHelper = new ServerOutputHelper(this);
			this.sentCount = 0;
		}

		ServerInputHelper(final ServiceHandler serviceHandler) {
			this(serviceHandler.session);
			serviceHandler.outputActCallback = this.outputHelper;
		}


		@Override
		public final void accomplished(final AIOFuture<AIOInputActResult> future,
				final Object attachment, final AIOInputActResult result) {

			super.accomplished(future, attachment, result);

			final AIOReadableActEntry inputEntry = this.currentActEntry.toInput();

			if (inputEntry.count() == 0) {
				if (this.doneStatus != 0) {
					this.session.close();
				} else {
					try {
						receive();
					} catch (Throwable ex) {
						synchronized (this) {
							this.doneStatus = -1;
							this.cause = ex;
							notify();
						}
					}
				}
				return;
			}

			try {
				this.session.outputActor().write(this.session.id(),
						inputEntry, this.outputHelper, 10000, TimeUnit.MILLISECONDS, null);

				this.currentActEntry = null;

				if (this.doneStatus == 0) {
					receive();
				}
			} catch (Throwable ex) {
				synchronized (this) {
					this.doneStatus = -1;
					this.cause = ex;
					notify();
				}
			}
		}

		final void outputInitiate(final AIOFuture<AIOOutputActResult> future,
				final Object attachment, final AIOOutputActResult result) {
		}

		final void outputAccomplished(final AIOFuture<AIOOutputActResult> future,
				final Object attachment, final AIOOutputActResult result) {
			synchronized (this) {
				this.sentCount += result.completedCount();
				notify();
			}
			future.release();
		}

		final void outputTimeout(final AIOFuture<AIOOutputActResult> future,
				final Object attachment, final AIOOutputActResult result) {
			synchronized (this) {
				this.sentCount += result.completedCount();
				this.doneStatus = -1;
				notify();
			}
			future.release();

			this.session.close(true);
		}

		final void outputFailed(final AIOFuture<AIOOutputActResult> future,
				final Object attachment, final AIOOutputActResult result, final Throwable cause) {
			synchronized (this) {
				this.sentCount += result.completedCount();
				this.doneStatus = -1;
				this.cause = cause;
				notify();
			}
			future.release();

			this.session.close(true);
		}

		final void outputCancelled(final AIOFuture<AIOOutputActResult> future,
				final Object attachment, final AIOOutputActResult result) {
			synchronized (this) {
				this.sentCount += result.completedCount();
				this.doneStatus = 1;
				notify();
			}
			future.release();

			this.session.close(true);
		}

	}

	private static final class ServerOutputHelper implements AIOFutureCallback<AIOOutputActResult> {

		private final ServerInputHelper inputHelper;

		ServerOutputHelper(final ServerInputHelper inputHelper) {
			this.inputHelper = inputHelper;
		}

		public final void initiate(final AIOFuture<AIOOutputActResult> future,
				final Object attachment, final AIOOutputActResult result) {
			this.inputHelper.outputInitiate(future, attachment, result);
		}

		public final void accomplished(final AIOFuture<AIOOutputActResult> future,
				final Object attachment, final AIOOutputActResult result) {
			this.inputHelper.outputAccomplished(future, attachment, result);
		}

		public final void timeout(final AIOFuture<AIOOutputActResult> future,
				final Object attachment, final AIOOutputActResult result) {
			this.inputHelper.outputTimeout(future, attachment, result);
		}

		public final void failed(final AIOFuture<AIOOutputActResult> future,
				final Object attachment, final AIOOutputActResult result, final Throwable cause) {
			this.inputHelper.outputFailed(future, attachment, result, cause);
		}

		public final void cancelled(final AIOFuture<AIOOutputActResult> future,
				final Object attachment, final AIOOutputActResult result) {
			this.inputHelper.outputCancelled(future, attachment, result);
		}

	}

	protected static final class ClientInputHelper extends InputHelperBase {

		ClientInputHelper(final AIOSession session) {
			super(session);
		}

		@Override
		public final void accomplished(final AIOFuture<AIOInputActResult> future,
				final Object attachment, final AIOInputActResult result) {

			super.accomplished(future, attachment, result);

			if (this.doneStatus != 0) {
				this.session.close();
				return;
			}

			try {
				receive();
			} catch (Throwable ex) {
				synchronized (this) {
					this.doneStatus = -1;
					this.cause = ex;
					notify();
				}
			}
		}

	}


	protected static final class ServiceHandler implements AIOServiceHandler, AIOServiceHandler.Factory {

		private final String name;

		volatile AIOSession session;

		volatile int openedCount;
		volatile int closedCount;
		volatile Throwable cause;

		private volatile AIOFutureCallback<AIOOutputActResult> outputActCallback;


		ServiceHandler(final String name) {
			this.name = name;
		}

		final void reset() {
			this.session = null;
			this.openedCount = 0;
			this.closedCount = 0;
			this.cause = null;
			this.outputActCallback = null;
		}

		public final void handleAIOSessionOpened(final AIOSession session) throws AIOClosedSessionException {
			this.session = session;
			session.setTimeout(1000, TimeUnit.MILLISECONDS);
			synchronized (this) {
				++this.openedCount;

				notify();
			}
		}

		public final boolean handleAIOSessionInputReady(final AIOSession session)
				throws IOException, AIOClosedSessionException {
			if (session.datagramChannel() != null) {
				final AIODatagramEntryBase entry = new AIODatagramEntryBase(0);
				if (entry.receiveFrom(session.datagramChannel()) > 0) {
					session.outputActor().write(session.id(),
							entry.toInput(),
							this.outputActCallback, 10000, TimeUnit.MILLISECONDS, null);
				}
				return true;
			}
			return false;
		}

		public final boolean handleAIOSessionOutputReady(final AIOSession session)
				throws IOException, AIOClosedSessionException {
			return false;
		}

		public final void handleAIOSessionTimeout(final AIOSession session) throws AIOClosedSessionException {
			session.close();
		}

		public final void handleAIOSessionClosed(final AIOSession session, final Throwable cause) {
			this.session = null;
			synchronized (this) {
				++this.closedCount;
				this.cause = cause;

				notify();
			}
		}

		final void waitForOpened() throws InterruptedException {
			synchronized (this) {
				while (this.openedCount == 0 && this.closedCount == 0) {
					wait();
				}
			}
		}

		final void waitForClosed() throws InterruptedException {
			synchronized (this) {
				while (this.closedCount == 0) {
					wait();
				}
			}
		}

		public final AIOServiceHandler createAIOServiceHandler(final AIOSession session) {
			return this;
		}

		@Override
		public final String toString() {
			return this.name;
		}

	}


	protected static final class AcceptorObserver implements AIOAcceptor.Observer {

		volatile int startedCount;
		volatile int acceptingCount;
		volatile int stoppedCount;

		volatile String msg;
		volatile Throwable cause;


		AcceptorObserver() {
		}


		final void reset() {
			this.startedCount = 0;
			this.acceptingCount = 0;
			this.stoppedCount = 0;
			this.msg = null;
			this.cause = null;
		}

		public final void onAIOAcceptorStarted(final AIOAcceptor acceptor) {
			synchronized (this) {
				this.startedCount++;
				notify();
			}
		}

		public final void onAIOAcceptorAccepting(final AIOAcceptor acceptor) {
			synchronized (this) {
				this.acceptingCount++;
				notify();
			}
		}

		public final void onAIOAcceptorStopped(final AIOAcceptor acceptor,
				final Object msgObj, final Throwable cause) {
			synchronized (this) {
				this.stoppedCount++;
				this.msg = msgObj.toString();
				this.cause = cause;
				notify();
			}
		}

		final void waitForStarted() throws InterruptedException {
			synchronized (this) {
				while (this.startedCount == 0 && this.stoppedCount == 0) {
					wait();
				}
			}
		}

		final void waitForAccepting() throws InterruptedException {
			synchronized (this) {
				while (this.acceptingCount == 0 && this.stoppedCount == 0) {
					wait();
				}
			}
		}

		final void waitForStopped() throws InterruptedException {
			synchronized (this) {
				while (this.stoppedCount == 0) {
					wait();
				}
			}
		}

	}


	protected volatile String currentMethodName;

	protected volatile Reactor reactor;

	protected volatile ServiceHandler serverHandler;
	protected volatile ServiceHandler clientHandler;

	protected volatile AcceptorObserver acceptorObserver;

	private volatile int handlerId;

	private volatile Logger.Level origLevel;


	protected TransportTestsBase() {
		super();
	}


	@Override
	public void init(final ActionContext context) {
		super.init(context);
		this.handlerId = 0;
		this.origLevel = null;
	}

	@Override
	public void destroy() {
		tearDownReactor();
		super.destroy();
	}

	@Override
	protected final void setUp(final String methodName) throws Exception {
		this.currentMethodName = methodName;

		++this.handlerId;

		this.serverHandler = new ServiceHandler("Server_" + this.handlerId);
		this.clientHandler = new ServiceHandler("Client_" + this.handlerId);

		this.serverHandler.reset();
		this.clientHandler.reset();

		this.acceptorObserver = new AcceptorObserver();
		this.acceptorObserver.reset();

		doSetUp();
	}

	@Override
	protected final void tearDown() throws Exception {
		doTearDown();

		this.serverHandler = null;
		this.clientHandler = null;

		this.acceptorObserver = null;
	}


	protected void doSetUp() throws Exception {
	}

	protected void doTearDown() throws Exception {
	}

	protected final void setUpReactor(final int coreThreadPoolSize, final boolean isInterestOpsQueueing,
			final boolean enableLog) throws IOException {
		if (this.reactor != null) {
			final AIOConfiguration config = this.reactor.configuration();
			if (config.getCoreThreadPoolSize() != coreThreadPoolSize ||
				config.isInterestOpsQueueing() != isInterestOpsQueueing) {
				tearDownReactor();
			}
		}

		if (this.reactor == null) {
			final AIOConfiguration config = new AIOConfiguration();

			config.setCoreThreadPoolSize(coreThreadPoolSize);
			config.setInterestOpsQueueing(isInterestOpsQueueing);

			try {
				this.reactor = new Reactor(Selector.open(), config);
			} catch (IOException ex) {
				fail(ex);
			}

			if (this.origLevel == null) {
				this.origLevel = this.reactor.logger().getEnabledLevel();
			}
		}

		if (enableLog) {
			this.reactor.logger().enableLevel(Logger.Level.ALL);
		} else {
			this.reactor.logger().enableLevel(this.origLevel);
		}

		//logStep("startReactor...");

		this.reactor.start();
	}

	protected final void tearDownReactor() {
		if (this.reactor != null) {
			this.reactor.logger().enableLevel(this.origLevel);

			//logStep("stopReactor...");

			this.reactor.stop(true);
			this.reactor = null;

			//logStep("reactor.stopped.");
		}
	}

	protected final void logStep(final String step) {
		if (this.reactor != null) {
			this.reactor.logger().enableLevel(Logger.Level.DEBUG);
			if (this.reactor.logger().isDebugEnabled()) {
				final StringBuilder builder = new StringBuilder();
				builder.append("\t==== ");
				builder.append(this.currentMethodName);
				builder.append(' ');
				builder.append(step);
				this.reactor.logger().writeln().writeln(builder.toString()).flush();
			}
			this.reactor.logger().enableLevel(this.origLevel);
		}
	}

	/*
	 * Test methods
	 */


}
