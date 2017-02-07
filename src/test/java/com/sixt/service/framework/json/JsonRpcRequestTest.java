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

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonPrimitive;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class JsonRpcRequestTest {

    @Test
    public void gettersAndSetters() {
        JsonArray array = new JsonArray();
        JsonRpcRequest req = new JsonRpcRequest(null, "mefod", array);
        assertThat(req.getMethod()).isEqualTo("mefod");
        assertThat(req.getParams()).isEqualTo(array);
    }

    @Test
    public void getIdAsString_SomeIdAsNumber_TheNumberAsString() {
        // given
        JsonElement jsonElement = new JsonPrimitive(20);
        JsonRpcRequest req = new JsonRpcRequest(jsonElement, "something", null);

        // when
        String result = req.getIdAsString();

        // then
        assertThat(result).isNotNull();
        assertThat(result).isEqualTo("20");
    }

    @Test
    public void getIdAsString_NullId_Null() {
        // given
        JsonRpcRequest req = new JsonRpcRequest(null, "something", null);

        // when
        String result = req.getIdAsString();

        // then
        assertThat(result).isNull();
    }

    @Test
    public void getIdAsString_NullJson_Null() {
        // given
        JsonRpcRequest req = new JsonRpcRequest(JsonNull.INSTANCE, "something", null);

        // when
        String result = req.getIdAsString();

        // then
        assertThat(result).isNull();
    }
}
