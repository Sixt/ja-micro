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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;

public class JsonUtil {

    private static final Logger logger = LoggerFactory.getLogger(JsonUtil.class);

    public int extractInteger(JsonObject json, String name, int defaultValue) {
        if (json != null) {
            int dotIndex = name.indexOf('.');
            if (dotIndex > 0) {
                String baseName = name.substring(0, dotIndex);
                JsonElement childElement = json.get(baseName);
                return extractInteger((JsonObject) childElement, name.substring(dotIndex + 1), defaultValue);
            }
            JsonElement element = json.get(name);
            if (element != null && ! element.isJsonNull()) {
                return element.getAsInt();
            }
        }
        return defaultValue;
    }

    public String extractString(JsonObject json, String name) {
        if (json != null) {
            int dotIndex = name.indexOf('.');
            if (dotIndex > 0) {
                String baseName = name.substring(0, dotIndex);
                JsonElement childElement = json.get(baseName);
                return extractString((JsonObject) childElement, name.substring(dotIndex + 1));
            }
            JsonElement element = json.get(name);
            if (element != null && ! element.isJsonNull()) {
                return element.getAsString();
            }
        }
        return null;
    }

    public long extractTimestamp(JsonObject json, String name) {
        String timestamp = extractString(json, name);
        if (timestamp != null) {
            try {
                Instant instant = Instant.parse(timestamp);
                return instant.toEpochMilli();
            } catch (Exception ex) {
                logger.info("Invalid timestamp: {}", timestamp);
            }
        }
        return 0;
    }

    public double extractDouble(JsonObject json, String name, int defaultValue) {
        if (json != null) {
            int dotIndex = name.indexOf('.');
            if (dotIndex > 0) {
                String baseName = name.substring(0, dotIndex);
                JsonElement childElement = json.get(baseName);
                return extractDouble((JsonObject) childElement, name.substring(dotIndex + 1), defaultValue);
            }
            JsonElement element = json.get(name);
            if (element != null && ! element.isJsonNull()) {
                return element.getAsDouble();
            }
        }
        return defaultValue;
    }

}
