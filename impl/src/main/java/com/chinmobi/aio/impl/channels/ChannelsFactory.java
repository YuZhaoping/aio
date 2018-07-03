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
import java.nio.channels.SelectableChannel;

import com.chinmobi.aio.impl.BuildOptions;
import com.chinmobi.aio.impl.nio.AcceptableChannel;
import com.chinmobi.aio.impl.nio.ConnectableChannel;
import com.chinmobi.aio.impl.nio.TransportChannel;
import com.chinmobi.aio.scheme.AIODatagramScheme;
import com.chinmobi.aio.scheme.AIOSocketScheme;
import com.chinmobi.aio.scheme.AIOStreamScheme;
import com.chinmobi.aio.scheme.AIOTransportScheme;

/**
 * @author <a href="mailto:yuzhaoping1970@gmail.com">Zhaoping Yu</a>
 *
 */
public final class ChannelsFactory implements BuildOptions {

	private ChannelsFactory() {
	}


	public static TransportChannel createTransportChannel(final SelectableChannel selectableChannel) {
return new DefaultTransportChannel(selectableChannel);
	}

	public static ConnectableChannel createConnectableChannel(final AIOTransportScheme scheme) throws IOException {
		if (scheme != null) {
			if (scheme instanceof AIOSocketScheme) {
				if (SUPPORT_CLIENT != 0 && SUPPORT_SOCKET_SCHEME != 0) {
return new SocketConnectableChannel((AIOSocketScheme)scheme);
				}
				throw new UnsupportedOperationException("SocketClient");
			} else
			if (scheme instanceof AIODatagramScheme) {
				if (SUPPORT_CLIENT != 0 && SUPPORT_DATAGRAM_SCHEME != 0) {
return new DatagramConnectableChannel((AIODatagramScheme)scheme);
				}
				throw new UnsupportedOperationException("DatagramClient");
			} else
			if (scheme instanceof AIOStreamScheme) {
				if (SUPPORT_CLIENT != 0 && SUPPORT_STREAM_SCHEME != 0) {
return new StreamConnectableChannel((AIOStreamScheme)scheme);
				}
				throw new UnsupportedOperationException("StreamClient");
			} else {
				throw new IllegalArgumentException("Unsupported scheme.");
			}

		} else {
			throw new IllegalArgumentException("Null scheme.");
		}
	}

	public static AcceptableChannel createAcceptableChannel(final AIOTransportScheme scheme) throws IOException {
		if (scheme != null) {
			if (scheme instanceof AIOSocketScheme) {
				if (SUPPORT_SERVER != 0 && SUPPORT_SOCKET_SCHEME != 0) {
return new SocketAcceptableChannel((AIOSocketScheme)scheme);
				}
				throw new UnsupportedOperationException("SocketServer");
			} else
			if (scheme instanceof AIODatagramScheme) {
				if (SUPPORT_SERVER != 0 && SUPPORT_DATAGRAM_SCHEME != 0) {
return new DatagramAcceptableChannel((AIODatagramScheme)scheme);
				}
				throw new UnsupportedOperationException("DatagramServer");
			} else
			if (scheme instanceof AIOStreamScheme) {
				if (SUPPORT_SERVER != 0 && SUPPORT_STREAM_SCHEME != 0) {
return new StreamAcceptableChannel((AIOStreamScheme)scheme);
				}
				throw new UnsupportedOperationException("StreamServer");
			} else {
				throw new IllegalArgumentException("Unsupported scheme.");
			}

		} else {
			throw new IllegalArgumentException("Null scheme.");
		}
	}

}
