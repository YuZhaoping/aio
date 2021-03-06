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
package com.chinmobi.aio.impl.service;

import com.chinmobi.aio.AIOReactor;
import com.chinmobi.aio.impl.BuildOptions;
import com.chinmobi.aio.scheme.AIOTransportScheme;
import com.chinmobi.aio.service.AIOConnection;
import com.chinmobi.aio.service.AIOConnectionAcceptor;

/**
 * @author <a href="mailto:yuzhaoping1970@gmail.com">Zhaoping Yu</a>
 *
 */
public final class ComponentCreator {

	private ComponentCreator() {
	}


	public static final AIOConnection createConnection(final ServiceContext serviceContext,
			final AIOReactor reactor, final AIOTransportScheme scheme, final AIOConnection.ReleaseCallback releaseCallback) {
		if (BuildOptions.SUPPORT_CLIENT != 0) {
return new Connection(serviceContext, reactor, scheme, releaseCallback);
		}
		throw new UnsupportedOperationException();
	}

	public static final AIOConnectionAcceptor createConnectionAcceptor(final ServiceContext serviceContext,
			final AIOReactor reactor, final AIOTransportScheme scheme) {
		if (BuildOptions.SUPPORT_SERVER != 0) {
return new ConnectionAcceptor(serviceContext, reactor, scheme);
		}
		throw new UnsupportedOperationException();
	}

}
