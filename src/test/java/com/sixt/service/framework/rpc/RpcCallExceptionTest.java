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

    @Test
    public void testFromJson_ErrorWithoutCategory() {
        String error = "{\"id\":\"com.sixt.service.foobar\",\"code\":500,\"message\":\"error message\",\"status\":\"Internal Server Error\"}";
        RpcCallException ex = RpcCallException.fromJson(error);
        assertThat(ex.getCategory()).isEqualTo(RpcCallException.Category.InternalServerError);
    }

    @Test
    public void testFromJson_ErrorWithDetailInsteadOfMessage() {
        String error = "{\"id\":\"com.sixt.service.foobar\",\"code\":500,\"detail\":\"error message\",\"status\":\"Internal Server Error\"}";
        RpcCallException ex = RpcCallException.fromJson(error);
        assertThat(ex.getCategory()).isEqualTo(RpcCallException.Category.InternalServerError);
        assertThat(ex.getMessage()).isEqualTo("error message");
    }

    @Test
    public void testFromInvalidJson() {
        String error = "invalid json";
        RpcCallException ex = RpcCallException.fromJson(error);
        assertThat(ex).isNull();
    }

    @Test
    public void testFromJson_mediumBadData() {
        String error = "{\"sxerror\":\"1\",\"source\":\"go.micro.client\",\"category\":408,\"code\":\"unexpected_error\",\"message\":\"call timeout: context deadline exceeded\",\"data\":\"'*errors.Error' with error '{\\\"id\\\":\\\"go.micro.client\\\",\\\"code\\\":408,\\\"detail\\\":\\\"call timeout: context deadline exceeded\\\",\\\"status\\\":\\\"Request Timeout\\\"}' cannot be mapped to a known representation. Data: \\u0026errors.Error{Id:\\\"go.micro.client\\\", Code:408, Detail:\\\"call timeout: context deadline exceeded\\\", Status:\\\"Request Timeout\\\"}\",\"retriable\":false}";
        RpcCallException ex = RpcCallException.fromJson(error);
        assertThat(ex).isNotNull();
        assertThat(ex.getCategory()).isEqualTo(RpcCallException.Category.InternalServerError); // Switch to default Category when 
        assertThat(ex.getErrorCode()).isEqualTo("unexpected_error");
        assertThat(ex.getMessage()).isEqualTo("call timeout: context deadline exceeded");
        assertThat(ex.getData()).isNotNull();
        assertThat(ex.isRetriable()).isFalse();
    }

}
