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
package com.chinmobi.aio.impl.facade;

import java.io.IOException;
import java.nio.channels.Selector;

import com.chinmobi.aio.AIOConfiguration;
import com.chinmobi.aio.AIOReactor;
import com.chinmobi.aio.facade.AIOComponentFactory;
import com.chinmobi.aio.facade.AIOConnectionCreator;
import com.chinmobi.aio.impl.nio.Reactor;
import com.chinmobi.aio.impl.nio.ReactorGroup;
import com.chinmobi.aio.impl.pool.ConnectionPoolCreator;
import com.chinmobi.aio.impl.service.ComponentCreator;
import com.chinmobi.aio.impl.service.ServiceContext;
import com.chinmobi.aio.pool.AIOConnectionPool;
import com.chinmobi.aio.scheme.AIOTransportScheme;
import com.chinmobi.aio.service.AIOConnection;
import com.chinmobi.aio.service.AIOConnectionAcceptor;

/**
 * @author <a href="mailto:yuzhaoping1970@gmail.com">Zhaoping Yu</a>
 *
 */
public final class ComponentFactory implements AIOComponentFactory {

	private final ServiceContext serviceContext;


	public ComponentFactory() {
		this.serviceContext = new ServiceContext();
	}

	public final AIOReactor createReactor(final AIOConfiguration config) throws IOException {
		if (config.getReactorGroupSize() <= 0) {
			return new Reactor(Selector.open(), config);
		} else {
			return new ReactorGroup(config);
		}
	}

	public final AIOConnection createConnection(final AIOReactor reactor, final AIOTransportScheme scheme, final AIOConnection.ReleaseCallback releaseCallback) {
		return ComponentCreator.createConnection(this.serviceContext, reactor, scheme, releaseCallback);
	}

	public final AIOConnectionAcceptor createConnectionAcceptor(final AIOReactor reactor, final AIOTransportScheme scheme) {
		return ComponentCreator.createConnectionAcceptor(this.serviceContext, reactor, scheme);
	}

	public final AIOConnectionPool createConnectionPool(final AIOConnectionCreator connectionCreator) {
		return ConnectionPoolCreator.createConnectionPool(connectionCreator);
	}

}
