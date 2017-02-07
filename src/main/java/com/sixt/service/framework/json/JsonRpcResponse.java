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

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.apache.commons.lang3.builder.ToStringBuilder;

public class JsonRpcResponse {

    public final static String ID_FIELD = "id";
    public final static String ERROR_FIELD = "error";
    public final static String RESULT_FIELD = "result";

    private JsonElement id;
    private JsonElement result;
    private JsonElement error;
    // HTTP status code, not part of the JSON-RPC spec, used for internal purposes only
    private int statusCode;

    public JsonRpcResponse(JsonElement id, JsonElement result, JsonElement error, int statusCode) {
        setId(id);
        setResult(result);
        setError(error);
        setStatusCode(statusCode);
    }

    public JsonObject toJson() {
        JsonObject retval = new JsonObject();
        retval.add(ID_FIELD, id);
        retval.add(ERROR_FIELD, error);
        retval.add(RESULT_FIELD, result);
        return retval;
    }

    @Override
    public String toString() {
        ToStringBuilder builder = new ToStringBuilder(this);
        builder.append(ID_FIELD, getId());
        builder.append(RESULT_FIELD, getResult());
        builder.append(ERROR_FIELD, getError());
        return builder.toString();
    }

    public JsonElement getId() {
        return id;
    }

    public void setId(JsonElement id) {
        this.id = id;
    }

    public JsonElement getResult() {
        return result;
    }

    public void setResult(JsonElement result) {
        this.result = result;
    }

    public JsonElement getError() {
        return error;
    }

    public void setError(JsonElement error) {
        this.error = error;
    }

    public int getStatusCode() { return statusCode; }

    public void setStatusCode(int statusCode) { this.statusCode = statusCode; }
}
