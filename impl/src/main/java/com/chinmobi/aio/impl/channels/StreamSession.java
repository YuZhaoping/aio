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
package com.chinmobi.aio.impl.channels;

import java.net.SocketAddress;
import java.nio.channels.DatagramChannel;
import java.nio.channels.GatheringByteChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.ScatteringByteChannel;
import java.nio.channels.WritableByteChannel;
import java.util.concurrent.TimeUnit;

import com.chinmobi.aio.AIOClosedSessionException;
import com.chinmobi.aio.AIOServiceHandler;
import com.chinmobi.aio.AIOSession;
import com.chinmobi.aio.impl.Constants;
import com.chinmobi.aio.impl.act.InputActor;
import com.chinmobi.aio.impl.act.OutputActor;
import com.chinmobi.aio.impl.nio.ExtendedSession;
import com.chinmobi.aio.impl.nio.Session;

/**
 * @author <a href="mailto:yuzhaoping1970@gmail.com">Zhaoping Yu</a>
 *
 */
public final class StreamSession implements ExtendedSession {

	private final Session outputSession;
	private final Session inputSession;

	private AIOServiceHandler serviceHandler;


	StreamSession(final Session outputSession, final Session inputSession) {
		this.outputSession = outputSession;
		this.inputSession = inputSession;
	}


	public final void setServiceHandler(final AIOServiceHandler handler) {
		this.serviceHandler = handler;
	}

	public final void handleAIOSessionOpened(final AIOSession session) throws AIOClosedSessionException {
		final AIOServiceHandler handler = this.serviceHandler;
		if (handler != null) {
			handler.handleAIOSessionOpened(this);
		}
	}

	public final void fail(final Throwable cause) {
		this.outputSession.fail(cause);
		this.inputSession.fail(cause);
	}

	/* (non-Javadoc)
	 * @see com.chinmobi.aio.AIOSession#isOpen()
	 */
	public final boolean isOpen() {
		return this.outputSession.isOpen() && this.inputSession.isOpen();
	}

	/* (non-Javadoc)
	 * @see com.chinmobi.aio.AIOSession#close()
	 */
	public final void close() {
		close(false);
	}

	/* (non-Javadoc)
	 * @see com.chinmobi.aio.AIOSession#close(boolean forceShutdown)
	 */
	public final void close(final boolean forceShutdown) {
		this.outputSession.close(forceShutdown);
		this.inputSession.close(forceShutdown);
	}

	/* (non-Javadoc)
	 * @see com.chinmobi.aio.AIOSession#isInputShutdown()
	 */
	public final boolean isInputShutdown() {
		return this.inputSession.isInputShutdown();
	}

	/* (non-Javadoc)
	 * @see com.chinmobi.aio.AIOSession#isOutputShutdown()
	 */
	public final boolean isOutputShutdown() {
		return this.outputSession.isOutputShutdown();
	}

	/* (non-Javadoc)
	 * @see com.chinmobi.aio.AIOSession#shutdownInput(boolean force)
	 */
	public final boolean shutdownInput(final boolean force) {
		return this.inputSession.shutdownInput(force);
	}

	/* (non-Javadoc)
	 * @see com.chinmobi.aio.AIOSession#shutdownOutput(boolean force)
	 */
	public final boolean shutdownOutput(final boolean force) {
		return this.outputSession.shutdownOutput(force);
	}

	/* (non-Javadoc)
	 * @see com.chinmobi.aio.AIOSession#getServiceHandler()
	 */
	public final AIOServiceHandler getServiceHandler() {
		return this.serviceHandler;
	}

	/* (non-Javadoc)
	 * @see com.chinmobi.aio.AIOSession#setTimeout(long timeout, TimeUnit unit)
	 */
	public final void setTimeout(final long timeout, final TimeUnit unit) {
		this.inputSession.setTimeout(timeout, unit);
	}

	/* (non-Javadoc)
	 * @see com.chinmobi.aio.AIOSession#inputActor()
	 */
	public final InputActor inputActor() {
		return this.inputSession.inputActor();
	}

	/* (non-Javadoc)
	 * @see com.chinmobi.aio.AIOSession#outputActor()
	 */
	public final OutputActor outputActor() {
		return this.outputSession.outputActor();
	}

	/*
	 * (non-Javadoc)
	 * @see com.chinmobi.aio.AIOSession#readableChannel()
	 */
	public final ReadableByteChannel readableChannel() {
		return this.inputSession.readableChannel();
	}

	/* (non-Javadoc)
	 * @see com.chinmobi.aio.AIOSession#scatteringChannel()
	 */
	public final ScatteringByteChannel scatteringChannel() {
		return this.inputSession.scatteringChannel();
	}

	/*
	 * (non-Javadoc)
	 * @see com.chinmobi.aio.AIOSession#writableChannel()
	 */
	public final WritableByteChannel writableChannel() {
		return this.outputSession.writableChannel();
	}

	/* (non-Javadoc)
	 * @see com.chinmobi.aio.AIOSession#gatheringChannel()
	 */
	public final GatheringByteChannel gatheringChannel() {
		return this.outputSession.gatheringChannel();
	}

	/* (non-Javadoc)
	 * @see com.chinmobi.aio.AIOSession#datagramChannel()
	 */
	public final DatagramChannel datagramChannel() {
		return null;
	}

	/* (non-Javadoc)
	 * @see com.chinmobi.aio.AIOSession#getInterestEvents()
	 */
	public final int getInterestEvents() {
		return (this.inputSession.getInterestEvents() | this.outputSession.getInterestEvents());
	}

	/* (non-Javadoc)
	 * @see com.chinmobi.aio.AIOSession#setInputEvent()
	 */
	public final void setInputEvent() {
		this.inputSession.setInputEvent();
	}

	/* (non-Javadoc)
	 * @see com.chinmobi.aio.AIOSession#clearInputEvent()
	 */
	public final void clearInputEvent() {
		this.inputSession.clearInputEvent();
	}

	/* (non-Javadoc)
	 * @see com.chinmobi.aio.AIOSession#setOutputEvent()
	 */
	public final void setOutputEvent() {
		this.outputSession.setOutputEvent();
	}

	/* (non-Javadoc)
	 * @see com.chinmobi.aio.AIOSession#clearOutputEvent()
	 */
	public final void clearOutputEvent() {
		this.outputSession.clearOutputEvent();
	}

	/* (non-Javadoc)
	 * @see com.chinmobi.aio.AIOSession#getRemoteSocketAddress()
	 */
	public final SocketAddress getRemoteSocketAddress() {
		return null;
	}

	/* (non-Javadoc)
	 * @see com.chinmobi.aio.AIOSession#getLocalSocketAddress()
	 */
	public final SocketAddress getLocalSocketAddress() {
		return null;
	}

	/* (non-Javadoc)
	 * @see com.chinmobi.aio.AIOSession#id()
	 */
	public final int id() {
		return this.inputSession.id();
	}

	@Override
	public final String toString() {
		final StringBuilder builder = new StringBuilder();

		builder.append(this.inputSession.toString());
		builder.append(Constants.CRLF).append('\t');
		builder.append(this.outputSession.toString());

		return builder.toString();
	}

}
