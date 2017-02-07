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

import com.sixt.service.framework.ServiceProperties;
import com.sixt.service.framework.health.HealthCheck;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.Before;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

public class SchemaMigratorIntegrationTest {

    private ServiceProperties props;
    private SchemaMigrator migrator;
    private DatabaseMigrationContributor healthcheck;
    private ConnectionVerifier connectionVerifier;

    @Before
    public void setup() {
        props = new ServiceProperties();
        healthcheck = new DatabaseMigrationContributor(props);
        connectionVerifier = mock(ConnectionVerifier.class);
        migrator = new SchemaMigrator(props, healthcheck, connectionVerifier, new Flyway());
    }

    @Test(expected = IllegalArgumentException.class)
    public void sleepForDatabaseCreation() throws InterruptedException {
        doThrow(new IllegalArgumentException()).when(connectionVerifier).verifyDatabaseExists();
        migrator.badConfigSemaphore.release();
        migrator.migrate();
        HealthCheck hc = healthcheck.getHealthCheck();
        assertThat(hc.getStatus()).isEqualTo(HealthCheck.Status.FAIL);
    }

    @Test
    public void sleepAfterFailedMigration() {
        props.addProperty("databaseServer", "foo");
        Flyway flyway = mock(Flyway.class);
        when(flyway.migrate()).thenThrow(new FlywayException());
        migrator.flyway = flyway;
        migrator.flywayFailedSemaphore.release();
        migrator.migrate();
    }
}
