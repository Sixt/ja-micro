package com.sixt.service.test_service.handler;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.sixt.service.framework.kafka.EventReceivedCallback;
import com.sixt.service.framework.kafka.KafkaSubscriber;
import com.sixt.service.framework.kafka.KafkaSubscriberFactory;
import com.sixt.service.framework.kafka.KafkaTopicInfo;
import com.sixt.service.test_service.api.TestServiceOuterClass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class RandomEventHandler implements EventReceivedCallback<TestServiceOuterClass.RandomSampleEvent> {

    private static Logger logger = LoggerFactory.getLogger(RandomEventHandler.class);

    private final KafkaSubscriber subscriber;

    @Inject
    public RandomEventHandler(KafkaSubscriberFactory factory) {
        this.subscriber = factory.newBuilder("events", this).build();
    }

    @Override
    public void eventReceived(TestServiceOuterClass.RandomSampleEvent message, KafkaTopicInfo topicInfo) {
        try {
            logger.info("Handling RandomSampleEvent event: {}", message);
            // do some handling
        } catch (Exception ex) {
            logger.warn("Handling RandomSampleEvent event failed", ex);
        } finally {
            subscriber.consume(topicInfo);
        }
    }
}
