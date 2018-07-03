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
package com.chinmobi.aio;

import java.net.SocketAddress;
import java.nio.channels.DatagramChannel;
import java.nio.channels.GatheringByteChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.ScatteringByteChannel;
import java.nio.channels.WritableByteChannel;
import java.util.concurrent.TimeUnit;

import com.chinmobi.aio.act.AIOInputActor;
import com.chinmobi.aio.act.AIOOutputActor;

/**
 * @author <a href="mailto:yuzhaoping1970@gmail.com">Zhaoping Yu</a>
 *
 */
public interface AIOSession {

	public boolean isOpen();

	public void close();
	public void close(boolean forceShutdown);

	public boolean isInputShutdown();
	public boolean isOutputShutdown();

	public boolean shutdownInput(boolean force);
	public boolean shutdownOutput(boolean force);

	public AIOServiceHandler getServiceHandler();

	public void setTimeout(long timeout, TimeUnit unit);

	public AIOInputActor inputActor();
	public AIOOutputActor outputActor();

	public ReadableByteChannel readableChannel();
	public ScatteringByteChannel scatteringChannel();

	public WritableByteChannel writableChannel();
	public GatheringByteChannel gatheringChannel();

	public DatagramChannel datagramChannel();

	public int getInterestEvents();

	public void setInputEvent();
	public void clearInputEvent();

	public void setOutputEvent();
	public void clearOutputEvent();

	public SocketAddress getRemoteSocketAddress();
	public SocketAddress getLocalSocketAddress();

	public int id();

}
