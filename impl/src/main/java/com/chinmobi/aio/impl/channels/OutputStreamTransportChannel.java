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
import java.io.OutputStream;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.GatheringByteChannel;
import java.nio.channels.Pipe;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.ScatteringByteChannel;
import java.nio.channels.SelectableChannel;
import java.nio.channels.WritableByteChannel;

import com.chinmobi.aio.impl.nio.HandshakeHandler;
import com.chinmobi.aio.impl.nio.TransportChannel;

/**
 * @author <a href="mailto:yuzhaoping1970@gmail.com">Zhaoping Yu</a>
 *
 */
public final class OutputStreamTransportChannel implements TransportChannel, Runnable {

	private final OutputStream outputStream;

	private final Pipe.SourceChannel sourceChannel;
	private final Pipe.SinkChannel sinkChannel;

	private final ByteBuffer buffer;
	private final byte[] array;

	private volatile int status;

	private String name;


	OutputStreamTransportChannel(final OutputStream outputStream, final int bufferSize) throws IOException {
		this.outputStream = outputStream;

		final Pipe pipe = Pipe.open();

		this.sourceChannel = pipe.source();
		this.sinkChannel = pipe.sink();

		this.sourceChannel.configureBlocking(true);
		this.sinkChannel.configureBlocking(false);

		this.buffer = ByteBuffer.allocate(bufferSize);
		this.buffer.clear();

		this.array = this.buffer.array();
	}


	final void setName(final String name) {
		this.name = name;
	}

	public final int open() throws IOException {
		// Nothing to do.
		return 0;
	}

	public final void close() {
		internalClose();

		try {
			this.sinkChannel.close();
		} catch (IOException ignore) {
		}
	}

	private final void internalClose() {
		this.status = -1;

		try {
			this.outputStream.close();
		} catch (IOException ignore) {
		}

		try {
			this.sourceChannel.close();
		} catch (IOException ignore) {
		}
	}

	public final boolean isSupportedInput() {
		return false;
	}

	public final boolean isSupportedOutput() {
		return true;
	}

	public final boolean shutdownInput() {
		// Nothing to do.
		return true;
	}

	public final boolean shutdownOutput() {
		internalClose();
		return true;
	}

	public final HandshakeHandler getHandshakeHandler() {
		return null;
	}

	public final SelectableChannel selectableChannel() {
		return this.sinkChannel;
	}

	public final ReadableByteChannel readableChannel() {
		return null;
	}

	public final ScatteringByteChannel scatteringChannel() {
		return null;
	}

	public final WritableByteChannel writableChannel() {
		return this.sinkChannel;
	}

	public final GatheringByteChannel gatheringChannel() {
		return this.sinkChannel;
	}

	public final DatagramChannel datagramChannel() {
		return null;
	}

	public final SocketAddress remoteSocketAddress() {
		return null;
	}

	public final SocketAddress localSocketAddress() {
		return null;
	}

	/*
	 * (non-Javadoc)
	 * @see java.lang.Runnable#run()
	 */
	public final void run() {
		while (this.status == 0) {
			try {
				doRun();
			} catch (Exception ex) {
				internalClose();
			}
		}
	}

	private final void doRun() throws IOException {
		final int count = this.sourceChannel.read(this.buffer);

		this.buffer.flip();

		if (this.buffer.hasRemaining()) {
			this.outputStream.write(this.array, 0, this.buffer.remaining());
			this.outputStream.flush();
		}

		this.buffer.clear();

		if (count < 0) {
			internalClose();
		}
	}

	@Override
	public final String toString() {
		final StringBuilder builder = new StringBuilder();
		builder.append("OutputSTC");
		final String name = this.name;
		if (name != null) {
			builder.append("[");
			builder.append(name);
			builder.append("]");
		}
		return builder.toString();
	}

}
