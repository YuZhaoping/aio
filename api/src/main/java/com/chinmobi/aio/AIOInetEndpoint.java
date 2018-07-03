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
import java.net.InetAddress;
import java.net.InetSocketAddress;

/**
 * @author <a href="mailto:yuzhaoping1970@gmail.com">Zhaoping Yu</a>
 *
 */
public final class AIOInetEndpoint {


	public interface Observer {
		public void onAIOEndpointChanged(AIOInetEndpoint endpoint, boolean isSocketAddress);
	}


	private final Observer observer;

	private SocketAddress socketAddress;

	private String hostName;
	private int port;


	public AIOInetEndpoint(final int port) {
		this(port, null);
	}

	public AIOInetEndpoint(final int port, final Observer observer) {
		this.observer = observer;
		this.port = port;
	}

	public AIOInetEndpoint(final String hostName, final int port) {
		this(hostName, port, null);
	}

	public AIOInetEndpoint(final String hostName, final int port, final Observer observer) {
		this.observer = observer;

		this.hostName = hostName;
		this.port = port;
	}

	public AIOInetEndpoint(final SocketAddress socketAddress) {
		this(socketAddress, null);
	}

	public AIOInetEndpoint(final SocketAddress socketAddress, final Observer observer) {
		this.observer = observer;
		doSet(socketAddress);
	}

	public AIOInetEndpoint(final AIOInetEndpoint endpoint) {
		this(endpoint, null);
	}

	public AIOInetEndpoint(final AIOInetEndpoint endpoint, final Observer observer) {
		this.observer = observer;

		this.socketAddress = endpoint.socketAddress;
		this.hostName = endpoint.hostName;
		this.port = endpoint.port;
	}


	public final String getHostName() {
		return this.hostName;
	}

	public final int getPort() {
		return this.port;
	}

	public final void set(final String hostName, final int port) {
		if (!equals(hostName, port)) {
			doSet(hostName, port);

			if (this.observer != null) {
				this.observer.onAIOEndpointChanged(this, false);
			}
		}
	}

	public final void set(final int port) {
		set(null, port);
	}


	public final SocketAddress getSocketAddress() {
		return this.socketAddress;
	}

	public final void set(final SocketAddress socketAddress) {
		if (!equals(socketAddress)) {
			doSet(socketAddress);

			if (this.observer != null) {
				this.observer.onAIOEndpointChanged(this, true);
			}
		}
	}


	@Override
	public boolean equals(final Object obj) {
		if (obj != null && obj instanceof AIOInetEndpoint) {
			return equals((AIOInetEndpoint)obj);
		}
		return false;
	}

	public final boolean equals(final AIOInetEndpoint another) {
		if (another != null) {
			if (this == another) {
				return true;
			}

			if (equals(another.hostName, another.port)) {
				return true;
			} else {
				return another.equals(this.hostName, this.port);
			}
		}
		return false;
	}

	public final boolean equals(final String anotherHostName, final int anotherPort) {
		if (this.port == anotherPort) {
			final String hostName = this.hostName;
			if (hostName != null) {
				if (anotherHostName != null) {
					if (hostName.equalsIgnoreCase(anotherHostName)) {
						return true;
					}
					final SocketAddress socketAddress = this.socketAddress;
					if (socketAddress != null && (socketAddress instanceof InetSocketAddress)) {
						final InetAddress addr = ((InetSocketAddress)socketAddress).getAddress();
						if (addr != null && addr.getHostAddress().equals(anotherHostName)) {
							return true;
						}
					}
				}
			} else if (anotherHostName == null) {
				return true;
			}
		}
		return false;
	}

	public final boolean equals(final SocketAddress anotherAddress) {
		final SocketAddress socketAddress = this.socketAddress;
		if (socketAddress != null) {
			if (anotherAddress != null) {
				return anotherAddress.equals(socketAddress);
			}
		} else if (anotherAddress == null) {
			return true;
		}
		return false;
	}


	private final void doSet(final SocketAddress socketAddress) {
		this.socketAddress = socketAddress;
	}

	private final void doSet(final String hostName, final int port) {
		this.hostName = hostName;
		this.port = port;
		this.socketAddress = null;
	}

}
