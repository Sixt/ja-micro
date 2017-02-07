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
import com.sixt.service.framework.ServiceProperties;
import com.sixt.service.framework.health.HealthCheck;
import com.sixt.service.framework.util.Sleeper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DriverManager;

public class ConnectionVerifier {

    private static final Logger logger = LoggerFactory.getLogger(ConnectionVerifier.class);

    protected ServiceProperties serviceProps;
    protected DatabaseMigrationContributor healthCheck;
    protected Sleeper sleeper = new Sleeper();

    @Inject
    public ConnectionVerifier(ServiceProperties serviceProps, DatabaseMigrationContributor hc) {
        this.serviceProps = serviceProps;
        this.healthCheck = hc;
    }

    public void verifyDatabaseExists() {
        boolean success = false;
        while (! success) {
            try {
                String url = "jdbc:" + serviceProps.getDatabaseServer();
                logger.debug("Attempting connection to {}", url);
                if (url.contains("mysql:")) {
                    Class<?> mysqlDriver = Class.forName("com.mysql.jdbc.Driver");
                    if (mysqlDriver == null) {
                        logger.error("Could not load mysql driver");
                    }
                } else {
                    Class<?> postgresDriver = Class.forName("org.postgresql.Driver");
                    if (postgresDriver == null) {
                        logger.error("Could not load postgres driver");
                    }
                }
                Connection conn = DriverManager.getConnection(url, serviceProps.getDatabaseUsername(),
                        serviceProps.getDatabasePassword());
                logger.debug("Connected to schema {}", conn.getSchema());
                conn.close();
                success = true;
                healthCheck.updateStatus(new HealthCheck());
            } catch (Exception e) {
                String message = "Error connecting to database: " + e.getMessage();
                logger.error(message, e);
                healthCheck.updateStatus(new HealthCheck("migration_connection_verifier",
                        HealthCheck.Status.FAIL, message));
                try {
                    sleeper.sleep(60000L);
                } catch (InterruptedException e1) {
                }
            }
        }
    }

}
