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

import com.google.gson.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Any error state triggered by interaction with a remote service will result
 * in an instance of this class being thrown.
 */
public class RpcCallException extends Exception {

    private static final Logger logger = LoggerFactory.getLogger(RpcCallException.class);

    public static final String CATEGORY = "category";
    public static final String MESSAGE = "message";
    public static final String SOURCE = "source";
    public static final String CODE = "code";
    public static final String DATA = "data";
    public static final String RETRIABLE = "retriable";

    private static Map<Integer, Category> cache = new HashMap<>();

    public enum Category {
        BadRequest(400, false),               //invalid params or malformed
        Unauthorized(401, false),             //not logged in
        InsufficientPermissions(403, false),  //not enough perms
        ResourceNotFound(404, false),
        InternalServerError(500, true),       //unexpected exception
        BackendError(501, false),             //business logic failure
        RequestTimedOut(504, true);

        private int httpStatus;
        private boolean retriable; //default, can be overridden in the exception instance

        Category(int status, boolean retriable) {
            this.httpStatus = status;
            this.retriable = retriable;
            addToCache(status, this);
        }

        private void addToCache(int status, Category category) {
            cache.put(status, category);
        }

        public int getHttpStatus() {
            return httpStatus;
        }

        public boolean isRetriable() {
            return retriable;
        }

        public static Category fromStatus(int status) {
            return cache.get(status);
        }
    }

    private String source;
    private Category category;
    private String errorCode;
    private String message;
    private String data;
    private boolean retriable;

    public RpcCallException(Category category, String message) {
        super(); //builds stacktrace
        this.category = category;
        this.retriable = category.retriable;
        this.message = message;
    }

    public RpcCallException withSource(String source) {
        this.source = source;
        return this;
    }

    public RpcCallException withErrorCode(String errorCode) {
        this.errorCode = errorCode;
        return this;
    }

    public RpcCallException withData(String data) {
        this.data = data;
        return this;
    }

    public RpcCallException withRetriable(boolean retriable) {
        this.retriable = retriable;
        return this;
    }

    public String getSource() {
        return source;
    }

    public Category getCategory() {
        return category;
    }

    public String getErrorCode() {
        return errorCode;
    }

    @Override
    public String getMessage() {
        return message;
    }

    public String getData() {
        return data;
    }

    public boolean isRetriable() {
        return retriable;
    }

    @Override
    public String toString() {
        return toJson().toString();
    }

    public JsonObject toJson() {
        JsonObject obj = new JsonObject();
        obj.addProperty(CATEGORY, category.getHttpStatus());
        obj.addProperty(MESSAGE, message);
        obj.addProperty(SOURCE, source);
        obj.addProperty(CODE, errorCode);
        obj.addProperty(DATA, data);
        obj.addProperty(RETRIABLE, retriable);
        return obj;
    }

    public static RpcCallException fromJson(String json) {
        try {
            JsonParser parser = new JsonParser();
            JsonElement rawObject = parser.parse(json);
            if (rawObject instanceof JsonObject) {
                JsonObject object = (JsonObject) rawObject;
                Category category = Category.fromStatus(object.get(CATEGORY).getAsInt());
                String message = object.get(MESSAGE).getAsString();
                RpcCallException retval = new RpcCallException(category, message);
                JsonElement element = object.get(SOURCE);
                if (element != null && !(element instanceof JsonNull)) {
                    retval.withSource(element.getAsString());
                }
                element = object.get(CODE);
                if (element != null && !(element instanceof JsonNull)) {
                    retval.withErrorCode(element.getAsString());
                }
                element = object.get(DATA);
                if (element != null && !(element instanceof JsonNull)) {
                    retval.withData(element.getAsString());
                }
                element = object.get(RETRIABLE);
                if (element != null && !(element instanceof JsonNull)) {
                    retval.withRetriable(element.getAsBoolean());
                }
                return retval;
            } else if (rawObject instanceof JsonPrimitive) {
                logger.warn("Expected an RpcCallException json object, but received: {}", rawObject.toString());
            }
        } catch (Exception ex) {
            logger.warn("Caught exception parsing RpcCallException: " + json, ex);
        }
        return null;
    }

}
