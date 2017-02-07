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

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.eclipse.jetty.client.api.ContentResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JsonRpcCallExceptionDecoder implements RpcCallExceptionDecoder {

    private static final Logger logger = LoggerFactory.getLogger(JsonRpcCallExceptionDecoder.class);

    @Override
    public RpcCallException decodeException(ContentResponse response) {
        if (response != null) {
            try {
                JsonObject json = (JsonObject) new JsonParser().parse(response.getContentAsString());
                JsonElement error = json.get("error");
                if (error != null) {
                    return RpcCallException.fromJson(error.toString());
                }
            } catch (Exception ex) {
                logger.warn("Caught exception decoding json response exception", ex);
            }
        }
        return null;
    }

}
