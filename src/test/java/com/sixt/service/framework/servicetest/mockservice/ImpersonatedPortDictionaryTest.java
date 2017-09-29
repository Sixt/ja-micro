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

package com.sixt.service.framework.servicetest.mockservice;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

public class ImpersonatedPortDictionaryTest {

    private ImpersonatedPortDictionary dictionary = ImpersonatedPortDictionary.getInstance();

    @Test
    public void testDictionary() {
        int port = dictionary.newInternalPortForImpersonated("foo");
        assertThat(port).isEqualTo(42000);
        port = dictionary.getInternalPortForImpersonated("foo");
        assertThat(port).isEqualTo(42000);
        port = dictionary.newInternalPortForImpersonated("bar");
        assertThat(port).isEqualTo(42001);
        Throwable thrown = catchThrowable(() -> {
            dictionary.getInternalPortForImpersonated("baz");
        });
        assertThat(thrown).isInstanceOf(IllegalStateException.class);
    }

}
