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
package com.chinmobi.aio.impl.nio;

import java.io.PrintStream;
import java.io.PrintWriter;

/**
 * @author <a href="mailto:yuzhaoping1970@gmail.com">Zhaoping Yu</a>
 *
 */
public class ThrowableWrapper extends Throwable {

	private static final long serialVersionUID = -708606144629751871L;


	public ThrowableWrapper(final String message, final Throwable wrapped) {
		super(message, wrapped);
	}


	@Override
	public final String getMessage() {
		final StringBuilder msg = new StringBuilder(super.getMessage());
		msg.append(getCause().getMessage());
		return msg.toString();
	}

	@Override
	public final void printStackTrace(PrintStream stream) {
		getCause().printStackTrace(stream);
	}

	@Override
	public final void printStackTrace(PrintWriter writer) {
		getCause().printStackTrace(writer);
	}

	@Override
	public final StackTraceElement[] getStackTrace() {
		return getCause().getStackTrace();
	}

	@Override
	public final void setStackTrace(final StackTraceElement[] stackTrace) {
		getCause().setStackTrace(stackTrace);
	}

}
