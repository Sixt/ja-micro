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

import com.codahale.metrics.MetricRegistry;
import com.sixt.service.framework.MethodHandlerDictionary;
import com.sixt.service.framework.ServiceProperties;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import javax.servlet.http.HttpServletRequest;
import java.util.*;

public class RpcHandlerTest {

    @Test
    public void itShouldFilterHeadersToLeaveOnlyFirstValue() {
        // Given
        RpcHandlerTest_RpcHandlerMock rpcHandlerMock = new RpcHandlerTest_RpcHandlerMock(null, null, null);

        Map<String, Set<String>> headers = new TreeMap<>();

        Set<String> values = new TreeSet<>();
        values.add("value1_1");
        headers.put("single_key_1", values);

        values = new TreeSet<>();
        values.add("value2_1");
        values.add("value2_2");
        headers.put("multiple_key_1", values);

        values = new TreeSet<>();
        values.add("value3_3");
        values.add("value3_4");
        values.add("value3_5");
        headers.put("multiple_key_2", values);

        values = new TreeSet<>();
        values.add("value4_1");
        headers.put("single_key_2", values);

        HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        Mockito.when(request.getHeaderNames())
                .thenReturn(
                        new RpcHandlerTest_IteratorEnumeration<>(headers.keySet().iterator())
                );
        Mockito.when(request.getHeaders("single_key_1"))
                .thenReturn(
                        new RpcHandlerTest_IteratorEnumeration<>(headers.get("single_key_1").iterator())
                );
        Mockito.when(request.getHeaders("multiple_key_1"))
                .thenReturn(
                        new RpcHandlerTest_IteratorEnumeration<>(headers.get("multiple_key_1").iterator())
                );
        Mockito.when(request.getHeaders("multiple_key_2"))
                .thenReturn(
                        new RpcHandlerTest_IteratorEnumeration<>(headers.get("multiple_key_2").iterator())
                );
        Mockito.when(request.getHeaders("single_key_2"))
                .thenReturn(
                        new RpcHandlerTest_IteratorEnumeration<>(headers.get("single_key_2").iterator())
                );

        // When
        Map<String, String> filteredHeaders = rpcHandlerMock.gatherHttpHeaders(request);

        // Then
        Assert.assertEquals("First single key should match value1_1",   filteredHeaders.get("single_key_1"),   "value1_1");
        Assert.assertEquals("Second single key should match value4_1",  filteredHeaders.get("single_key_2"),   "value4_1");
        Assert.assertEquals("First multiple key should match value2_1",  filteredHeaders.get("multiple_key_1"), "value2_1");
        Assert.assertEquals("Second multiple key should match value3_3", filteredHeaders.get("multiple_key_2"), "value3_3");
    }

    public class RpcHandlerTest_RpcHandlerMock
            extends RpcHandler {

        public RpcHandlerTest_RpcHandlerMock(MethodHandlerDictionary handlers,
                                             MetricRegistry registry,
                                             RpcHandlerMetrics handlerMetrics) {
            super(handlers, registry, handlerMetrics, new ServiceProperties());
        }

        public Map<String, String> gatherHttpHeaders(HttpServletRequest request) {
            return super.gatherHttpHeaders(request);
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
}
