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
package com.chinmobi.aio.octet;

import java.net.SocketAddress;

import com.chinmobi.aio.act.AIODatagramReadableActEntry;
import com.chinmobi.aio.act.AIODatagramWritableActEntry;
import com.chinmobi.octet.ExpandableOctetBuffer;

/**
 * @author <a href="mailto:yuzhaoping1970@gmail.com">Zhaoping Yu</a>
 *
 */
public class AIOOctetDatagramBase extends AIOOctetBufferBase {

	protected SocketAddress remote;


	public AIOOctetDatagramBase(final ExpandableOctetBuffer octetBuffer) {
		super();

		this.octetBuffer = octetBuffer;

		this.input = new Input(this);
		this.output = new Output(this);
	}


	@Override
	public AIOOctetDatagramBase wrap(final ExpandableOctetBuffer octetBuffer) {
		super.wrap(octetBuffer);
		return this;
	}

	@Override
	public AIOOctetDatagramBase clear() {
		super.clear();
		return this;
	}

	@Override
	public AIODatagramWritableActEntry toOutput() {
		return (AIODatagramWritableActEntry)super.toOutput();
	}

	@Override
	public AIODatagramWritableActEntry toOutput(final int count) {
		return (AIODatagramWritableActEntry)super.toOutput(count);
	}

	@Override
	public AIODatagramReadableActEntry toInput() {
		return (AIODatagramReadableActEntry)super.toInput();
	}


	public static class Input extends AIOOctetBufferBase.Input
		implements AIODatagramReadableActEntry {

		protected Input(final AIOOctetDatagramBase base) {
			super(base);
		}

		public final SocketAddress getRemoteSocketAddress() {
			return base().remote;
		}

		@Override
		public AIOOctetDatagramBase base() {
			return (AIOOctetDatagramBase)this.base;
		}
	}


	public static class Output extends AIOOctetBufferBase.Output
		implements AIODatagramWritableActEntry {

		protected Output(final AIOOctetDatagramBase base) {
			super(base);
		}

		public final void setRemoteSocketAddress(final SocketAddress remote) {
			base().remote = remote;
		}

		@Override
		public AIOOctetDatagramBase base() {
			return (AIOOctetDatagramBase)this.base;
		}
	}

}
