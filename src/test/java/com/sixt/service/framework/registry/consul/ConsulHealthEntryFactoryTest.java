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

import org.junit.Test;

import java.io.IOException;
import java.util.List;
import java.util.Scanner;

import static org.assertj.core.api.Assertions.assertThat;

public class ConsulHealthEntryFactoryTest {

    private ConsulHealthEntryFactory factory = new ConsulHealthEntryFactory();

    @Test
    public void testParsing() throws IOException {
        String input = loadResourceAsString("rating-full.json");
        List<ConsulHealthEntry> entries = factory.parse(input);
        assertThat(entries).hasSize(145);
        entries = ConsulHealthEntryFilter.filterHealthyInstances(entries);
        assertThat(entries).hasSize(3);
    }

    public String loadResourceAsString(String fileName) throws IOException {
        String retval = null;
        try (Scanner scanner = new Scanner(getClass().getClassLoader().getResourceAsStream(fileName))) {
            retval = scanner.useDelimiter("\\A").next();
        }
        return retval;
    }

}
