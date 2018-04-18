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

import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.protobuf.Message;
import com.sixt.service.configuration.api.ConfigurationOuterClass;
import org.junit.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

//TODO: enhance this test to cover and verify all of the types for
//      serialization / deserialization

public class ProtobufUtilTest {

    @Test
    public void fromJson_SetRepeated_YieldsTheSameValues() throws Exception {
        // given
        FrameworkTest.Foobar.Builder builder = FrameworkTest.Foobar.newBuilder();
        JsonParser parser = new JsonParser();
        JsonObject json = (JsonObject) parser.parse("{\"blah\":[\"a\",\"b\",\"c\"]}");

        // when
        FrameworkTest.Foobar message = (FrameworkTest.Foobar) ProtobufUtil.fromJson(builder, json);

        // then
        List<String> blahArray = message.getBlahList();
        assertThat(blahArray).hasSize(3);
        assertThat(blahArray).containsOnly("a", "b", "c");
    }

    @Test
    public void testNullSubmessage_YieldsDefaultInstance() throws Exception {
        FrameworkTest.SerializationTest.Builder builder = FrameworkTest.SerializationTest.newBuilder();
        String jsonInput = "{\"id\":\"a\",\"id2\":\"b\",\"id4\":\"c\",\"sub_message\":null}";
        JsonObject json = (JsonObject) new JsonParser().parse(jsonInput);
        FrameworkTest.SerializationTest message = (FrameworkTest.SerializationTest) ProtobufUtil.fromJson(builder, json);

        assertThat(message.getSubMessage()).isEqualTo(FrameworkTest.SerializationSubMessage.getDefaultInstance());
    }

    @Test
    public void testMessageWithEnums() {
        FrameworkTest.MessageWithEnum message = FrameworkTest.MessageWithEnum.newBuilder().
                setError(FrameworkTest.Error.INVALID_VEHICLE_ID).build();
        JsonObject messageStr = ProtobufUtil.protobufToJson(message);
        assertThat(messageStr.get("error").getAsString()).isEqualTo("INVALID_VEHICLE_ID");
    }

    @Test
    public void protobufToJson_NullMessage_YieldsToEmptyJson() {
        // given

        // when
        JsonObject result = ProtobufUtil.protobufToJson(null);

        // then
        assertThat(result).isNotNull();
        assertThat(result).isEqualTo(new JsonObject());
    }

    @Test
    public void protobufToJson_SimpleMessage_YieldsSuccess() {
        // given
        FrameworkTest.SerializationTest message = FrameworkTest.SerializationTest.newBuilder()
                .setId("id")
                .setId2("id2")
                .build();

        // when
        JsonObject result = ProtobufUtil.protobufToJson(message);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getAsJsonPrimitive("id").getAsString()).isEqualTo("id");
        assertThat(result.getAsJsonPrimitive("id2").getAsString()).isEqualTo("id2");
    }

    @Test
    public void protobufToJson_RepeatedFields_YieldsSuccess() {
        // given
        FrameworkTest.SerializationTest message = FrameworkTest.SerializationTest.newBuilder()
                .setSubMessage(
                        FrameworkTest.SerializationSubMessage.newBuilder()
                                .setId("TheId")
                                .build()
                ).setId4("The fourth id")
                .build();

        // when
        JsonObject result = ProtobufUtil.protobufToJson(message);

        // then
        assertThat(result).isNotNull();
        assertThat(result.get("sub_message").getAsJsonObject().get("id").getAsString()).isEqualTo("TheId");
        assertThat(result.getAsJsonPrimitive("id4").getAsString()).isEqualTo("The fourth id");
    }

    @Test
    public void protobufToJson_EnumField_YieldsSuccess() throws Exception {
        // given
        FrameworkTest.MessageWithEnum message = FrameworkTest.MessageWithEnum.newBuilder()
                .setError(FrameworkTest.Error.INVALID_VEHICLE_ID)
                .build();

        // when
        JsonObject result = ProtobufUtil.protobufToJson(message);

        // then
        assertThat(result.getAsJsonPrimitive("error").getAsString()).isEqualTo(FrameworkTest.Error.INVALID_VEHICLE_ID.toString());
    }

    @Test
    public void jsonToProtobuf_EnumField_YieldsSuccess() throws Exception {
        // given
        String json = "{\"error\":\"INVALID_VEHICLE_ID\"}";

        // when
        FrameworkTest.MessageWithEnum message = ProtobufUtil.jsonToProtobuf(json, FrameworkTest.MessageWithEnum.class);

        // then
        assertThat(message.getError()).isEqualTo(FrameworkTest.Error.INVALID_VEHICLE_ID);
    }

    @Test
    public void jsonToProtobuf_NullInput_ReturnNull() throws Exception {
        // given
        String nullString = null;

        // when
        FrameworkTest.SerializationTest message = ProtobufUtil.jsonToProtobuf(nullString, FrameworkTest.SerializationTest.class);

        // then
        assertThat(message).isNull();
    }

    @Test
    public void jsonToProtobuf_EmptyJsonObject_YieldDefaultInstance() throws Exception {
        // given
        String json = "{}";

        // when
        FrameworkTest.SerializationTest message = ProtobufUtil.jsonToProtobuf(json, FrameworkTest.SerializationTest.class);

        // then
        assertThat(message).isEqualTo(FrameworkTest.SerializationTest.getDefaultInstance());
    }

    @Test // this behaviour is deprecated and will be removed in the next major release
    public void jsonToProtobuf_EmptyString_YieldDefaultInstance() throws Exception {
        // given
        String json = "";

        // when
        FrameworkTest.SerializationTest message = ProtobufUtil.jsonToProtobuf(json, FrameworkTest.SerializationTest.class);

        // then
        assertThat(message).isEqualTo(FrameworkTest.SerializationTest.getDefaultInstance());
    }

    @Test // this behaviour is deprecated and will be removed in the next major release
    public void jsonToProtobuf_InvalidJson_YieldDefaultInstance() throws Exception {
        // given
        String json = "invalidJson";

        // when
        FrameworkTest.SerializationTest message = ProtobufUtil.jsonToProtobuf(json, FrameworkTest.SerializationTest.class);

        // then
        assertThat(message).isEqualTo(FrameworkTest.SerializationTest.getDefaultInstance());
    }

    @Test
    public void jsonToProtobuf_SimpleJsonToProtobufGeneralMessage_YieldsSuccess() {
        // given
        final String json = "{\"id\":\"theId\"}";

        // when
        Message message = ProtobufUtil.jsonToProtobuf(json, FrameworkTest.SerializationTest.class);

        // then
        assertThat(message).isInstanceOf(FrameworkTest.SerializationTest.class);
        assertThat(((FrameworkTest.SerializationTest) message).getId()).isEqualTo("theId");
    }

    @Test
    public void jsonToProtobuf_SimpleJsonToProtobufSpecificMessage_YieldsSuccess() {
        // given
        final String json = "{\"id\":\"theId\"}";

        // when
        FrameworkTest.SerializationTest message = ProtobufUtil.jsonToProtobuf(json, FrameworkTest.SerializationTest.class);

        // then
        assertThat(message.getId()).isEqualTo("theId");
    }

    @Test
    public void protobufToJson_MessageWithMap_Success() throws Exception {
        // given
        FrameworkTest.MessageWithMap messageWithMap = FrameworkTest.MessageWithMap.newBuilder()
                .putErrorMap("first-error", FrameworkTest.Error.INVALID_VEHICLE_ID)
                .putErrorMap("second-error", FrameworkTest.Error.NO_ERROR)
                .build();

        // when
        JsonObject jsonObject = ProtobufUtil.protobufToJson(messageWithMap);

        // then
        JsonObject error_map = jsonObject.getAsJsonObject("error_map");
        assertThat(error_map.size()).isEqualTo(2);
        assertThat(error_map.getAsJsonPrimitive("first-error").getAsString())
                .isEqualTo(FrameworkTest.Error.INVALID_VEHICLE_ID.toString());
        assertThat(error_map.getAsJsonPrimitive("second-error").getAsString())
                .isEqualTo(FrameworkTest.Error.NO_ERROR.toString());
    }

    @Test
    public void jsonToProtobuf_SimpleJsonWithUnknownField_MessageEmpty() {
        // given
        final String json = "{\"fahrzeug\":\"auto\"}"; // key 'fahrzeug' does not exist in protobuf message FrameworkTest.SerializationTest.

        // when
        FrameworkTest.SerializationTest message = ProtobufUtil.jsonToProtobuf(json, FrameworkTest.SerializationTest.class);

        // then
        assertThat(message.getId()).isEmpty();
    }

    @Test
    public void jsonToProtobuf_SimpleJsonWithEmptyUnknownField_MessageEmpty() {
        // given
        final String json = "{\"fahrzeug\": {}}"; // key 'fahrzeug' does not exist in protobuf message FrameworkTest.SerializationTest.

        // when
        FrameworkTest.SerializationTest message = ProtobufUtil.jsonToProtobuf(json, FrameworkTest.SerializationTest.class);

        // then
        assertThat(message.getId()).isEmpty();
    }

    @Test
    public void jsonToProtobuf_MessageWithMap_Success() throws Exception {
        // given
        String jsonString = "{\"error_map\":{\"first-error\":\"INVALID_VEHICLE_ID\",\"second-error\":\"NO_ERROR\"}}";

        // when
        FrameworkTest.MessageWithMap message = ProtobufUtil.jsonToProtobuf(jsonString, FrameworkTest.MessageWithMap.class);

        // then
        assertThat(message.getErrorMapCount()).isEqualTo(2);
        assertThat(message.getErrorMapMap().get("first-error")).isEqualTo(FrameworkTest.Error.INVALID_VEHICLE_ID);
        assertThat(message.getErrorMapMap().get("second-error")).isEqualTo(FrameworkTest.Error.NO_ERROR);
    }

    @Test
    public void jsonToProtobuf_JsonArrayWithTwoElements_ParseFirstOne() throws Exception {
        // given
        JsonArray jsonArray = new JsonArray();
        jsonArray.add(new JsonParser().parse("{\"id\":\"1\"}"));
        jsonArray.add(new JsonParser().parse("{\"id\":\"2\"}"));

        // when
        FrameworkTest.SerializationTest message = ProtobufUtil.jsonToProtobuf(jsonArray, FrameworkTest.SerializationTest.class);

        // then
        assertThat(message.getId()).isEqualTo("1");
    }

    @Test
    public void byteArrayToProtobuf_Success() throws Exception {
        // given
        byte[] byteArray = FrameworkTest.SerializationTest.newBuilder()
                .setId("id")
                .setId2("id2")
                .build()
                .toByteArray();

        // when
        FrameworkTest.SerializationTest message = ProtobufUtil.byteArrayToProtobuf(byteArray, FrameworkTest.SerializationTest.class);

        // then
        assertThat(message.getId()).isEqualTo("id");
        assertThat(message.getId2()).isEqualTo("id2");
    }

    @Test
    public void newEmptyMessage_Success() throws Exception {
        // given, when
        FrameworkTest.SerializationTest message = ProtobufUtil.newEmptyMessage(FrameworkTest.SerializationTest.class);

        // then
        assertThat(message).isEqualTo(FrameworkTest.SerializationTest.getDefaultInstance());
    }

    @Test
    public void protobufToJson_with_includingDefaultValueFields() {
        // given
        FrameworkTest.SerializationTest message = FrameworkTest.SerializationTest.newBuilder().build();

        // when
        JsonObject result = ProtobufUtil.protobufToJsonWithDefaultValues(message);

        // then
        assertThat(result).isNotNull();
        assertThat(result.get("id").getAsString()).isEqualTo("");
        assertThat(result.get("id2").getAsString()).isEqualTo("");
        assertThat(result.get("id4").getAsString()).isEqualTo("");
        assertThat(result.get("sub_message")).isNull();
    }

    @Test
    public void protobufToJson_with_sub_object() {
        // given
        FrameworkTest.SerializationTest message = FrameworkTest.SerializationTest.newBuilder()
            .setSubMessage(
                FrameworkTest.SerializationSubMessage.newBuilder().build()
            )
            .build();

        // when
        JsonObject result = ProtobufUtil.protobufToJsonWithDefaultValues(message);

        // then
        assertThat(result).isNotNull();
        assertThat(result.get("id").getAsString()).isEqualTo("");
        assertThat(result.get("id2").getAsString()).isEqualTo("");
        assertThat(result.get("id4").getAsString()).isEqualTo("");
        assertThat(result.get("sub_message")).isNotNull();
        assertThat(result.getAsJsonObject("sub_message").get("id").getAsString()).isEqualTo("");
    }

    @Test
    public void jsonToProtobuf_nullRequest() {
        JsonArray array = null;
        assertThat(ProtobufUtil.jsonToProtobuf(array, FrameworkTest.SerializationTest.class)).isNull();
        array = new JsonArray();
        assertThat(ProtobufUtil.jsonToProtobuf(array, FrameworkTest.SerializationTest.class)).isNull();
        array.add(JsonNull.INSTANCE);
        assertThat(ProtobufUtil.jsonToProtobuf(array, FrameworkTest.SerializationTest.class)).isNull();
    }

    /**
     * There is a limitation in gson that does not allow nulls in arrays when converting to proto
     * com.google.protobuf.InvalidProtocolBufferException: Repeated field elements cannot be null
     */
    @Test
    public void nullsInArrayAreRemoved() {
        String input = "{\"instances\":[null]}";
        ProtobufUtil.jsonToProtobuf(input, ConfigurationOuterClass.VariantDetail.class);
    }

}
