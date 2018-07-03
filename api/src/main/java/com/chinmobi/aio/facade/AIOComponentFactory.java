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
package com.chinmobi.aio.facade;

import java.io.IOException;

import com.chinmobi.aio.AIOConfiguration;
import com.chinmobi.aio.AIOReactor;
import com.chinmobi.aio.pool.AIOConnectionPool;
import com.chinmobi.aio.scheme.AIOTransportScheme;
import com.chinmobi.aio.service.AIOConnection;
import com.chinmobi.aio.service.AIOConnectionAcceptor;

/**
 * @author <a href="mailto:yuzhaoping1970@gmail.com">Zhaoping Yu</a>
 *
 */
public interface AIOComponentFactory {

	public AIOReactor createReactor(AIOConfiguration config) throws IOException;

	public AIOConnection createConnection(AIOReactor reactor, AIOTransportScheme scheme, AIOConnection.ReleaseCallback releaseCallback);

	public AIOConnectionAcceptor createConnectionAcceptor(AIOReactor reactor, AIOTransportScheme scheme);

	public AIOConnectionPool createConnectionPool(AIOConnectionCreator connectionCreator);

}
