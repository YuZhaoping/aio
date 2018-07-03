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
package com.chinmobi.aio.scheme;

import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;

/**
 * @author <a href="mailto:yuzhaoping1970@gmail.com">Zhaoping Yu</a>
 *
 */
public class AIOSocketScheme extends AIOTransportScheme {

	public AIOSocketScheme(final String name) {
		super(name);
	}

	public AIOSocketScheme() {
		this("TCP");
	}


	public final boolean isAddressResolvable() {
		return true;
	}

	public AIOSecurityScheme getSecurityScheme() {
		return null;
	}

	public void prepareBind(final ServerSocket serverSocket) throws SocketException {
	}

	public int getServerBacklog() {
		return 0;
	}

	public void socketReady(final ServerSocket serverSocket) throws SocketException {
	}

	public void prepareBind(final Socket socket) throws SocketException {
		//socket.setReuseAddress(true);
	}

	public void socketReady(final Socket socket) throws SocketException {
		//socket.setKeepAlive(true);
	}

}
