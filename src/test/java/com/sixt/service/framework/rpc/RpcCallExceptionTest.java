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

package com.sixt.service.framework.rpc;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class RpcCallExceptionTest {

    @Test
    public void testJsonEncodingDecoding() throws Exception {
        RpcCallException exception = new RpcCallException(RpcCallException.Category.BadRequest,
                "You fool!").withData("my data").withErrorCode("SERVICE_PROTOBUF_ENUM").
                withRetriable(true).withSource("com.sixt.service.foobar");
        String json = exception.toString();
        assertThat(json).isEqualTo("{\"category\":400,\"message\":\"You fool!\"," +
                "\"source\":\"com.sixt.service.foobar\",\"code\":\"SERVICE_PROTOBUF_ENUM\"," +
                "\"data\":\"my data\",\"retriable\":true}");
        RpcCallException.fromJson(json);
    }

    @Test
    public void testJsonToString() {
        RpcCallException ex = new RpcCallException(RpcCallException.Category.InternalServerError, "feckoff");
        System.out.println(ex.toString());
    }

}
