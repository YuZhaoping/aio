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
package com.chinmobi.aio.impl.pool;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import com.chinmobi.aio.AIOClosedSessionException;
import com.chinmobi.aio.AIOSession;
import com.chinmobi.aio.service.AIOConnection;
import com.chinmobi.aio.service.AIOService;
import com.chinmobi.aio.service.AIOServiceCallback;

/**
 * @author <a href="mailto:yuzhaoping1970@gmail.com">Zhaoping Yu</a>
 *
 */
public final class PoolEntry implements AIOConnection.ReleaseCallback, AIOServiceCallback {

	PoolEntry next;

	private final PoolEntryGroup group;

	private final AtomicBoolean isFree;

	private AIOConnection connection;


	PoolEntry(final PoolEntryGroup group) {
		this.group = group;
		this.isFree = new AtomicBoolean(false);
	}


	final void setConnection(final AIOConnection connection) {
		this.connection = connection;
	}

	final AIOConnection getConnection() {
		return this.connection;
	}

	final void setBusy() {
		this.isFree.set(false);
	}

	private final boolean trySetFree() {
		return this.isFree.compareAndSet(false, true);
	}

	private static final boolean setTimeToLive(final AIOConnection connection, final long timeToLive) {
		final AIOSession session = connection.session();
		if (session != null && session.isOpen()) {
			session.setTimeout(timeToLive, TimeUnit.MILLISECONDS);
			session.setInputEvent();
			return true;
		}
		return false;
	}


	/* (non-Javadoc)
	 * @see com.chinmobi.aio.act.AIOConnection.ReleaseCallback#aioConnectionReleased(...)
	 */
	public final void aioConnectionReleased(final AIOConnection connection) {
		if (!trySetFree()) {
			return;
		}

		connection.setServiceCallback(this);
		final boolean isOpen = setTimeToLive(connection, this.group.pool().getIdleConnectionTimeToLive());
		this.group.releaseConnection(this, connection, isOpen);
	}

	/* (non-Javadoc)
	 * @see com.chinmobi.aio.act.AIOConnection.ReleaseCallback#aioConnectionClosed(...)
	 */
	public final void aioConnectionClosed(final AIOConnection connection) {
		this.group.closedConnection(this, connection);
	}


	/*
	 * (non-Javadoc)
	 * @see com.chinmobi.aio.service.AIOServiceCallback#aioServiceOpened(AIOService service)
	 */
	public final void aioServiceOpened(final AIOService service)
			throws AIOClosedSessionException {
		// Nothing to do.
	}

	/*
	 * (non-Javadoc)
	 * @see com.chinmobi.aio.service.AIOServiceCallback#aioServiceInputReady(AIOService service)
	 */
	public final boolean aioServiceInputReady(final AIOService service)
			throws IOException, AIOClosedSessionException {
		// Nothing to do.
		return false;
	}

	/*
	 * (non-Javadoc)
	 * @see com.chinmobi.aio.service.AIOServiceCallback#aioServiceOutputReady(AIOService service)
	 */
	public final boolean aioServiceOutputReady(final AIOService service)
			throws IOException, AIOClosedSessionException {
		// Nothing to do.
		return false;
	}

	/*
	 * (non-Javadoc)
	 * @see com.chinmobi.aio.service.AIOServiceCallback#aioServiceTimeout(AIOService service)
	 */
	public final void aioServiceTimeout(final AIOService service)
			throws AIOClosedSessionException {
		service.close();
	}

	/*
	 * (non-Javadoc)
	 * @see com.chinmobi.aio.service.AIOServiceCallback#aioServiceClosed(AIOService service, Object msgObj, Throwable cause)
	 */
	public final void aioServiceClosed(final AIOService service,
			final Object msgObj, final Throwable cause) {
		// Nothing to do.
	}

	@Override
	public final String toString() {
		return "PoolEntry";
	}

}
