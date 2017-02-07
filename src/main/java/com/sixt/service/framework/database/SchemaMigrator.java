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
import com.sixt.service.framework.health.HealthCheck;
import org.apache.commons.lang3.StringUtils;
import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Semaphore;

@Singleton
public class SchemaMigrator {

    private static final Logger logger = LoggerFactory.getLogger(SchemaMigrator.class);

    private ServiceProperties serviceProps;
    private DatabaseMigrationContributor healthCheck;
    protected Semaphore badConfigSemaphore = new Semaphore(0);
    protected Semaphore flywayFailedSemaphore = new Semaphore(0);
    protected ConnectionVerifier connectionVerifier;
    protected Flyway flyway;

    @Inject
    public SchemaMigrator(ServiceProperties props, DatabaseMigrationContributor hc,
                          ConnectionVerifier verifier, Flyway flyway) {
        this.serviceProps = props;
        this.healthCheck = hc;
        this.connectionVerifier = verifier;
        this.flyway = flyway;
    }

    public void migrate() {
        if (StringUtils.isBlank(serviceProps.getDatabaseServer())) {
            String message = "Missing database server parameters";
            logger.error(message);
            healthCheck.updateStatus(new HealthCheck("migration_server_params_missing",
                    HealthCheck.Status.FAIL, message));
            try {
                badConfigSemaphore.acquire(); //sleep forever
            } catch (InterruptedException e1) {
            }
        }
        logger.info("Migrating database...");
        //try to make jdbc connection to verify database exists
        //if it doesn't, make healthcheck fail and retry once a minute
        connectionVerifier.verifyDatabaseExists();
        //when our database exists, try to migrate it.
        //if it fails, make healthcheck fail and pause forever
        migrateDatabase();
        logger.info("Database migration complete, continue server startup...");
    }

    protected void migrateDatabase() {
        try {
            String url = "jdbc:" + serviceProps.getDatabaseServer();
            flyway.setDataSource(url, serviceProps.getDatabaseUsername(),
                    serviceProps.getDatabasePassword());
            //Use repair to fix the state if a migration failed and you corrected it.
            if (serviceProps.getProperty("repairDatabase") != null) {
                flyway.repair();
            }
            flyway.setValidateOnMigrate(false);
            flyway.migrate();
        } catch (Exception e) {
            String message = "Error migrating database schema: " + e.getMessage() +
                    " Restart service with options '-repairDatabase true' after the problem" +
                    " has been corrected to resume migration.";
            logger.error(message);
            healthCheck.updateStatus(new HealthCheck("migration_failure",
                    HealthCheck.Status.FAIL, message));
            try {
                flywayFailedSemaphore.acquire(); //sleep forever
            } catch (InterruptedException e1) {
            }
        }
    }

}
