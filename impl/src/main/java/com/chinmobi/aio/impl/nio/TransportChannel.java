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
package com.chinmobi.aio.impl.nio;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.channels.DatagramChannel;
import java.nio.channels.GatheringByteChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.ScatteringByteChannel;
import java.nio.channels.SelectableChannel;
import java.nio.channels.WritableByteChannel;

/**
 * @author <a href="mailto:yuzhaoping1970@gmail.com">Zhaoping Yu</a>
 *
 */
public interface TransportChannel {

	/**
	 *
	 * @return The interestOps
	 * @throws IOException
	 */
	public int open() throws IOException;

	public void close();

	public boolean isSupportedInput();
	public boolean isSupportedOutput();

	public boolean shutdownInput();
	public boolean shutdownOutput();

	/*
	 * Handshake methods.
	 */

	public HandshakeHandler getHandshakeHandler();

	/*
	 * Channel methods.
	 */

	public SelectableChannel selectableChannel();

	public ReadableByteChannel readableChannel();
	public ScatteringByteChannel scatteringChannel();

	public WritableByteChannel writableChannel();
	public GatheringByteChannel gatheringChannel();

	public DatagramChannel datagramChannel();

	public SocketAddress remoteSocketAddress();
	public SocketAddress localSocketAddress();

}
