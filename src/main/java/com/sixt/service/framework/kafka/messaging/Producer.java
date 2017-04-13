package com.sixt.service.framework.kafka.messaging;

import com.sixt.service.framework.kafka.SixtPartitioner;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public final class Producer {
    private static final Logger logger = LoggerFactory.getLogger(Producer.class);

    private final KafkaProducer<String, byte[]> kafka;

    // Instances are to be created by ProducerFactory
    Producer(Properties kafkaProducerConfig) {

        // Mandatory settings, not changeable.
        kafkaProducerConfig.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        kafkaProducerConfig.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class.getName());
        kafkaProducerConfig.put(ProducerConfig.PARTITIONER_CLASS_CONFIG, SixtPartitioner.class.getName());

        kafka = new org.apache.kafka.clients.producer.KafkaProducer<>(kafkaProducerConfig);
        logger.info("Created producer.");
    }

    public void shutdown() {
        try {
            kafka.close(90, TimeUnit.SECONDS);
        } catch (Exception unexpected) {
            logger.warn("Ignored unexpected exception in producer shut down", unexpected);
        }

        logger.info("Shut down producer.");
    }

    public void send(Message message) {
        String destinationTopic = message.getMetadata().getTopic().toString();
        String partitioningKey = message.getMetadata().getPartitioningKey();
        Envelope envelope = Messages.toKafka(message);

        ProducerRecord<String, byte[]> record = new ProducerRecord<>(destinationTopic, partitioningKey, envelope.toByteArray());

        try {
            logger.debug(message.getMetadata().getLoggingMarker(), "Sending message {} with key {} to topic {}", message.getMetadata().getType().toString(), partitioningKey, destinationTopic);

            Future future = kafka.send(record);
            future.get();

        } catch (InterruptedException ex) {
            logger.warn(message.getMetadata().getLoggingMarker(), "Producer interrupted while waiting on future.get() of kafka.send(record). It is unknown if the message has been sent.", ex);
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();

            logger.warn(message.getMetadata().getLoggingMarker(), "Error sending message", cause);

            // Examples for Exceptions seen during testing:
            // org.apache.kafka.common.errors.NetworkException: The server disconnected before a response was received.
            // org.apache.kafka.common.errors.TimeoutException: Expiring 1 record(s) for ping-2 due to 30180 ms has passed since batch creation plus linger time
            // org.apache.kafka.common.errors.UnknownTopicOrPartitionException: This server does not host this topic-partition.
            // org.apache.kafka.common.errors.NotLeaderForPartitionException: This server is not the leader for that topic-partition.

            // The error handling strategy here is to not retry here but pass to the caller:
            // If for example the producer is used in a synchronous context, it probably does not make sense to retry.
            // However, in an asynchronous context (e.g. in a MessageHandler) it would be wise to retry.
            if (cause instanceof RuntimeException) {
                throw (RuntimeException)cause;
            } else {
                throw new RuntimeException(ex);
            }
        }
    }
}

