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

package com.sixt.service.framework.jetty;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.sixt.service.framework.health.HealthCheck;
import com.sixt.service.framework.health.HealthCheckManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collection;

/**
 * This servlet allows infrastructure to determine the health of the service.
 */
@Singleton
public class HealthServlet extends HttpServlet {

    private static final Logger logger = LoggerFactory.getLogger(HealthServlet.class);

    private HealthCheckManager healthManager;

    @Inject
    public HealthServlet(HealthCheckManager healthManager) {
        this.healthManager = healthManager;
    }

    /**
     * This is a Sixt-specific implementation.
     */
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        Collection<HealthCheck> healthChecks = healthManager.getCurrentHealthChecks();
        HealthCheck.Status summary = healthManager.getSummaryFor(healthChecks);
        JsonObject json = new JsonObject();
        json.addProperty("summary", translateStatus(summary));
        if (! healthChecks.isEmpty()) {
            JsonArray detailsArray = new JsonArray();
            for (HealthCheck check : healthChecks) {
                detailsArray.add(getCheckDetail(check));
            }
            json.add("details", detailsArray);
        }
        resp.getWriter().print(json.toString());
    }

    private JsonObject getCheckDetail(HealthCheck check) {
        JsonObject retval = new JsonObject();
        retval.add("name", new JsonPrimitive(check.getName()));
        retval.add("status", new JsonPrimitive(translateStatus(check.getStatus())));
        String message = check.getMessage();
        retval.add("reason", new JsonPrimitive(message == null ? "" : message));
        return retval;
    }

    private String translateStatus(HealthCheck.Status summary) {
        switch(summary) {
            case PASS:
                return "OK";
            case WARN:
                return "DEGRADED";
            case FAIL:
                return "CRITICAL";
            default:
                logger.warn("Unexpected Status value: {}", summary);
                return "UNKNOWN";
        }
    }

}
