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
package com.chinmobi.aio.impl.act;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.ReadableByteChannel;

import com.chinmobi.aio.impl.nio.Session;
import com.chinmobi.logging.Logger;

/**
 * @author <a href="mailto:yuzhaoping1970@gmail.com">Zhaoping Yu</a>
 *
 */
public class DummyInputConsumer {

	private DummyInputConsumer() {
	}


	public static final void handleInput(final Session session) throws IOException {
		final ReadableByteChannel readableChannel = session.readableChannel();
		if (readableChannel != null) {
			if (handleInput(readableChannel, session) < 0) {
				session.close();
			}
		} else {
			final DatagramChannel datagramChannel = session.datagramChannel();
			if (datagramChannel != null) {
				handleInput(datagramChannel, session);
			} else {
				session.close();
			}
		}
	}

	private static final int handleInput(final ReadableByteChannel channel,
			final Session session) throws IOException {

		int bufferSize = 1024;
		final DatagramChannel datagramChannel = session.datagramChannel();
		if (datagramChannel != null) {
			bufferSize = datagramChannel.socket().getReceiveBufferSize();
		}

		final ByteBuffer buffer = ByteBuffer.allocate(bufferSize);

		Logger logger = null;

		for (;;) {
			buffer.clear();

			final int reads = channel.read(buffer);
			if (reads > 0) {
				if (logger == null) {
					logger = session.context().logger();
					if (logger != null && logger.isDebugEnabled()) {
						logger.debug().writeln().write("Dump input for: ").writeln(session.toString());
					}
				}
				if (logger != null && logger.isDebugEnabled()) {
					logger.debug().write(buffer, true);
				}

				continue;
			} else if (reads < 0) {
				if (logger != null && logger.isDebugEnabled()) {
					logger.debug().flush();
				}

				return -1;
			}

			break;
		}

		if (logger != null && logger.isDebugEnabled()) {
			logger.debug().flush();
		}

		return 0;
	}

	private static final void handleInput(final DatagramChannel channel,
			final Session session) throws IOException {
		final ByteBuffer buffer = ByteBuffer.allocate(channel.socket().getReceiveBufferSize());

		final SocketAddress remote = channel.receive(buffer);
		if (remote != null) {
			final Logger logger = session.context().logger();

			if (logger != null && logger.isDebugEnabled()) {
				logger.debug().writeln().write("Dump input for: ").writeln(session.toString());
				logger.debug().write("from: ").writeln(remote.toString());

				logger.debug().write(buffer, true).flush();
			}
		}
	}

}
