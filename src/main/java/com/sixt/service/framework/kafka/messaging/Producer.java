package com.sixt.service.framework.kafka.messaging;

import com.sixt.service.framework.OrangeContext;
import com.sixt.service.framework.kafka.SixtPartitioner;
import com.sixt.service.framework.protobuf.MessagingEnvelope;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

public class Producer {
    private static final Logger logger = LoggerFactory.getLogger(Producer.class);

    protected org.apache.kafka.clients.producer.KafkaProducer<String, byte[]> realProducer;
    protected AtomicBoolean isInitialized = new AtomicBoolean(false);

    // TODO move to ctor?
    public void initialize(String servers) {
        if (isInitialized.get()) {
            logger.warn("Already initialized");
            return;
        }

        Properties props = new Properties();
        props.put(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, servers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class.getName());
        props.put(ProducerConfig.PARTITIONER_CLASS_CONFIG, SixtPartitioner.class.getName());
        realProducer = new org.apache.kafka.clients.producer.KafkaProducer<>(props);
        isInitialized.set(true);
    }

    public void shutdown() {
        realProducer.close();
        realProducer = null;
    }


    public void send(Message message) {
        if (!isInitialized.get()) {
            throw new IllegalStateException("KafkaProducer is not initialized.");
        }

        String destinationTopic = message.getMetadata().getTopic().toString();
        String partitioningKey = message.getMetadata().getPartitioningKey();
        MessagingEnvelope envelope = Messages.toKafka(message);

        ProducerRecord<String, byte[]> record = new ProducerRecord<>(destinationTopic, partitioningKey, envelope.toByteArray());

        try {
            Future future = realProducer.send(record);
            future.get();
        } catch (Exception ex) {
            // TODO proper exception
            throw new RuntimeException(ex);
        }
    }
}
