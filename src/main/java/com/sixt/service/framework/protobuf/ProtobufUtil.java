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
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.protobuf.Message;
import com.google.protobuf.util.JsonFormat;
import com.sixt.service.framework.rpc.RpcCallException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;

@SuppressWarnings({"StatementWithEmptyBody", "unchecked"})
public class ProtobufUtil {

    public static final int MAX_HEADER_CHUNK_SIZE = 1000;
    public static final int MAX_BODY_CHUNK_SIZE = 10_000_000;
    private static final Logger logger = LoggerFactory.getLogger(ProtobufUtil.class);

    private static <TYPE extends Message> TYPE.Builder getBuilder(Class<TYPE> messageClass) throws NoSuchMethodException,
            InstantiationException, IllegalAccessException, java.lang.reflect.InvocationTargetException {

        Constructor<TYPE> constructor = messageClass.getDeclaredConstructor();
        constructor.setAccessible(true);
        TYPE instance = constructor.newInstance();

        return instance.newBuilderForType();
    }

    /**
     * NOTE: this is only using the first element of the JsonArray
     */
    public static Message jsonToProtobuf(JsonArray request, Class<? extends Message> messageClass) {
        return jsonToProtobuf(request.get(0).toString(), messageClass);
    }

    /**
     * Converts a JSON String to a protobuf message
     *
     * @param input        the input String to convert
     * @param messageClass the protobuf message class to convert into
     * @return the converted protobuf message
     */
    public static <TYPE extends Message> TYPE jsonToProtobuf(String input, Class<TYPE> messageClass) {
        if (input == null) {
            return null;
        } else if ("{}".equals(input)) {
            try {
                TYPE.Builder builder = getBuilder(messageClass);
                return (TYPE) builder.getDefaultInstanceForType();
            } catch (Exception e) {
                logger.warn("Error building protobuf object of type {} from json: {}",
                        messageClass.getName(), input);
            }
        }

        try {
            TYPE.Builder builder = getBuilder(messageClass);
            JsonFormat.parser().ignoringUnknownFields().merge(input, builder);
            return (TYPE) builder.build();
        } catch (Exception e) {
            throw new RuntimeException("Error deserializing json to protobuf", e);
        }
    }

    /**
     * Converts a byte array to a protobuf message
     *
     * @param data         the byte array to convert
     * @param messageClass the protobuf message class to convert into
     * @return the converted protobuf message
     * @throws RpcCallException if something goes wrong during the deserialization
     */
    public static <TYPE extends Message> TYPE byteArrayToProtobuf(byte data[], Class<TYPE> messageClass)
            throws RpcCallException {
        try {
            Message.Builder builder = getBuilder(messageClass);
            return (TYPE) builder.mergeFrom(data).build();
        } catch (Exception e) {
            throw new RpcCallException(RpcCallException.Category.InternalServerError,
                    "Error deserializing byte array to protobuf: " + e);
        }
    }

    /**
     * Creates an empty protobuf message of the specified type
     *
     * @param klass the protobuf message type
     * @return the generated protobuf message
     */
    public static <TYPE extends Message> TYPE newEmptyMessage(Class<TYPE> klass) {
        try {
            Message.Builder builder = getBuilder(klass);
            return (TYPE) builder.build();
        } catch (Exception e) {
            throw new RuntimeException("Error deserializing byte array to protobuf", e);
        }
    }

    /**
     * Converts a protobuf message to a JSON object
     * <p>
     * Note: Preserves the field names as defined in the *.proto definition
     *
     * @param input the protobuf message to convert
     * @return the converted JSON object
     */
    public static JsonObject protobufToJson(Message input) {
        JsonObject object = new JsonObject();
        if (input == null) {
            logger.warn("Protobuf message was null");
        } else {
            try {
                String jsonString = JsonFormat.printer().preservingProtoFieldNames().print(input);
                object = new JsonParser().parse(jsonString).getAsJsonObject();
            } catch (Exception e) {
                throw new RuntimeException("Error deserializing protobuf to json", e);
            }
        }
        return object;
    }

    /**
     * Converts a JSON object to a protobuf message.
     * <p>
     * Note: Ignores unknown fields
     *
     * @param builder the proto message type builder
     * @param input   the JSON object to convert
     * @return the converted protobuf message
     */
    public static Message fromJson(Message.Builder builder, JsonObject input) throws Exception {
        JsonFormat.parser().ignoringUnknownFields().merge(input.toString(), builder);
        return builder.build();
    }
}
