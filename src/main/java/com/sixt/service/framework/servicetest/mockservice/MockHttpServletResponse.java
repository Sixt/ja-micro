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

import org.apache.commons.lang3.NotImplementedException;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Collection;
import java.util.Locale;

public class MockHttpServletResponse implements HttpServletResponse {

	protected Socket socket;
	protected String contentType;
	protected int statusCode;
	protected HttpResponseWriter writer;

	public MockHttpServletResponse(Socket socket) throws IOException {
		this.socket = socket;
		writer = new HttpResponseWriter(this, socket.getOutputStream());
	}

	public void complete() throws IOException {
		writer.complete();
	}

	@Override
	public void setStatus(int sc) {
		statusCode = sc;
	}

	public int getStatus() {
		return statusCode;
	}

	@Override
	public String getHeader(String name) {
		return null;
	}

	@Override
	public Collection<String> getHeaders(String name) {
		return null;
	}

	@Override
	public Collection<String> getHeaderNames() {
		return null;
	}

	@Override
	public String getContentType() {
		return contentType;
	}

	@Override
	public PrintWriter getWriter() throws IOException {
		return writer;
	}

	@Override
	public ServletOutputStream getOutputStream() throws IOException {
		return writer.getOutputStream();
	}

	@Override
	public void addCookie(Cookie cookie) {
		throw new NotImplementedException("");
	}

	@Override
	public boolean containsHeader(String name) {
		throw new NotImplementedException("");
	}

	@Override
	public String encodeURL(String url) {
		throw new NotImplementedException("");
	}

	@Override
	public String encodeRedirectURL(String url) {
		throw new NotImplementedException("");
	}

    @Deprecated
	@Override
	public String encodeUrl(String url) {
		throw new NotImplementedException("");
	}

    @Deprecated
	@Override
	public String encodeRedirectUrl(String url) {
		throw new NotImplementedException("");
	}

	@Override
	public void sendError(int sc, String msg) throws IOException {
		throw new NotImplementedException("");
	}

	@Override
	public void sendError(int sc) throws IOException {
		throw new NotImplementedException("");
	}

	@Override
	public void sendRedirect(String location) throws IOException {
		throw new NotImplementedException("");
	}

	@Override
	public void setDateHeader(String name, long date) {
		throw new NotImplementedException("");
	}

	@Override
	public void addDateHeader(String name, long date) {
		throw new NotImplementedException("");
	}

	@Override
	public void setHeader(String name, String value) {
		throw new NotImplementedException("");
	}

	@Override
	public void addHeader(String name, String value) {
		throw new NotImplementedException("");
	}

	@Override
	public void setIntHeader(String name, int value) {
		throw new NotImplementedException("");
	}

	@Override
	public void addIntHeader(String name, int value) {
		throw new NotImplementedException("");
	}

	@Deprecated
	@Override
	public void setStatus(int sc, String sm) {
		throw new NotImplementedException("");
	}

	@Override
	public String getCharacterEncoding() {
		throw new NotImplementedException("");
	}

	@Override
	public void setCharacterEncoding(String charset) {
		throw new NotImplementedException("");
	}

	@Override
	public void setContentLength(int len) {
		throw new NotImplementedException("");
	}

	@Override
	public void setContentLengthLong(long len) {

	}

	@Override
	public void setContentType(String type) {
		contentType = type;
	}

	@Override
	public void setBufferSize(int size) {
		throw new NotImplementedException("");
	}

	@Override
	public int getBufferSize() {
		throw new NotImplementedException("");
	}

	@Override
	public void flushBuffer() throws IOException {
		throw new NotImplementedException("");
	}

	@Override
	public void resetBuffer() {
		throw new NotImplementedException("");
	}

	@Override
	public boolean isCommitted() {
		throw new NotImplementedException("");
	}

	@Override
	public void reset() {
		throw new NotImplementedException("");
	}

	@Override
	public void setLocale(Locale loc) {
		throw new NotImplementedException("");
	}

	@Override
	public Locale getLocale() {
		throw new NotImplementedException("");
	}
}
