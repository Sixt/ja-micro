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

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@RunWith(MockitoJUnitRunner.class)
public class RpcServletTest {

    private RpcServlet cut;

    @Mock
    private JsonHandler mockJsonHandler;

    @Mock
    private ProtobufHandler mockProtobufHandler;

    @Mock
    private HttpServletRequest mockHttpServletRequest;

    @Mock
    private HttpServletResponse mockHttpServletResponse;

    @Before
    public void setup() {
        cut = new RpcServlet(mockJsonHandler, mockProtobufHandler);
    }

    @Test
    public void doHead() throws Exception {
        // given

        // when
        cut.doHead(mockHttpServletRequest, mockHttpServletResponse);
    }

    @Test
    public void doGet() throws Exception {

    }

    @Test
    public void doPost_CannotServe_ServiceUnavailable() throws Exception {
        // given
        cut.canServeRequests.set(false);

        // when
        cut.doPost(mockHttpServletRequest, mockHttpServletResponse);

        // then
        verify(mockHttpServletResponse).setStatus(Mockito.eq(HttpServletResponse.SC_SERVICE_UNAVAILABLE));
        verifyNoMoreInteractions(mockJsonHandler, mockProtobufHandler);
    }

}