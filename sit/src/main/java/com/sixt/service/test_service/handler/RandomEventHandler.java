package com.sixt.service.test_service.handler;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.sixt.service.framework.kafka.EventReceivedCallback;
import com.sixt.service.framework.kafka.KafkaSubscriber;
import com.sixt.service.framework.kafka.KafkaSubscriberFactory;
import com.sixt.service.framework.kafka.KafkaTopicInfo;
import com.sixt.service.test_service.api.TestServiceOuterClass;
import com.sixt.service.test_service.infrastructure.RandomEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class RandomEventHandler implements EventReceivedCallback<TestServiceOuterClass.RandomSampleEvent> {

    private static Logger logger = LoggerFactory.getLogger(RandomEventHandler.class);

    private final KafkaSubscriber subscriber;
    private final RandomEventPublisher publisher;

    @SuppressWarnings("unchecked")
    @Inject
    public RandomEventHandler(KafkaSubscriberFactory factory, RandomEventPublisher publisher) {
        this.subscriber = factory.newBuilder("events.RandomTopic", this).build();
        this.publisher = publisher;
    }

    @Override
    public void eventReceived(TestServiceOuterClass.RandomSampleEvent message, KafkaTopicInfo topicInfo) {
        try {
            logger.info("Handling RandomSampleEvent event: {}", message);
            // do some handling
            publishSuccessEvent(message.getId(), message.getMessage());
        } catch (Exception ex) {
            logger.warn("Handling RandomSampleEvent event failed", ex);
        } finally {
            subscriber.consume(topicInfo);
        }
    }

    private void publishSuccessEvent(String id, String message) {
        publisher.publishSync(TestServiceOuterClass.HandlerSuccessEvent.newBuilder()
                .setMeta(TestServiceOuterClass.Meta.newBuilder()
                        .setName(TestServiceOuterClass.HandlerSuccessEvent.getDescriptor().getName())
                        .build())
                .setId(id)
                .setMessage(message)
                .build());
    }
}
