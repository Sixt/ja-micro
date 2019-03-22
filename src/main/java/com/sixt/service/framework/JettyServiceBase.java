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

package com.sixt.service.framework;

import com.sixt.service.framework.annotation.*;
import com.sixt.service.framework.util.Sleeper;
import io.github.lukehutch.fastclasspathscanner.FastClasspathScanner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.annotation.Annotation;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

public class JettyServiceBase {

    private static final Logger logger = LoggerFactory.getLogger(JettyServiceBase.class);

    public static void main(String[] args) {
        try {
            verifyDefaultCharset();

            // find main class, rpc handlers, healthcheck providers, serviceRegistryPlugins,
            // configuration plugins, and metrics reporting plugins
            List<String> serviceEntries = new ArrayList<>();
            List<String> rpcHandlers = new ArrayList<>();
            List<String> hcProviders = new ArrayList<>();
            List<String> serviceRegistryPlugins = new ArrayList<>();
            List<String> configPlugins = new ArrayList<>();
            List<String> metricsReportingPlugins = new ArrayList<>();
            List<String> tracingPlugins = new ArrayList<>();

            new FastClasspathScanner()
                    .matchClassesWithAnnotation(OrangeMicroservice.class, matchingClass ->
                            serviceEntries.add(matchingClass.getName()))
                    .matchClassesWithAnnotation(RpcHandler.class, matchingClass ->
                            rpcHandlers.add(matchingClass.getName()))
                    .matchClassesWithAnnotation(HealthCheckProvider.class, matchingClass ->
                            hcProviders.add(matchingClass.getName()))
                    .matchClassesWithAnnotation(ServiceRegistryPlugin.class, matchingClass ->
                            serviceRegistryPlugins.add(matchingClass.getName()))
                    .matchClassesWithAnnotation(ConfigurationPlugin.class, matchingClass ->
                            configPlugins.add(matchingClass.getName()))
                    .matchClassesWithAnnotation(MetricsReporterPlugin.class, matchingClass ->
                            metricsReportingPlugins.add(matchingClass.getName()))
                    .matchClassesWithAnnotation(TracingPlugin.class, matchingClass ->
                            tracingPlugins.add(matchingClass.getName()))
                    .scan();

            if (serviceEntries.isEmpty()) {
                logger.error("No OrangeMicroservice classes found");
                System.exit(-1);
            } else if (serviceEntries.size() > 1) {
                logger.error("More than one OrangeMicroservice class found: {}", serviceEntries);
                System.exit(-1);
            }
            //create main class
            Class<?> serviceClass = Class.forName(serviceEntries.get(0));
            AbstractService service = (AbstractService) serviceClass.newInstance();

            displayHelpAndExitIfNeeded(args, service);

            service.enableJmx();

            service.initProperties(args);

            service.setConfigurationPlugins(configPlugins);
            service.setServiceRegistryPlugins(serviceRegistryPlugins);
            service.setMetricsReporterPlugins(metricsReportingPlugins);
            service.setTracingPlugins(tracingPlugins);

            service.initializeConfigurationManager();

            service.initializeGuice();

            service.initializeMetricsReporting();

            service.initializeServiceDiscovery();

            service.registerMethodHandlers(rpcHandlers);
            service.registerMethodHandlers();

            //we have to start the container before service registration happens to know the port
            service.startJettyContainer();

            service.initializeServiceRegistration();

            verifyEnvironmentAndExitIfNeeded(service);

            //we start health checks first so we can see services with bad state
            if (!hcProviders.isEmpty()) {
                service.initializeHealthCheckManager(hcProviders);
            }

            if (hasAnnotation(serviceClass.getAnnotations(), EnableDatabaseMigration.class)) {
                //this will block until the database is available.
                //it will then attempt a migration.  if the migration fails,
                //   the process emits an error and pauses.  it's senseless to continue.
                service.performDatabaseMigration();
            }

            service.bootstrapComplete();
        } catch (Exception ex) {
            logger.error("Uncaught exception running service", ex);
        }
    }

    private static void displayHelpAndExitIfNeeded(String[] args, AbstractService service) {
        for (String arg : args) {
            if (arg.endsWith("help")) {
                service.displayHelp(System.out);
                System.exit(0);
            }
        }
    }

    private static void verifyEnvironmentAndExitIfNeeded(AbstractService service) {
        try {
            service.verifyEnvironment();
        } catch (Exception e) {
            logger.error("Error while verifying environment - {}", e.getMessage(), e);
            System.exit(-1);
        }
    }

    private static void verifyDefaultCharset() {
        String charset = Charset.defaultCharset().name();
        logger.info("Found charset {}", charset);
        if (! charset.equals("UTF-8")) {
            Exception ex = new IllegalStateException("The default character set is not UTF-8.\n" +
                    "Please configure your runtime with the JVM option -Dfile.encoding=UTF8");
            logger.error("Bad configuration", ex);
            try {
                new Sleeper().sleep(Long.MAX_VALUE);
            } catch (InterruptedException ignored) {
            }
        }
    }

    private static boolean hasAnnotation(Annotation[] annotations, Class<?> targetAnnotation) {
        if ((annotations == null) || (annotations.length == 0)) {
            return false;
        }

        for (Annotation annotation : annotations) {
            if (annotation.annotationType().getSimpleName().equals(targetAnnotation.getSimpleName())) {
                return true;
            }
        }

        return false;
    }

}