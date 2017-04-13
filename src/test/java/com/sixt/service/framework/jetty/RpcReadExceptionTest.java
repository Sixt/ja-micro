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
package com.sixt.service.framework.jetty;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import javax.servlet.ReadListener;
import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServletRequest;
import java.io.*;
import java.util.*;

import static org.mockito.Mockito.when;

public class RpcReadExceptionTest {

    public String decode(String base)  {
        try {
            String r = new String(Base64.getDecoder().decode(base));
            return r;
        } catch (IllegalArgumentException a) {
            return "";
        }
    }


    public void testBodyReading(String first, String second) throws IOException {
        ServletInputStream x = (ServletInputStream) new RpcHandlerTest_InputStream(second);
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        Map<String, Set<String>> headers = new TreeMap<>();
        when(request.getHeaderNames())
                .thenReturn(
                        new RpcReadExceptionTest.RpcHandlerTest_IteratorEnumeration<>(headers.keySet().iterator())
                );

        when(request.getInputStream()).thenReturn(x);
        when(request.getRequestURL())
                .thenReturn(new StringBuffer("http://fizz.buzz"));

        RpcReadException rpcReadException = new RpcReadException(first.getBytes(), x, "i am a message");
        String json = rpcReadException.toJson(request);

        try {
            JsonElement root = new JsonParser().parse(json);
            JsonObject jsob = root.getAsJsonObject();
            JsonElement b = jsob.get("request_body");
            Assert.assertNotNull(b);
            Assert.assertEquals(first+second, this.decode(b.getAsString()));
        } catch (Exception ex) {
            Assert.fail(ex.toString());
        }
    }

    @Test
    public void testRequestExtractionBody() throws IOException {
        testBodyReading("beg", "inning");
        testBodyReading("", "inning");
        testBodyReading("beg", "");
    }

    @Test
    public void testRequestExtraction() throws IOException {
        Map<String, Set<String>> headers = new TreeMap<>();
        Set<String> values = new TreeSet<>();
        values.add("value");
        headers.put("theKey", values);

        ServletInputStream x = (ServletInputStream) new RpcHandlerTest_InputStream("inning");
        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        when(request.getHeaderNames())
                .thenReturn(
                        new RpcReadExceptionTest.RpcHandlerTest_IteratorEnumeration<>(headers.keySet().iterator())
                );

        when(request.getHeader("theKey"))
                .thenReturn("value");

        when(request.getRequestURL())
                .thenReturn(new StringBuffer("http://fizz.buzz"));

        when(request.getInputStream()).thenReturn(x);

        String json = "";
        Boolean caught = false;
        try {
            throw new RpcReadException("beg".getBytes(),request.getInputStream(),"i am a message");
        } catch (RpcReadException ex) {
            json = ex.toJson(request);
            caught = true;
        } catch (Exception ex) {
            Assert.fail(ex.toString());
        }
        Assert.assertTrue(caught);
        Assert.assertNotEquals("", json);

        try {
            JsonElement root = new JsonParser().parse(json);
            JsonObject jsob = root.getAsJsonObject();
            Assert.assertNotNull(jsob);
            JsonElement val = jsob.get("request_header_theKey");
            Assert.assertNotNull("no value for header", val);
            Assert.assertEquals("value", val.getAsString());

            JsonElement rb = jsob.get("read_body");
            Assert.assertNotNull(rb);
            Assert.assertEquals("success", rb.getAsString());

            JsonElement body = jsob.get("request_body");
            Assert.assertNotNull(body);
            Assert.assertEquals("beginning", this.decode(body.getAsString()));

            JsonElement reqUrl = jsob.get("request_url");
            Assert.assertNotNull(reqUrl);
            Assert.assertEquals("http://fizz.buzz", reqUrl.getAsString());

            JsonElement exMsg = jsob.get("exception_message");
            Assert.assertNotNull(body);
            Assert.assertEquals("i am a message", exMsg.getAsString());

        } catch (Exception ex) {
            Assert.fail(ex.toString());
        }

    }

    class RpcHandlerTest_IteratorEnumeration<E> implements Enumeration<E> {
        private final Iterator<E> iterator;

        public RpcHandlerTest_IteratorEnumeration(Iterator<E> iterator)
        {
            this.iterator = iterator;
        }

        public E nextElement() {
            return iterator.next();
        }

        public boolean hasMoreElements() {
            return iterator.hasNext();
        }

    }

    class RpcHandlerTest_InputStream extends ServletInputStream {
        ByteArrayInputStream inputStream;
        RpcHandlerTest_InputStream(String s) {
            this.inputStream = new ByteArrayInputStream(s.getBytes());
        }

        public boolean isReady() {
            return true;
        }

        public void setReadListener(ReadListener readListener) {}

        public boolean isFinished() {
            return this.inputStream.available() == 0;
        }

        public int read() {
            return this.inputStream.read();
        }

    }
}
