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

package com.sixt.service.framework.health;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.sixt.service.framework.ServiceProperties;
import com.sixt.service.framework.injection.InjectionModule;
import org.junit.Before;
import org.junit.Test;

import java.util.Collection;

import static org.assertj.core.api.Assertions.assertThat;

public class HealthCheckManagerTest {

    private HealthCheckManager manager;

    @Before
    public void setup() {
        InjectionModule module = new InjectionModule();
        module.setServiceProperties(new ServiceProperties());
        Injector injector = Guice.createInjector(module);
        manager = injector.getInstance(HealthCheckManager.class);
    }

    @Test
    public void verifyOverallStatus() {
        manager.registerPollingContributor(new HealthCheckContributor() {
            @Override
            public HealthCheck getHealthCheck() {
                return new HealthCheck();
            }
            @Override
            public boolean shouldRegister() {
                return true;
            }
        });

        Collection<HealthCheck> healthChecks = manager.getCurrentHealthChecks();
        HealthCheck.Status summary = manager.getSummaryFor(healthChecks);
        assertThat(healthChecks).hasSize(1);
        assertThat(summary.getConsulStatus()).isEqualTo("pass");

        manager.registerPollingContributor(new HealthCheckContributor() {
            @Override
            public HealthCheck getHealthCheck() {
                return new HealthCheck("", HealthCheck.Status.WARN, "cheapy");
            }
            @Override
            public boolean shouldRegister() {
                return true;
            }
        });

        healthChecks = manager.getCurrentHealthChecks();
        summary = manager.getSummaryFor(healthChecks);
        assertThat(healthChecks).hasSize(2);
        assertThat(summary.getConsulStatus()).isEqualTo("warn");

        manager.registerPollingContributor(new HealthCheckContributor() {
            @Override
            public HealthCheck getHealthCheck() {
                return new HealthCheck("", HealthCheck.Status.FAIL, "crappy");
            }
            @Override
            public boolean shouldRegister() {
                return true;
            }
        });

        healthChecks = manager.getCurrentHealthChecks();
        summary = manager.getSummaryFor(healthChecks);
        assertThat(healthChecks).hasSize(3);
        assertThat(summary.getConsulStatus()).isEqualTo("fail");
    }

}
