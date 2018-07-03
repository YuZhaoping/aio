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

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.channels.DatagramChannel;
import java.nio.channels.GatheringByteChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.ScatteringByteChannel;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.WritableByteChannel;

import com.chinmobi.aio.impl.nio.HandshakeHandler;
import com.chinmobi.aio.impl.nio.TransportChannel;

/**
 * @author <a href="mailto:yuzhaoping1970@gmail.com">Zhaoping Yu</a>
 *
 */
public class DefaultTransportChannel implements TransportChannel {

	private final SelectableChannel selectableChannel;
	private final boolean isDatagramChannel;


	DefaultTransportChannel(final SelectableChannel selectableChannel) {
		this.selectableChannel = selectableChannel;

		if (selectableChannel instanceof DatagramChannel) {
			this.isDatagramChannel = true;
		} else {
			this.isDatagramChannel = false;
		}
	}


	public final int open() throws IOException {
		// Nothing to do.
		return 0;
	}

	public final boolean isSupportedInput() {
		if (!this.isDatagramChannel) {
			if (readableChannel() == null) {
				return false;
			}
		}
		return true;
	}

	public final boolean isSupportedOutput() {
		if (!this.isDatagramChannel) {
			if (writableChannel() == null) {
				return false;
			}
		}
		return true;
	}

	public final boolean shutdownInput() {
		if (this.selectableChannel instanceof SocketChannel) {
			final SocketChannel socketChannel = (SocketChannel)this.selectableChannel;
			try {
				socketChannel.socket().shutdownInput();
			} catch (IOException ignore) {
			}
		} else if (!this.isDatagramChannel) {
			final ReadableByteChannel readableChannel = readableChannel();
			if (readableChannel != null) {
				try {
					readableChannel.close();
				} catch (IOException ignore) {
				}
			}
		}
		return true;
	}

	public final boolean shutdownOutput() {
		if (this.selectableChannel instanceof SocketChannel) {
			final SocketChannel socketChannel = (SocketChannel)this.selectableChannel;
			try {
				socketChannel.socket().shutdownOutput();
			} catch (IOException ignore) {
			}
		} else if (!this.isDatagramChannel) {
			final WritableByteChannel writableChannel = writableChannel();
			if (writableChannel != null) {
				try {
					writableChannel.close();
				} catch (IOException ignore) {
				}
			}
		}
		return true;
	}

	public final HandshakeHandler getHandshakeHandler() {
		return null;
	}

	public final SelectableChannel selectableChannel() {
		return this.selectableChannel;
	}

	public final ReadableByteChannel readableChannel() {
		if (this.isDatagramChannel && !datagramChannel().isConnected()) {
			return null;
		}
		if (this.selectableChannel instanceof ReadableByteChannel) {
			return (ReadableByteChannel)this.selectableChannel;
		}
		return null;
	}

	public final ScatteringByteChannel scatteringChannel() {
		if (this.isDatagramChannel && !datagramChannel().isConnected()) {
			return null;
		}
		if (this.selectableChannel instanceof ScatteringByteChannel) {
			return (ScatteringByteChannel)this.selectableChannel;
		}
		return null;
	}

	public final WritableByteChannel writableChannel() {
		if (this.isDatagramChannel && !datagramChannel().isConnected()) {
			return null;
		}
		if (this.selectableChannel instanceof WritableByteChannel) {
			return (WritableByteChannel)this.selectableChannel;
		}
		return null;
	}

	public final GatheringByteChannel gatheringChannel() {
		if (this.isDatagramChannel && !datagramChannel().isConnected()) {
			return null;
		}
		if (this.selectableChannel instanceof GatheringByteChannel) {
			return (GatheringByteChannel)this.selectableChannel;
		}
		return null;
	}

	public final DatagramChannel datagramChannel() {
		if (this.isDatagramChannel) {
			return (DatagramChannel)this.selectableChannel;
		}
		return null;
	}

	public final SocketAddress remoteSocketAddress() {
		if (this.selectableChannel instanceof SocketChannel) {
			final SocketChannel socketChannel = (SocketChannel)this.selectableChannel;
			return socketChannel.socket().getRemoteSocketAddress();
		} else
		if (this.isDatagramChannel) {
			final DatagramChannel datagramChannel = (DatagramChannel)this.selectableChannel;
			return datagramChannel.socket().getRemoteSocketAddress();
		}
		return null;
	}

	public final SocketAddress localSocketAddress() {
		if (this.selectableChannel instanceof SocketChannel) {
			final SocketChannel socketChannel = (SocketChannel)this.selectableChannel;
			return socketChannel.socket().getLocalSocketAddress();
		} else
		if (this.isDatagramChannel) {
			final DatagramChannel datagramChannel = (DatagramChannel)this.selectableChannel;
			return datagramChannel.socket().getLocalSocketAddress();
		}
		return null;
	}

	public final void close() {
		try {
			this.selectableChannel.close();
		} catch (IOException ignore) {
		}
	}

}
