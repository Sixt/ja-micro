package com.sixt.service.framework.kafka;

import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.Metric;
import org.apache.kafka.common.MetricName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Singleton;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;

@Singleton
public class PartitionAssignmentWatchdog {

    private static final Logger logger = LoggerFactory.getLogger(PartitionAssignmentWatchdog.class);

    public static final int SECONDS_BEFORE_KILLING = 30;

    private Set<KafkaConsumer<String, String>> consumers = new CopyOnWriteArraySet<>();
    private Map<String, ConsumerPartitionLevels> consumerPartitions = new HashMap<>();
    private ScheduledFuture<?> partitionWatchdogFuture;
    private ScheduledExecutorService scheduler;

    public synchronized void subscriberInitialized(KafkaConsumer<String, String> realConsumer) {
        logger.debug("Adding subscriber");
        consumers.add(realConsumer);
        if (consumers.size() == 1) {
            startWatchdog();
        }
    }

    public synchronized void subscriberShutdown(KafkaConsumer<String, String> realConsumer) {
        logger.debug("Removing subscriber");
        consumers.remove(realConsumer);
        if (consumers.size() == 0) {
            stopWatchdog();
        }
    }

    private synchronized void checkAssignments() {
        logger.debug("Checking partition assignments");

        try {
            KafkaConsumer<String, String> consumer = consumers.iterator().next();
            Map<MetricName, ? extends Metric> metrics = consumer.metrics();
            for (MetricName name : metrics.keySet()) {
                if ("assigned-partitions".equals(name.name())) {
                    Metric metric = metrics.get(name);
                    Map<String, String> tags = name.tags();
                    String clientId = tags.get("client-id");
                    int partitionCount = ((Double)metric.metricValue()).intValue();
                    processDataPoint(clientId, partitionCount, Instant.now());
                }
            }
        } catch (NoSuchElementException ex) {
        }
    }

    private void processDataPoint(String clientId, int partitionCount, Instant now) {
        consumerPartitions.putIfAbsent(clientId, new ConsumerPartitionLevels());
        ConsumerPartitionLevels clientInfo = consumerPartitions.get(clientId);
        if (partitionCount != clientInfo.lastKnownCount) {
            clientInfo.lastKnownCount = partitionCount;
            clientInfo.lastChangeDate = now;
        }
        if (clientInfo.lastKnownCount == 0 && clientInfo.lastChangeDate
                .isBefore(now.minus(SECONDS_BEFORE_KILLING, ChronoUnit.SECONDS))) {
            logger.error("TESTING: would now kill the application because consumer {} has had no " +
                    "partition assignments for more than {} seconds", clientId, SECONDS_BEFORE_KILLING);
        }
    }

    private void startWatchdog() {
        scheduler = Executors.newSingleThreadScheduledExecutor();
        partitionWatchdogFuture = scheduler.scheduleAtFixedRate(
                this::checkAssignments, 10, 10, TimeUnit.SECONDS);
    }

    private void stopWatchdog() {
        partitionWatchdogFuture.cancel(true);
        scheduler.shutdown();
        logger.debug("Partition assignment watchdog scheduler shut down");
    }

    class ConsumerPartitionLevels {
        int lastKnownCount = -1;
        Instant lastChangeDate = Instant.ofEpochSecond(0);

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ConsumerPartitionLevels that = (ConsumerPartitionLevels) o;
            return lastKnownCount == that.lastKnownCount &&
                    Objects.equals(lastChangeDate, that.lastChangeDate);
        }

        @Override
        public int hashCode() {
            return Objects.hash(lastKnownCount, lastChangeDate);
        }
    }

}
