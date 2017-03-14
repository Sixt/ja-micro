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

package com.sixt.service.framework.injection;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.sixt.service.framework.ServiceProperties;
import com.sixt.service.framework.jetty.HealthServlet;
import com.sixt.service.framework.jetty.RpcServlet;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class OrangeServletModuleTest {

    @Test
    public void testConfigure() {
        OrangeServletModule module = new OrangeServletModule();
        Injector injector = Guice.createInjector(module, new TracingModule(new ServiceProperties()));
        assertThat(injector.getInstance(HealthServlet.class)).isNotNull();
        assertThat(injector.getInstance(RpcServlet.class)).isNotNull();
    }
}
