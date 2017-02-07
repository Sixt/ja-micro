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
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ConsulHealthEntryFactory {

    public List<ConsulHealthEntry> parse(String data) throws IOException {
        List<ConsulHealthEntry> retval = new ArrayList<>();
        if (data != null) {
            JsonParser parser = new JsonParser();
            JsonElement parsed = parser.parse(data);
            if (parsed instanceof JsonArray) {
                JsonArray array = (JsonArray) parsed;
                if (array.size() > 0) {
                    for (JsonElement element : array) {
                        JsonObject obj = (JsonObject) element;
                        JsonElement checksArrayObj = obj.get("Checks");
                        ConsulHealthEntry.Status status = parseHealthStatus(checksArrayObj);
                        obj = (JsonObject) obj.get("Service");
                        ConsulHealthEntry entry = new ConsulHealthEntry(obj.get("ID").getAsString(),
                                status, obj.get("Address").getAsString(), obj.get("Port").getAsInt());
                        retval.add(entry);
                    }
                }
            }
        }
        return retval;
    }

    private ConsulHealthEntry.Status parseHealthStatus(JsonElement element) {
        ConsulHealthEntry.Status retval = ConsulHealthEntry.Status.Passing;
        if (element instanceof JsonArray) {
            JsonArray array = (JsonArray)element;
            for (JsonElement arrayElement : array) {
                String status = ((JsonObject)arrayElement).get("Status").getAsString();
                ConsulHealthEntry.Status checkStatus = ConsulHealthEntry.Status.fromString(status);
                if (! ConsulHealthEntry.Status.Passing.equals(checkStatus)) {
                    retval = checkStatus;
                }
            }
        }
        return retval;
    }

}
