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
import org.apache.commons.lang3.StringUtils;

import javax.servlet.*;
import javax.servlet.http.*;
import java.io.*;
import java.net.Socket;
import java.security.Principal;
import java.util.*;

import static com.google.common.net.HttpHeaders.CONTENT_TYPE;

public class MockHttpServletRequest implements HttpServletRequest {

	protected Socket socket;
	protected InputStream inputStream;
	protected List<String> headers = new ArrayList<>();
	protected byte[] postBody;

	public MockHttpServletRequest(Socket socket) throws IOException {
		this.socket = socket;
		parseRequest();
	}

	private void parseRequest() throws IOException {
		inputStream = socket.getInputStream();

		while (true) {
			String line = readLine();
			if (StringUtils.isBlank(line)) {
				break;
			}
			headers.add(line);
		}
		String lengthStr = getHeader("content-length");
		if (lengthStr != null) {
			int contentLength = Integer.valueOf(lengthStr);
			postBody = new byte[contentLength];
			inputStream.read(postBody);
		}
	}

	// read a line and strip the \r\n
	private String readLine() throws IOException {
		StringBuilder sb = new StringBuilder();
		while (true) {
			int b = inputStream.read();
			if (b == '\n') {
				break;
			}
			sb.append((char) b);
		}
		return sb.toString().trim();
	}

	@Override
	public String getHeader(String name) {
		String retval = null;
		for (String line : headers) {
			if (line.toLowerCase().startsWith(name.toLowerCase() + ":")) {
				retval = line;
				break;
			}
		}
		if (retval != null) {
			int index = retval.indexOf(':');
			return retval.substring(index + 1).trim();
		}
		return null;
	}

	@Override
	public Enumeration<String> getHeaders(String name) {
		Set<String> uniqueSet = new HashSet<>();
		for (String line : headers) {
			if (line.toLowerCase().startsWith(name.toLowerCase() + ":")) {
				int index = line.indexOf(':');
				uniqueSet.add(line.substring(index + 1).trim());
			}
		}
		Vector<String> retval = new Vector<>(uniqueSet);
		return retval.elements();
	}

	@Override
	public Enumeration<String> getHeaderNames() {
		Vector<String> retval = new Vector<>();
		for (String line : headers) {
			int index = line.indexOf(':');
			if (index != -1) {
				retval.add(line.substring(0, index).trim());
			}
		}
		return retval.elements();
	}

	@Override
	public ServletInputStream getInputStream() throws IOException {
		final ByteArrayInputStream stream = new ByteArrayInputStream(postBody);
		return new ServletInputStream() {
			@Override
			public boolean isFinished() {
				return false;
			}

			@Override
			public boolean isReady() {
				return false;
			}

			@Override
			public void setReadListener(ReadListener readListener) {

			}

			@Override
			public int read() throws IOException {
				return stream.read();
			}
		};
	}

	@Override
	public BufferedReader getReader() throws IOException {
		if (postBody == null) {
			return new BufferedReader(new StringReader(""));
		} else {
			return new BufferedReader(new InputStreamReader(new ByteArrayInputStream(postBody)));
		}
	}

	@Override
	public String getAuthType() {
		throw new NotImplementedException("");
	}

	@Override
	public Cookie[] getCookies() {
		throw new NotImplementedException("");
	}

	@Override
	public long getDateHeader(String name) {
		throw new NotImplementedException("");
	}

	@Override
	public int getIntHeader(String name) {
		throw new NotImplementedException("");
	}

	@Override
	public String getMethod() {
		throw new NotImplementedException("");
	}

	@Override
	public String getPathInfo() {
		throw new NotImplementedException("");
	}

	@Override
	public String getPathTranslated() {
		throw new NotImplementedException("");
	}

	@Override
	public String getContextPath() {
		throw new NotImplementedException("");
	}

	@Override
	public String getQueryString() {
		throw new NotImplementedException("");
	}

	@Override
	public String getRemoteUser() {
		throw new NotImplementedException("");
	}

	@Override
	public boolean isUserInRole(String role) {
		throw new NotImplementedException("");
	}

	@Override
	public Principal getUserPrincipal() {
		throw new NotImplementedException("");
	}

	@Override
	public String getRequestedSessionId() {
		throw new NotImplementedException("");
	}

	@Override
	public String getRequestURI() {
		throw new NotImplementedException("");
	}

	@Override
	public StringBuffer getRequestURL() {
		throw new NotImplementedException("");
	}

	@Override
	public String getServletPath() {
		throw new NotImplementedException("");
	}

	@Override
	public HttpSession getSession(boolean create) {
		throw new NotImplementedException("");
	}

	@Override
	public HttpSession getSession() {
		throw new NotImplementedException("");
	}

	@Override
	public String changeSessionId() {
		return null;
	}

	@Override
	public boolean isRequestedSessionIdValid() {
		throw new NotImplementedException("");
	}

	@Override
	public boolean isRequestedSessionIdFromCookie() {
		throw new NotImplementedException("");
	}

	@Override
	public boolean isRequestedSessionIdFromURL() {
		throw new NotImplementedException("");
	}

	@Override
	public boolean isRequestedSessionIdFromUrl() {
		throw new NotImplementedException("");
	}

	@Override
	public boolean authenticate(HttpServletResponse response) throws IOException, ServletException {
		return false;
	}

	@Override
	public void login(String username, String password) throws ServletException {

	}

	@Override
	public void logout() throws ServletException {

	}

	@Override
	public Collection<Part> getParts() throws IOException, ServletException {
		return null;
	}

	@Override
	public Part getPart(String name) throws IOException, ServletException {
		return null;
	}

	@Override
	public <T extends HttpUpgradeHandler> T upgrade(Class<T> handlerClass) throws IOException, ServletException {
		return null;
	}

	@Override
	public Object getAttribute(String name) {
		throw new NotImplementedException("");
	}

	@Override
	public Enumeration<String> getAttributeNames() {
		throw new NotImplementedException("");
	}

	@Override
	public String getCharacterEncoding() {
		throw new NotImplementedException("");
	}

	@Override
	public void setCharacterEncoding(String env) throws UnsupportedEncodingException {
		throw new NotImplementedException("");
	}

	@Override
	public int getContentLength() {
		throw new NotImplementedException("");
	}

	@Override
	public long getContentLengthLong() {
		return 0;
	}

	@Override
	public String getContentType() {
		Enumeration<String> headers = getHeaders(CONTENT_TYPE);

		if (! headers.hasMoreElements()) {
			return null;
		}

		return headers.nextElement();
	}

	@Override
	public String getParameter(String name) {
		throw new NotImplementedException("");
	}

	@Override
	public Enumeration<String> getParameterNames() {
		throw new NotImplementedException("");
	}

	@Override
	public String[] getParameterValues(String name) {
		throw new NotImplementedException("");
	}

	@Override
	public Map<String, String[]> getParameterMap() {
		throw new NotImplementedException("");
	}

	@Override
	public String getProtocol() {
		throw new NotImplementedException("");
	}

	@Override
	public String getScheme() {
		throw new NotImplementedException("");
	}

	@Override
	public String getServerName() {
		throw new NotImplementedException("");
	}

	@Override
	public int getServerPort() {
		throw new NotImplementedException("");
	}

	@Override
	public String getRemoteAddr() {
		throw new NotImplementedException("");
	}

	@Override
	public String getRemoteHost() {
		throw new NotImplementedException("");
	}

	@Override
	public void setAttribute(String name, Object o) {
		throw new NotImplementedException("");
	}

	@Override
	public void removeAttribute(String name) {
		throw new NotImplementedException("");
	}

	@Override
	public Locale getLocale() {
		throw new NotImplementedException("");
	}

	@Override
	public Enumeration<Locale> getLocales() {
		throw new NotImplementedException("");
	}

	@Override
	public boolean isSecure() {
		throw new NotImplementedException("");
	}

	@Override
	public RequestDispatcher getRequestDispatcher(String path) {
		throw new NotImplementedException("");
	}

	@Override
	public String getRealPath(String path) {
		throw new NotImplementedException("");
	}

	@Override
	public int getRemotePort() {
		throw new NotImplementedException("");
	}

	@Override
	public String getLocalName() {
		throw new NotImplementedException("");
	}

	@Override
	public String getLocalAddr() {
		throw new NotImplementedException("");
	}

	@Override
	public int getLocalPort() {
		throw new NotImplementedException("");
	}

	@Override
	public ServletContext getServletContext() {
		return null;
	}

	@Override
	public AsyncContext startAsync() throws IllegalStateException {
		return null;
	}

	@Override
	public AsyncContext startAsync(ServletRequest servletRequest, ServletResponse servletResponse) throws IllegalStateException {
		return null;
	}

	@Override
	public boolean isAsyncStarted() {
		return false;
	}

	@Override
	public boolean isAsyncSupported() {
		return false;
	}

	@Override
	public AsyncContext getAsyncContext() {
		return null;
	}

	@Override
	public DispatcherType getDispatcherType() {
		return null;
	}

}
