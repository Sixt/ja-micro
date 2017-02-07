/**
 * Copyright 2016-2017 Sixt GmbH & Co. Autovermietung KG
 * Licensed under the Apache License, Version 2.0 (the "License"); you may 
 * not use this file except in compliance with the License. You may obtain a 
 * copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software 
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT 
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the 
 * License for the specific language governing permissions and limitations 
 * under the License.
 */

package com.sixt.service.framework.servicetest.mockservice;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletOutputStream;
import javax.servlet.WriteListener;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;

public class HttpResponseWriter extends PrintWriter {

	private static final Logger logger = LoggerFactory.getLogger(HttpResponseWriter.class);

	protected MockHttpServletResponse servletResponse;
	protected OutputStream outputStream;
	protected ByteArrayOutputStream buffer;

	public HttpResponseWriter(MockHttpServletResponse mockHttpServletResponse, OutputStream out) {
		super(out);
		this.servletResponse = mockHttpServletResponse;
		this.outputStream = out;
		buffer = new ByteArrayOutputStream();
	}

	public void complete() throws IOException {
		String temp = "HTTP/1.1 " + servletResponse.getStatus() + "\r\n";
		outputStream.write(temp.getBytes());
		temp = "Content-Type: " + servletResponse.getContentType() + "\r\n";
		outputStream.write(temp.getBytes());
		byte data[] = buffer.toByteArray();
		temp = "Content-Length: " + data.length + "\r\n\r\n";
		outputStream.write(temp.getBytes());
		outputStream.write(data);
		outputStream.flush();
	}

	@Override
	public void write(String s) {
		try {
			buffer.write(s.getBytes());
		} catch (Exception ex) {
			logger.error("Caught exception", ex);
		}
	}

	public ServletOutputStream getOutputStream() {
		return new ServletOutputStream() {

			@Override
			public boolean isReady() {
				return false;
			}

			@Override
			public void setWriteListener(WriteListener writeListener) {

			}

			@Override
			public void write(int b) throws IOException {
				buffer.write(b);
			}

			@Override
			public void write(byte[] b) throws IOException {
				buffer.write(b);
			}
		};
	}

}
