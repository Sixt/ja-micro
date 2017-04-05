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

import com.google.common.base.CaseFormat;
import com.google.common.io.BaseEncoding;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.protobuf.ByteString;
import com.google.protobuf.Descriptors;
import com.google.protobuf.Message;
import com.googlecode.protobuf.format.JsonFormat;
import com.sixt.service.framework.rpc.RpcCallException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.util.List;
import java.util.Map;

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

    public static <TYPE extends Message> TYPE jsonToProtobuf(String request, Class<TYPE> messageClass) {
        if (request == null) {
            return null;
        } else if ("{}".equals(request)) {
            try {
                TYPE.Builder builder = getBuilder(messageClass);
                return (TYPE) builder.getDefaultInstanceForType();
            } catch (Exception e) {
                logger.warn("Error building protobuf object of type {} from json: {}",
                        messageClass.getName(), request);
            }
        }

        try {
            TYPE.Builder builder = getBuilder(messageClass);
            JsonFormat formatter = new JsonFormat();
            try {
                ByteArrayInputStream stream = new ByteArrayInputStream(request.getBytes());
                formatter.merge(stream, builder);
            } catch (IOException e) {
                logger.warn("Error building protobuf object of type {} from json: {}",
                        messageClass.getName(), request);
            }
            return (TYPE) builder.build();
        } catch (Exception e) {
            throw new RuntimeException("Error deserializing json to protobuf", e);
        }
    }

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

    public static <TYPE extends Message> TYPE newEmptyMessage(Class<TYPE> klass) {
        try {
            Message.Builder builder = getBuilder(klass);
            return (TYPE) builder.build();
        } catch (Exception e) {
            throw new RuntimeException("Error deserializing byte array to protobuf", e);
        }
    }

    public static JsonObject protobufToJson(Message output) {
        JsonObject object = new JsonObject();
        if (output == null) {
            logger.warn("Protobuf message was null");
        } else {
            for (Map.Entry<Descriptors.FieldDescriptor, Object> field : output.getAllFields().entrySet()) {
                String jsonName = field.getKey().getName();
                if (field.getKey().isRepeated()) {
                    JsonArray array = new JsonArray();
                    List<?> items = (List<?>) field.getValue();
                    for (Object item : items) {
                        array.add(serializeField(field.getKey(), item));
                    }
                    object.add(jsonName, array);
                } else {
                    object.add(jsonName, serializeField(field.getKey(), field.getValue()));
                }
            }
        }
        return object;
    }

    /**
     * Converts a JSON object to a protobuf message
     *
     * @param builder the proto message type builder
     * @param input   the JSON object to convert
     */
    public static Message fromJson(Message.Builder builder, JsonObject input) throws Exception {
        Descriptors.Descriptor descriptor = builder.getDescriptorForType();
        for (Map.Entry<String, JsonElement> entry : input.entrySet()) {
            String protoName = CaseFormat.LOWER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, entry.getKey());
            Descriptors.FieldDescriptor field = descriptor.findFieldByName(protoName);
            if (field == null) {
                throw new Exception("Can't find descriptor for field " + protoName);
            }
            if (field.isRepeated()) {
                if (!entry.getValue().isJsonArray()) {
                    // fail
                }
                JsonArray array = entry.getValue().getAsJsonArray();
                for (JsonElement item : array) {
                    builder.addRepeatedField(field, parseField(field, item, builder));
                }
            } else {
                builder.setField(field, parseField(field, entry.getValue(), builder));
            }
        }
        return builder.build();
    }

    private static JsonElement serializeField(Descriptors.FieldDescriptor field, Object value) {
        switch (field.getType()) {
            case DOUBLE:
                return new JsonPrimitive((Double) value);
            case FLOAT:
                return new JsonPrimitive((Float) value);
            case INT64:
            case UINT64:
            case FIXED64:
            case SINT64:
            case SFIXED64:
                return new JsonPrimitive((Long) value);
            case INT32:
            case UINT32:
            case FIXED32:
            case SINT32:
            case SFIXED32:
                return new JsonPrimitive((Integer) value);
            case BOOL:
                return new JsonPrimitive((Boolean) value);
            case STRING:
                return new JsonPrimitive((String) value);
            case GROUP:
            case MESSAGE:
                return protobufToJson((Message) value);
            case BYTES:
                return new JsonPrimitive(BaseEncoding.base64().encode(((ByteString) value).toByteArray()));
            case ENUM:
                String protoEnumName = ((Descriptors.EnumValueDescriptor) value).getName();
                return new JsonPrimitive(protoEnumName);
        }
        return null;
    }

    private static Object parseField(Descriptors.FieldDescriptor field, JsonElement value,
                                     Message.Builder enclosingBuilder) throws Exception {
        switch (field.getType()) {
            case DOUBLE:
                if (!value.isJsonPrimitive()) {
                    // fail;
                }
                return value.getAsDouble();
            case FLOAT:
                if (!value.isJsonPrimitive()) {
                    // fail;
                }
                return value.getAsFloat();
            case INT64:
            case UINT64:
            case FIXED64:
            case SINT64:
            case SFIXED64:
                if (!value.isJsonPrimitive()) {
                    // fail
                }
                return value.getAsLong();
            case INT32:
            case UINT32:
            case FIXED32:
            case SINT32:
            case SFIXED32:
                if (!value.isJsonPrimitive()) {
                    // fail
                }
                return value.getAsInt();
            case BOOL:
                if (!value.isJsonPrimitive()) {
                    // fail
                }
                return value.getAsBoolean();
            case STRING:
                if (!value.isJsonPrimitive()) {
                    // fail
                }
                return value.getAsString();
            case GROUP:
            case MESSAGE:
                if (!value.isJsonObject()) {
                    // fail
                }
                return fromJson(enclosingBuilder.newBuilderForField(field), value.getAsJsonObject());
            case BYTES:
                if (!value.isJsonPrimitive()) {
                    // fail
                }
                return ByteString.copyFrom(BaseEncoding.base64().decode(value.getAsString()));
            case ENUM:
                if (!value.isJsonPrimitive()) {
                    // fail
                }
                String protoEnumValue = CaseFormat.LOWER_CAMEL.to(CaseFormat.UPPER_UNDERSCORE,
                        value.getAsString());
                return field.getEnumType().findValueByName(protoEnumValue);
        }
        return null;
    }
}
