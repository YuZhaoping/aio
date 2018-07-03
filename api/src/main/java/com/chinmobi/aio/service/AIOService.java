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
package com.chinmobi.aio.service;

import java.util.concurrent.TimeUnit;

import com.chinmobi.aio.AIOClosedSessionException;
import com.chinmobi.aio.AIOFuture;
import com.chinmobi.aio.AIOReactor;
import com.chinmobi.aio.AIOSession;
import com.chinmobi.aio.act.AIOInputActResult;
import com.chinmobi.aio.act.AIOInputActStrategy;
import com.chinmobi.aio.act.AIOInputLegacy;
import com.chinmobi.aio.act.AIOOutputActResult;
import com.chinmobi.aio.act.AIOReadableActEntry;
import com.chinmobi.aio.act.AIOWritableActEntry;

/**
 * @author <a href="mailto:yuzhaoping1970@gmail.com">Zhaoping Yu</a>
 *
 */
public interface AIOService {

	public interface ReadCallback {
		public void aioReadInitiate(AIOService service, AIOWritableActEntry target);
		public void aioReadAccomplished(AIOService service, AIOWritableActEntry target, long position, long completedCount, boolean endOfInput);
		public void aioReadTimeout(AIOService service, AIOWritableActEntry target, long position, long completedCount);
		public void aioReadFailed(AIOService service, AIOWritableActEntry target, long position, long completedCount, Throwable cause);
		public void aioReadCancelled(AIOService service, AIOWritableActEntry target, long position, long completedCount);
	}

	public interface WriteCallback {
		public void aioWriteInitiate(AIOService service, AIOReadableActEntry source);
		public void aioWriteAccomplished(AIOService service, AIOReadableActEntry source, long position, long completedCount);
		public void aioWriteTimeout(AIOService service, AIOReadableActEntry source, long position, long completedCount);
		public void aioWriteFailed(AIOService service, AIOReadableActEntry source, long position, long completedCount, Throwable cause);
		public void aioWriteCancelled(AIOService service, AIOReadableActEntry source, long position, long completedCount);
	}


	public boolean isOpen();

	public void close();

	public AIOSession session();

	/**
	 *
	 * @param timeout
	 * @param unit
	 */
	public void setSessionTimeout(long timeout, TimeUnit unit);

	public AIOServiceCallback getServiceCallback();

	/**
	 *
	 * @param target
	 * @param callback
	 * @param strategy
	 * @return
	 * @throws AIOClosedSessionException, IllegalArgumentException, IllegalStateException
	 * @throws AIONotActiveException
	 */
	public AIOFuture<AIOInputActResult> read(AIOWritableActEntry target,
			ReadCallback callback, AIOInputActStrategy strategy, long timeout, TimeUnit unit)
					throws AIOClosedSessionException;

	/**
	 *
	 * @param legacy
	 * @param endOfInput
	 * @return the top index of this input legacy.
	 * @throws AIOClosedSessionException, IllegalArgumentException
	 */
	public int pushInputLegacy(AIOInputLegacy legacy, boolean endOfInput) throws AIOClosedSessionException;

	/**
	 *
	 * @return -1 if has not input legacy.
	 * @throws AIOClosedSessionException
	 */
	public int topIndexOfInputLegacy() throws AIOClosedSessionException;

	/**
	 *
	 * @return null if has not input legacy.
	 * @throws AIOClosedSessionException
	 */
	public AIOInputLegacy popInputLegacy() throws AIOClosedSessionException;

	/**
	 *
	 * @param source
	 * @param callback
	 * @return
	 * @throws AIOClosedSessionException, IllegalArgumentException, IllegalStateException
	 * @throws AIONotActiveException
	 */
	public AIOFuture<AIOOutputActResult> write(AIOReadableActEntry source,
			WriteCallback callback, long timeout, TimeUnit unit) throws AIOClosedSessionException;

	public AIOReactor reactor();

}
