package com.sixt.service.framework.kafka.messaging;

import com.google.protobuf.Parser;
import com.sixt.service.framework.OrangeContext;
import com.sixt.service.framework.protobuf.MessagingEnvelope;
import com.sixt.service.framework.protobuf.MessagingEnvelopeOrBuilder;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.junit.Test;

import static junit.framework.TestCase.*;

public class PartitionProcessorTest {

    static class TestHandler implements MessageHandler {
        @Override
        public void onMessage(Message msg, OrangeContext context) {
            System.out.println("Kruft!");
        }
    }


    static class TestTypeDictionary implements TypeDictionary {

        @Override
        public MessageHandler messageHandlerFor(MessageType type) {
            return new TestHandler();
        }

        @Override
        public Parser parserFor(MessageType type) {
            return MessagingEnvelope.parser();
        }
    }


    @Test
    public void basicTest() throws InterruptedException {
        TopicPartition topicKey = new TopicPartition("aTopic", 42);
        TypeDictionary typeDictionary = new TestTypeDictionary();
        FailedMessageProcessor failedMessageProcessor = new DiscardFailedMessages();

        MessagingEnvelope.Builder envelope = MessagingEnvelope.newBuilder();
        envelope.setMessageId("anId");



        PartitionProcessor processor = new PartitionProcessor(topicKey, typeDictionary, failedMessageProcessor);
        assertFalse(processor.isTerminated());

        assertFalse(processor.isPaused());
        assertTrue(processor.shouldResume());

        assertFalse(processor.hasUncommittedMessages());
        assertEquals(-1, processor.getCommitOffsetAndClear());


        processor.enqueue(new ConsumerRecord<String, byte[]>("aTopic", 42, 1, "key", envelope.build().toByteArray()));


        Thread.sleep(1000);

        processor.waitForHandlersToTerminate(50);

        assertTrue(processor.isTerminated());
    }

}
