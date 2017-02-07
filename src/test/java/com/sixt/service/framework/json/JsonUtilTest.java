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

package com.sixt.service.framework.json;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Test;

import javax.servlet.http.HttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

public class JsonUtilTest {

    @Test
    public void verifyParseResponse() {
        String input = "{\"error\":\"errror\",\"result\":{\"foo\":\"bar\"}}";
        JsonRpcResponse response = JsonUtil.parseJsonRpcResponse(input);
        assertThat(response.getError().getAsString()).isEqualTo("errror");
        assertThat(response.getResult().toString()).isEqualTo("{\"foo\":\"bar\"}");
        assertThat(response.getStatusCode()).isEqualTo(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    }

    @Test
    public void testUtfEncoding() {
        String input = "{\"name\":\"kr\\u00F6\\u00FC\\u00E4mer\"}";
        JsonParser parser = new JsonParser();
        JsonObject obj = (JsonObject) parser.parse(input);
        assertThat(obj.get("name").getAsString()).isEqualTo("kröüämer");
    }

    @Test
    public void testJsonResponseWithRpcException() {
        String jsonRpcException = "{\"category\":400,\"message\":\"You fool!\"," +
                "\"source\":\"com.sixt.service.foobar\",\"code\":\"SERVICE_PROTOBUF_ENUM\"," +
                "\"data\":\"my data\",\"retriable\":true}";
        String input = "{\"error\":" + jsonRpcException + ",\"result\":{\"foo\":\"bar\"}}";
        JsonRpcResponse response = JsonUtil.parseJsonRpcResponse(input);
        assertThat(response.getError()).isNotNull();
        assertThat(response.getStatusCode()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
    }

    @Test
    public void testJsonResponseWithSuccessCode() {
        String input = "{\"error\":\"\",\"result\":{\"foo\":\"bar\"}}";
        JsonRpcResponse response = JsonUtil.parseJsonRpcResponse(input);
        assertThat(response.getStatusCode()).isEqualTo(HttpServletResponse.SC_OK);
    }

    @Test
    public void testJsonArrayError() {
        String input = "{\"error\":\"[{},{}]\",\"result\":{\"foo\":\"bar\"}}";
        JsonRpcResponse response = JsonUtil.parseJsonRpcResponse(input);
        assertThat(response.getStatusCode()).isEqualTo(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    }
}
