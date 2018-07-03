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

import java.util.concurrent.TimeUnit;

import com.chinmobi.aio.facade.AIOConnectionCreator;
import com.chinmobi.aio.facade.AIORuntimeException;
import com.chinmobi.aio.pool.AIOConnectionPool;
import com.chinmobi.aio.service.AIOConnection;

/**
 * @author <a href="mailto:yuzhaoping1970@gmail.com">Zhaoping Yu</a>
 *
 */
public final class ConnectionPool implements AIOConnectionPool {

	private final AIOConnectionCreator connectionCreator;

	private final SchemePool schemePool;

	private volatile long timeToLive;


	ConnectionPool(final AIOConnectionCreator connectionCreator) {
		this.connectionCreator = connectionCreator;

		this.schemePool = new SchemePool(this);
	}


	/* (non-Javadoc)
	 * @see com.chinmobi.aio.pool.AIOConnectionPool#leaseConnection(...)
	 */
	public final AIOConnection leaseConnection(final String schemeName, final String hostName, final int port)
			throws AIORuntimeException {

		final PoolEntryGroup group = this.schemePool.getPoolEntryGroup(schemeName);

		AIOConnection connection = group.leaseConnection(hostName, port);

		if (connection == null) {
			final PoolEntry entry = new PoolEntry(group);

			connection = this.connectionCreator.createConnection(schemeName, (AIOConnection.ReleaseCallback)entry);
			entry.setConnection(connection);
			connection.remoteEndpoint().set(hostName, port);
		}

		return connection;
	}

	/* (non-Javadoc)
	 * @see com.chinmobi.aio.pool.AIOConnectionPool#setIdleConnectionTimeToLive(...)
	 */
	public final void setIdleConnectionTimeToLive(final long timeToLive, final TimeUnit unit) {
		this.timeToLive = unit.toMillis(timeToLive);
	}

	/*
	 * (non-Javadoc)
	 * @see com.chinmobi.aio.pool.AIOConnectionPool#clear()
	 */
	public final void clear() {
		this.schemePool.clear();
	}


	final long getIdleConnectionTimeToLive() {
		return this.timeToLive;
	}

}
