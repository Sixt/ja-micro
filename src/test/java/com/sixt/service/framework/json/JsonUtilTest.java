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

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.assertj.core.data.Percentage;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class JsonUtilTest {

    private JsonUtil jsonUtil = new JsonUtil();

    @Test
    public void extractFieldsTest() {
        String event = "{\"meta\":{\"name\":\"OneVehicleDynamicData\",\"timestamp\":" +
                "\"2017-02-14T10:20:37.629Z\",\"grouping\":\"OneVehicleFinderData\"," +
                "\"distribution_key\":\"665292e8-6a3b-4702-9666-b5624d6c8320\"},\"position\":{" +
                "\"latitude\":48.042999267578125,\"longitude\":11.510173797607422,\"foo\":7}," +
                "\"vehicle_id\":\"abcd\",\"fuel_level\":999," +
                "\"charge_level\":50.0,\"odometer\":12345}";
        JsonObject json = (JsonObject) new JsonParser().parse(event);
        assertThat(jsonUtil.extractInteger(json, "odometer", -1)).isEqualTo(12345);
        assertThat(jsonUtil.extractDouble(json, "charge_level", -1))
                .isCloseTo(50, Percentage.withPercentage(1));
        assertThat(jsonUtil.extractString(json, "vehicle_id")).isEqualTo("abcd");

        //check dot-notation paths
        assertThat(jsonUtil.extractInteger(json, "position.foo", -1)).isEqualTo(7);
        assertThat(jsonUtil.extractDouble(json, "position.latitude", 0))
                .isCloseTo(48.043, Percentage.withPercentage(1));
        assertThat(jsonUtil.extractString(json, "meta.name")).isEqualTo("OneVehicleDynamicData");
    }

}
