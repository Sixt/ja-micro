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

package com.sixt.service.framework.protobuf;

import com.google.common.primitives.Ints;
import com.google.protobuf.Message;

/**
 * Micro framework implementation that encapsulates the protobuf
 * envelope and body for a request
 */
public class ProtobufRpcRequest {

    private String serviceMethod;
    private Long sequenceNumber;
    private Message payload;

    public ProtobufRpcRequest(String serviceMethod, Message payload) {
        this.serviceMethod = serviceMethod;
        this.payload = payload;
    }

    public Message getPayload() {
        return payload;
    }

    public byte[] getProtobufData() {
        byte[] envelopeData = getEnvelope().toByteArray();
        byte[] payloadData = getPayload().toByteArray();
        int size = envelopeData.length + payloadData.length + 8;
        byte[] retval = new byte[size];
        int offset = 0;
        System.arraycopy(Ints.toByteArray(envelopeData.length), 0, retval, offset, 4);
        offset += 4;
        System.arraycopy(envelopeData, 0, retval, offset, envelopeData.length);
        offset += envelopeData.length;
        System.arraycopy(Ints.toByteArray(payloadData.length), 0, retval, offset, 4);
        offset += 4;
        System.arraycopy(payloadData, 0, retval, offset, payloadData.length);

        return retval;
    }

    private RpcEnvelope.Request getEnvelope() {
        RpcEnvelope.Request.Builder builder
            = RpcEnvelope.Request.newBuilder().setServiceMethod(serviceMethod);

        if (sequenceNumber != null) {
            builder.setSequenceNumber(sequenceNumber);
        }

        return builder.build();
    }
}
