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
package com.chinmobi.aiotest.level1;

import java.security.InvalidAlgorithmParameterException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.Provider;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import javax.net.ssl.ManagerFactoryParameters;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.TrustManagerFactorySpi;
import javax.net.ssl.X509TrustManager;

/**
 * @author <a href="mailto:yuzhaoping1970@gmail.com">Zhaoping Yu</a>
 *
 */
public final class BogusTrustManagerFactory extends TrustManagerFactory {

	private static final BogusTrustManagerFactory INSTANCE = new BogusTrustManagerFactory();

	private BogusTrustManagerFactory() {
		super(new Spi(), new Provider("AIOBogusTrust", 1.0, "") {
			private static final long serialVersionUID = -5930989100013453324L;
		}, "AIOBogusTrust");
	}


	public static final TrustManagerFactory getInstance() {
		return INSTANCE;
	}


	private static final X509TrustManager X509 = new X509TrustManager() {

		public final void checkClientTrusted(final X509Certificate[] chain, final String authType)
				throws CertificateException {
			// Nothing to do.
		}

		public final void checkServerTrusted(final X509Certificate[] chain, final String authType)
				throws CertificateException {
			// Nothing to do.
		}

		public final X509Certificate[] getAcceptedIssuers() {
			return new X509Certificate[0];
		}

	};

	private static final TrustManager[] X509_MANAGERS = new TrustManager[] { X509 };

	private static final class Spi extends TrustManagerFactorySpi {

		@Override
		protected final TrustManager[] engineGetTrustManagers() {
			return X509_MANAGERS;
		}

		@Override
		protected final void engineInit(final KeyStore keyStore) throws KeyStoreException {
			// Nothing to do.
		}

		@Override
		protected final void engineInit(final ManagerFactoryParameters arg0)
				throws InvalidAlgorithmParameterException {
			// Nothing to do.
		}

	}

}
