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

package com.sixt.service.framework.database;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.sixt.service.framework.ServiceProperties;
import com.sixt.service.framework.annotation.HealthCheckProvider;
import com.sixt.service.framework.health.HealthCheck;
import com.sixt.service.framework.health.HealthCheckContributor;

@HealthCheckProvider
@Singleton
public class DatabaseMigrationContributor implements HealthCheckContributor {

    private ServiceProperties serviceProps;
    private HealthCheck status = new HealthCheck("database_migration", HealthCheck.Status.PASS, "");

    @Inject
    public DatabaseMigrationContributor(ServiceProperties props) {
        this.serviceProps = props;
    }

    @Override
    public HealthCheck getHealthCheck() {
        return status;
    }

    @Override
    public boolean shouldRegister() {
        return true;
    }

    public void updateStatus(HealthCheck hc) {
        this.status = hc;
    }

}
