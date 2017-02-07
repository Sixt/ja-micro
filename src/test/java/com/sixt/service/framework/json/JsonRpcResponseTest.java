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

import com.google.gson.JsonNull;
import com.google.gson.JsonPrimitive;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class JsonRpcResponseTest {

    @Test
    public void verifyToString() {
        JsonRpcResponse response = new JsonRpcResponse(new JsonPrimitive(42), JsonNull.INSTANCE,
                new JsonPrimitive("none"), 200);
        assertThat(response.getId()).isEqualTo(new JsonPrimitive(42));
        assertThat(response.toString()).contains("[id=42,result=null,error=\"none\"]");
    }

}
