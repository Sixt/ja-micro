package com.sixt.service.framework.servicetest.helper;

import com.palantir.docker.compose.DockerComposeRule;
import com.palantir.docker.compose.configuration.DockerComposeFiles;
import com.palantir.docker.compose.configuration.ProjectName;
import com.palantir.docker.compose.connection.Cluster;
import com.palantir.docker.compose.connection.Container;
import com.palantir.docker.compose.connection.DockerPort;
import com.palantir.docker.compose.connection.waiting.ClusterHealthCheck;
import com.palantir.docker.compose.connection.waiting.ClusterWait;
import com.palantir.docker.compose.connection.waiting.HealthCheck;
import com.palantir.docker.compose.execution.ConflictingContainerRemovingDockerCompose;
import com.palantir.docker.compose.execution.Docker;
import com.palantir.docker.compose.execution.DockerCompose;
import com.palantir.docker.compose.logging.LogCollector;
import org.joda.time.ReadableDuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Staged DockerCompose start up: multiple services and cluster waits can be bundled to a stage. A start up consists of
 * multiple stages that are executed in order: all services of a stage are started (the equivalent of docker-compose up -d name) and
 * all waits are executed. Then the rule proceeds with the next stage.
 * <p>
 * There is a default stage that takes all waits not explicitly assigned to a stage.
 */
public class StagedDockerComposeRule extends DockerComposeRule {

    /*
        Implementation note:
        DockerComposeRule uses immutables.github.io to generate ImmutableDockerComposeRule which is the concrete class users interact with.
        This makes the extension of DockerComposeRule a fairly unpleasant job since we don't want to use code generation here.

        Here's what you need to do:
        For each attribute you want to set in the StagedDockerComposeRule, add methods to the Builder and transfer them to
        the StagedDockerComposeRule via constructor. In addition, you need to override the getter method specifying a default
        in the base class.

     */

    private static final Logger log = LoggerFactory.getLogger(DockerComposeRule.class);

    private final ProjectName projectName;
    private final DockerComposeFiles files;
    private final DynamicFileLogCollector logCollector;

    private final List<Stage> stages;

    /**
     * Build by StagedDockerComposeRule.Builder
     *
     * @param files
     * @param logCollector
     * @param stages
     */
    StagedDockerComposeRule(ProjectName name, DockerComposeFiles files, DynamicFileLogCollector logCollector, List<Stage> stages) {
        this.projectName = name;
        this.files = files;
        this.stages = Collections.unmodifiableList(stages);
        this.logCollector = logCollector;
    }

    @Override
    public DockerComposeFiles files() {
        return files;
    }


    @Override
    public ProjectName projectName() {
        return projectName;
    }

    @Override
    public LogCollector logCollector() {
        return logCollector;
    }


    @Override
    @Deprecated
    protected List<ClusterWait> clusterWaits() {
        throw new UnsupportedOperationException("Unsupported operation since a global list of waits does not make sense in this context.");
    }

    // Test access
    List<Stage> stages() {
        return Collections.unmodifiableList(stages);
    }

    @Override
    public void before() throws IOException, InterruptedException {
        // Partially copy-pasted from com.palantir.docker.compose.DockerComposeRule

        log.debug("Starting docker-compose cluster");

        Docker docker = docker();
        DockerCompose dockerCompose = dockerCompose();

        if (pullOnStartup()) {
            dockerCompose.pull();
        }
        dockerCompose.build();

        if (removeConflictingContainersOnStartup()) {
            dockerCompose = new ConflictingContainerRemovingDockerCompose(dockerCompose, docker);
        }


        for (Stage stage : stages) {
            log.debug("Starting stage {}", stage.getName());

            if (stage.getServices().isEmpty()) {
                // the default stage that contains all not explicitly mentioned services
                // we do a unqualified docker-compose up
                dockerCompose.up();
                log.debug("Starting all remaining services");
                logCollector.startCollecting(dockerCompose);
            } else {
                for (String service : stage.getServices()) {
                    Container serviceContainer = new Container(service, docker, dockerCompose);
                    dockerCompose.up(serviceContainer);
                    log.debug("Starting service {}", service);

                    // TODO: do we have a race between docker-compose up and docker-compose logs ??

                    // Incrementally add newly started services to the set of collected logs.
                    logCollector.startCollecting(dockerCompose, service);
                }
            }

            Cluster containers = this.containers();

            ClusterWait nativeHealthCheck = new ClusterWait(ClusterHealthCheck.nativeHealthChecks(), this.nativeServiceHealthCheckTimeout());
            nativeHealthCheck.waitUntilReady(containers);

            for (ClusterWait clusterWait : stage.getWaits()) {
                clusterWait.waitUntilReady(containers);
            }

            log.debug("Waiting for services of stage {}", stage.getName());
        }

        log.debug("docker-compose cluster started");
    }


    public static Builder customBuilder() {
        return new Builder();
    }

    @Deprecated
    public static DockerComposeRule.Builder builder() {
        throw new UnsupportedOperationException("Use customBuilder instead.");
    }

    public static class Builder {
        // Part of this Build was copy-pasted from com.palantir.docker.compose.DockerComposeRule.Builder

        private final List<Stage> stages = new ArrayList<>();
        private final Stage defaultStage = new Stage("default");
        private Stage currentExplicitStage = null;

        private DynamicFileLogCollector logCollector;
        private DockerComposeFiles files;
        private ProjectName projectName = ProjectName.random();

        // Global settings

        public Builder file(String dockerComposeYmlFile) {
            if (files != null) {
                throw new IllegalStateException("Files is already set.");
            }
            files = DockerComposeFiles.from(new String[]{dockerComposeYmlFile});
            return this;
        }

        public Builder saveLogsTo(String path) {
            if (logCollector != null) {
                throw new IllegalStateException("Log file path is alreay set.");
            }
            logCollector = DynamicFileLogCollector.fromPath(path);
            return this;
        }

        public Builder projectName(ProjectName name) {
            projectName = name;
            return this;
        }

        /**
         * @throws UnsupportedOperationException
         * @deprecated
         */
        @Deprecated
        public Builder skipShutdown(boolean skipShutdown) {
            throw new UnsupportedOperationException("skipShutdown no longer supported");
        }

        // Waits can be applied either to an explict stage or the default stage.

        public Builder waitingForService(String serviceName, HealthCheck<Container> healthCheck) {
            return this.waitingForService(serviceName, healthCheck, DockerComposeRule.DEFAULT_TIMEOUT);
        }

        public Builder waitingForService(String serviceName, HealthCheck<Container> healthCheck, ReadableDuration timeout) {
            ClusterHealthCheck clusterHealthCheck = ClusterHealthCheck.serviceHealthCheck(serviceName, healthCheck);
            return this.addClusterWait(new ClusterWait(clusterHealthCheck, timeout));
        }

        public Builder waitingForServices(List<String> services, HealthCheck<List<Container>> healthCheck) {
            return this.waitingForServices(services, healthCheck, DockerComposeRule.DEFAULT_TIMEOUT);
        }

        public Builder waitingForServices(List<String> services, HealthCheck<List<Container>> healthCheck, ReadableDuration timeout) {
            ClusterHealthCheck clusterHealthCheck = ClusterHealthCheck.serviceHealthCheck(services, healthCheck);
            return this.addClusterWait(new ClusterWait(clusterHealthCheck, timeout));
        }

        public Builder waitingForHostNetworkedPort(int port, HealthCheck<DockerPort> healthCheck) {
            return this.waitingForHostNetworkedPort(port, healthCheck, DockerComposeRule.DEFAULT_TIMEOUT);
        }

        public Builder waitingForHostNetworkedPort(int port, HealthCheck<DockerPort> healthCheck, ReadableDuration timeout) {
            ClusterHealthCheck clusterHealthCheck = ClusterHealthCheck.transformingHealthCheck((cluster) -> {
                return new DockerPort(cluster.ip(), port, port);
            }, healthCheck);
            return this.addClusterWait(new ClusterWait(clusterHealthCheck, timeout));
        }

        public Builder clusterWaits(Iterable<? extends ClusterWait> elements) {
            return this.addAllClusterWaits(elements);
        }

        private Builder addAllClusterWaits(Iterable<? extends ClusterWait> elements) {
            for (ClusterWait clusterWait : elements) {
                this.addClusterWait(clusterWait);
            }
            return this;
        }

        private Builder addClusterWait(ClusterWait clusterWait) {
            activeStage().addClusterWait(clusterWait);
            return this;
        }

        // Staging

        /**
         * Opens a stage to start one or multiple services.
         *
         * @param name
         * @return
         */
        public Builder stage(String name) {
            currentExplicitStage = new Stage(name);
            stages.add(currentExplicitStage);
            return this;
        }

        /**
         * End the current explicit stage and put everything into default stage from now on.
         */
        public Builder defaultStage() {
            currentExplicitStage = null;
            return this;
        }

        private Stage activeStage() {
            return currentExplicitStage != null ? currentExplicitStage : defaultStage;
        }

        /**
         * Adds a service to a stage.
         *
         * @param service the name of the service as specified in the docker compose files.
         * @return
         */
        public Builder service(String service) {
            if (currentExplicitStage == null) {
                throw new IllegalStateException("You need to open a stage if you want start a service explicitly.");
            }

            currentExplicitStage.addService(service);
            return this;
        }

        public StagedDockerComposeRule build() {
            // default stage is always executed and the last stage in the chain
            stages.add(defaultStage);

            return new StagedDockerComposeRule(projectName, files, logCollector, stages);
        }
    }


    static class Stage {

        final List<String> services = new ArrayList<>();

        final List<ClusterWait> waits = new ArrayList<>();

        final String name;

        Stage(String name) {
            this.name = name;
        }

        List<String> getServices() {
            return services;
        }

        List<ClusterWait> getWaits() {
            return waits;
        }

        String getName() {
            return name;
        }

        void addService(String service) {
            services.add(service);
        }

        void addClusterWait(ClusterWait clusterWait) {
            waits.add(clusterWait);
        }
    }
}