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

import com.sixt.service.framework.protobuf.ProtobufRpcResponse;
import org.apache.commons.lang3.ArrayUtils;
import org.eclipse.jetty.client.api.ContentResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProtobufRpcCallExceptionDecoder implements RpcCallExceptionDecoder {

    private static final Logger logger = LoggerFactory.getLogger(ProtobufRpcCallExceptionDecoder.class);

    @Override
    public RpcCallException decodeException(ContentResponse response) throws RpcCallException {
        try {
            if (response != null) {
                byte[] data = response.getContent();
                if (ArrayUtils.isEmpty(data)) {
                    logger.warn("Unable to decode: empty response received");
                    return new RpcCallException(RpcCallException.Category.InternalServerError,
                            "Empty response received");
                }
                ProtobufRpcResponse pbResponse = new ProtobufRpcResponse(data);
                String error = pbResponse.getErrorMessage();
                if (error != null) {
                    return RpcCallException.fromJson(error);
                }
            }
        } catch (Exception ex) {
            logger.warn("Caught exception decoding protobuf response exception", ex);
            throw new RpcCallException(RpcCallException.Category.InternalServerError,
                    RpcCallExceptionDecoder.exceptionToString(ex));
        }
        return null;
    }



}
