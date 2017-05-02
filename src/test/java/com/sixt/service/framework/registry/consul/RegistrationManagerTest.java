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

package com.sixt.service.framework.registry.consul;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.protobuf.Message;
import com.sixt.service.configuration.api.ConfigurationOuterClass;
import com.sixt.service.framework.OrangeContext;
import com.sixt.service.framework.ServiceMethodHandler;
import com.sixt.service.framework.ServiceProperties;
import com.sixt.service.framework.protobuf.FrameworkTest.MessageWithMap;
import com.sixt.service.framework.protobuf.RpcEnvelope;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.Inflater;

import static org.assertj.core.api.Assertions.assertThat;

public class RegistrationManagerTest {

    private RegistrationManager manager;

    @Before
    public void setup() {
        ServiceProperties props = new ServiceProperties();
        manager = new RegistrationManager(props, null);
        Map<String, ServiceMethodHandler<? extends Message,
                ? extends Message>> handlers = new HashMap<>();
        handlers.put("test", new DummyMethodHandler());
        manager.setRegisteredHandlers(handlers);
    }

    @Test
    public void verifyRegistrationTags() throws IOException {
        JsonObject json = manager.buildJsonRequest();
        JsonArray tags = (JsonArray) json.get("Tags");
        assertThat(tags).hasSize(7);
        assertThat(tags).contains(new JsonPrimitive(
                "t-789cab562aa92c4855b2522acdcbcecb2fcf53aa05003d1c068d"));
        assertThat(tags).contains(new JsonPrimitive(
                "t-789cab562a294acc2b2ec82f2a51b252ca28292950aa05004f930768"));
        assertThat(tags).contains(new JsonPrimitive(
                "t-789cab564a2acacf4e2d52b252ca28292950aa0500364d0600"));
        assertThat(tags).contains(new JsonPrimitive(
                "t-789cab562a4e2d2a4b2d52b2522a2a4856aa050030dd0597"));
        assertThat(tags).contains(new JsonPrimitive(
                "t-789cab562a4a4dcf2c2e29aa54b2524acecf2b2ecd51aa0500560f07c8"));
        assertThat(tags).contains(new JsonPrimitive(
                "v-789c33d033d033d04d83000016c103e4"));
        assertThat(tags).contains(new JsonPrimitive("e-789cc58fbb0e02211045ff656a4a63c167d81" +
                "a63461957121eeb001bcd867f179275616d2ced269739c3b93338b4041222850802981ea94e" +
                "72fe3c1c9644407c8ddb6042532690c77539104ffa4a674bf1ee55634264ed860e71c9982c3" +
                "aaedc740574c95e881b78d34f52fbdd3779ca55358cde05dab82e5127bb26ffb36d24317bfe" +
                "f5512d57945061c45aae6c11da7a1e4d6992f31bda579619"));
    }

    @Test
    public void verifyRegistrationWithMap() throws IOException {
        Map<String, ServiceMethodHandler<? extends Message,
                ? extends Message>> handlers = new HashMap<>();
        handlers.put("withMap", new MessageWithMapMethodHandler());
        manager.setRegisteredHandlers(handlers);
        manager.buildJsonRequest();
    }

    @Test
    public void verifyNestedProtobufDescriptor() throws Exception {
        String result = manager.getProtobufClassFieldDescriptions(ConfigurationOuterClass.FullPath.class);
        assertThat(result).isEqualTo("{\"name\":\"service\",\"type\":\"string\"," +
                "\"values\":null},{\"name\":\"name\",\"type\":\"string\",\"values\"" +
                ":null},{\"name\":\"detail\",\"type\":\"VariantDetail\",\"values\":" +
                "[{\"name\":\"minVersion\",\"type\":\"string\",\"values\":null},{" +
                "\"name\":\"maxVersion\",\"type\":\"string\",\"values\":null},{" +
                "\"name\":\"instances\",\"type\":\"string\",\"values\":null},{" +
                "\"name\":\"value\",\"type\":\"string\",\"values\":null},{\"name\":" +
                "\"isEncrypted\",\"type\":\"bool\",\"values\":null}]}");
    }

    @Test
    public void verifyNestedEndpoint() throws Exception {
        String result = manager.getProtobufClassFieldDescriptions(ConfigurationOuterClass.Import.class);
        assertThat(result).isEqualTo("{\"name\":\"values\",\"type\":\"ImportItem\"," +
                "\"values\":[{\"name\":\"name\",\"type\":\"string\",\"values\":null},{" +
                "\"name\":\"value\",\"type\":\"VariantDetail\",\"values\":[{\"name\":" +
                "\"minVersion\",\"type\":\"string\",\"values\":null},{\"name\":\"maxVersion\"," +
                "\"type\":\"string\",\"values\":null},{\"name\":\"instances\",\"type\":" +
                "\"string\",\"values\":null},{\"name\":\"value\",\"type\":\"string\",\"values" +
                "\":null},{\"name\":\"isEncrypted\",\"type\":\"bool\",\"values\":null}]}]}");
    }

    @Test
    public void decodeEndpointTag() throws Exception {
        String rawData = "789c948f310bc2301085ffcbcdc541b76e220eba08555cc4e1b4a706ae694d2" +
                "ec522f9efa650dad256c42de47d8f7bdf1b346604311ce9a1ae4ccb1215e345b1926a969" +
                "02553124460e8e9c80ac42ddf64491344205531f95f228717c4a7e1a94ddab5ac18a5ef3" +
                "d5a3b661fb5956dee8ca6ea9fca41b8839596c57cc89e7d2d668b5c5b9a326b92b15a1b8" +
                "cdd5679da6bec05c5d9ef1bd7af4285053bfd4bab9e9a91608a82f5d440116601bf21872" +
                "5de7f020000ffff4ed99cb2";
        int length = rawData.length();
        assertThat(length % 2).isZero();
        byte zippedData[] = new byte[length / 2];
        for (int i = 0; i < length / 2; i++) {
            zippedData[i] = decodeChars(rawData.charAt(i * 2), rawData.charAt((i * 2) + 1));
        }
        byte result[] = new byte[8192];
        Inflater inflater = new Inflater();
        inflater.setInput(zippedData);
        int size = inflater.inflate(result);
        String retval = new String(Arrays.copyOfRange(result, 0, size));
        assertThat(retval).isEqualTo("{\"name\":\"VehicleAvailability.Reserve\",\"request\":{\"name\":" +
                "\"ReserveRequest\",\"type\":\"ReserveRequest\",\"values\":[{\"name\":\"VehicleId\"," +
                "\"type\":\"string\",\"values\":null},{\"name\":\"JourneyId\",\"type\":\"string\"," +
                "\"values\":null},{\"name\":\"Ttl\",\"type\":\"int32\",\"values\":null}]},\"response\":{" +
                "\"name\":\"ReserveResponse\",\"type\":\"ReserveResponse\",\"values\":[{\"name\":" +
                "\"Code\",\"type\":\"Status\",\"values\":null},{\"name\":\"ExpiresOn\",\"type\":" +
                "\"string\",\"values\":null}]},\"metadata\":{\"stream\":\"false\"}}");
    }

    private byte decodeChars(char upper, char lower) {
        return (byte) ((getIntValue(upper) << 4) + getIntValue(lower));
    }

    private int getIntValue(char c) {
        char[] hexArray = "0123456789abcdef".toCharArray();
        for (int i = 0; i < hexArray.length; i++) {
            if (hexArray[i] == c) {
                return i;
            }
        }
        return -1;
    }

    class DummyMethodHandler implements ServiceMethodHandler<RpcEnvelope.Request, RpcEnvelope.Response> {
        @Override
        public RpcEnvelope.Response handleRequest(RpcEnvelope.Request request, OrangeContext ctx) {
            return null;
        }
    }

    class MessageWithMapMethodHandler implements ServiceMethodHandler<MessageWithMap, MessageWithMap> {
        @Override
        public MessageWithMap handleRequest(MessageWithMap request, OrangeContext ctx) {
            return null;
        }
    }

}
