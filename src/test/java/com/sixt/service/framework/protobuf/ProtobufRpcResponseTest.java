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
import com.sixt.service.framework.rpc.RpcCallException;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

public class ProtobufRpcResponseTest {

    @Test
    public void testNegativeHeaderLength() throws Exception {
        byte[] data = Ints.toByteArray(-1);

        Throwable thrown = catchThrowable( () ->  new ProtobufRpcResponse(data));

        assertThat(thrown).isExactlyInstanceOf(RpcCallException.class);
        assertThat(thrown).hasFieldOrPropertyWithValue("category", RpcCallException.Category.InternalServerError);
    }

    @Test
    public void testTooBigHeaderLength() throws Exception {
        byte[] data = Ints.toByteArray(19_000_000);

        Throwable thrown = catchThrowable( () ->  new ProtobufRpcResponse(data));

        assertThat(thrown).isExactlyInstanceOf(RpcCallException.class);
        assertThat(thrown).hasFieldOrPropertyWithValue("category", RpcCallException.Category.InternalServerError);
    }

    @Test
    public void testEnsurePrintableShort() throws Exception {
        byte[] testData = new byte[65];
        for (int i = 0; i < 65; i++) {
            testData[i] = (byte) i;
        }
        String result = ProtobufRpcResponse.ensurePrintable(testData, 70);
        assertThat(result).isEqualTo("[0x0, 0x1, 0x2, 0x3, 0x4, 0x5, 0x6, 0x7, 0x8, 0x9, 0xa, 0xb, " +
                "0xc, 0xd, 0xe, 0xf, 0x10, 0x11, 0x12, 0x13, 0x14, 0x15, 0x16, 0x17, 0x18, 0x19, 0x1a, " +
                "0x1b, 0x1c, 0x1d, 0x1e, 0x1f, 0x20, !, \", #, $, %, &, ', (, ), *, +, ,, -, ., /, 0, " +
                "1, 2, 3, 4, 5, 6, 7, 8, 9, :, ;, <, =, >, ?, @]");
    }
}
