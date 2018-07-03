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

import com.chinmobi.aio.AIOInetEndpoint;
import com.chinmobi.aio.AIONotActiveException;
import com.chinmobi.aio.AIOReactor;
import com.chinmobi.aio.scheme.AIOTransportScheme;

/**
 * @author <a href="mailto:yuzhaoping1970@gmail.com">Zhaoping Yu</a>
 *
 */
public interface AIOConnectionAcceptor {

	public interface Observer {
		public void aioAcceptorStarted(AIOConnectionAcceptor acceptor);
		public void aioAcceptorAccepting(AIOConnectionAcceptor acceptor);
		public void aioAcceptorStopped(AIOConnectionAcceptor acceptor, Object msgObj, Throwable cause);
	}

	public void setObserver(Observer observer);
	public Observer getObserver();

	public AIOTransportScheme transportScheme();

	public AIOInetEndpoint localEndpoint();

	public void start(AIOServiceCallback.Factory callbackFactory) throws AIONotActiveException;

	public void stop();

	public boolean isAccepting();
	public boolean waitForAccepting() throws InterruptedException;
	public void waitForStopped() throws InterruptedException;

	public AIOServiceCallback.Factory getServiceCallbackFactory();

	public AIOReactor reactor();

}
