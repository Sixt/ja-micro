package com.sixt.service.framework.kafka.messaging;

import com.google.protobuf.Parser;
import com.sixt.service.framework.OrangeContext;
import com.sixt.service.framework.metrics.MetricBuilderFactory;
import io.opentracing.Tracer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static junit.framework.TestCase.*;

public class PartitionProcessorTest {

    private static final String TOPIC = "aTopic";
    private static final int PARTITION = 42;
    private static final String KEY = "aKey";

    static class TestHandler implements MessageHandler {

        final CountDownLatch onMessageCalled = new CountDownLatch(1);
        final CountDownLatch blockReturnFromOnMessage = new CountDownLatch(1);
        final List<Message> handledMessage = Collections.synchronizedList(new ArrayList<>());
        volatile Message lastMessage = null;
        volatile OrangeContext lastContext = null;
        volatile RuntimeException exceptionToBeThrown = null;


        @Override
        public void onMessage(Message msg, OrangeContext context) {
            handledMessage.add(msg);
            lastMessage = msg;
            lastContext = context;

            onMessageCalled.countDown();

            if (exceptionToBeThrown != null) {
                throw exceptionToBeThrown;
            }

            try {
                blockReturnFromOnMessage.await();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }


    static class TestTypeDictionary extends TypeDictionary {

        TestHandler handler = new TestHandler();

        public TestTypeDictionary() {
            super(new HashMap<>(), new HashMap<>());
        }

        @Override
        public MessageHandler messageHandlerFor(MessageType type) {
            return handler;
        }

        @Override
        public Parser parserFor(MessageType type) {
            return EmptyMessage.parser();
        }
    }


    private PartitionProcessor givenAPartionProcessor() {
        TopicPartition topicKey = new TopicPartition(TOPIC, PARTITION);

        TypeDictionary typeDictionary = new TestTypeDictionary();
        FailedMessageProcessor failedMessageProcessor = new DiscardFailedMessages();


        MetricBuilderFactory metricsBuilderFactory = null;
        Tracer tracer = null;


        return new PartitionProcessor(topicKey, typeDictionary, failedMessageProcessor, tracer, metricsBuilderFactory);
    }

    private ConsumerRecord<String, byte[]> testRecordWithOffset(long offset) {
        Envelope.Builder envelope = Envelope.newBuilder();
        envelope.setMessageId("cruft");

        return new ConsumerRecord<String, byte[]>(TOPIC, PARTITION, offset, KEY, envelope.build().toByteArray());
    }


    private ConsumerRecord<String, byte[]> simulateKafkaInTheLoop(Message message, long offset) {
        Envelope envelope = Messages.toKafka(message);
        return new ConsumerRecord<String, byte[]>(message.getMetadata().getTopic().toString(), PARTITION, offset, message.getMetadata().getPartitioningKey(), envelope.toByteArray());
    }

    private void shortSleep() {
        try {
            Thread.sleep(100);
        } catch (InterruptedException ignored) {

        }
    }

    private TestHandler getTestHandler(PartitionProcessor processor) {
        return (TestHandler) processor.getTypeDictionary().messageHandlerFor(null);
    }

    @Test
    public void basicLifecycle() throws InterruptedException {
        PartitionProcessor processor = givenAPartionProcessor();
        assertFalse(processor.isTerminated());

        assertFalse(processor.isPaused());
        assertTrue(processor.shouldResume());

        assertEquals(0, processor.numberOfUnprocessedMessages());
        assertFalse(processor.hasUncommittedMessages());
        assertEquals(-1, processor.getCommitOffsetAndClear());

        processor.waitForHandlersToTerminate(1);
        assertTrue(processor.isTerminated());

        assertEquals(0, processor.numberOfUnprocessedMessages());
        assertFalse(processor.hasUncommittedMessages());
        assertEquals(-1, processor.getCommitOffsetAndClear());
    }


    @Test
    public void consumeOneMessage() throws InterruptedException {
        PartitionProcessor processor = givenAPartionProcessor();

        ConsumerRecord<String, byte[]> record = testRecordWithOffset(999);
        processor.enqueue(record);

        (getTestHandler(processor)).onMessageCalled.await();

        // message not yet marked as consumed
        assertEquals(0, processor.numberOfUnprocessedMessages());
        assertFalse(processor.hasUncommittedMessages());
        assertEquals(-1, processor.getCommitOffsetAndClear());

        (getTestHandler(processor)).blockReturnFromOnMessage.countDown();
        Thread.sleep(1000); // allow handler to continue

        assertTrue(processor.hasUncommittedMessages());
        assertEquals(1000, processor.getCommitOffsetAndClear());

        // getCommitOffsetAndClear clears hasUncommittedMessages
        assertFalse(processor.hasUncommittedMessages());
        assertEquals(1000, processor.getCommitOffsetAndClear());

        processor.waitForHandlersToTerminate(50);
    }


    @Test
    public void pauseProcessorIfBacklogOfUnconsumedMessagesToBig() throws InterruptedException {
        PartitionProcessor processor = givenAPartionProcessor();

        for (int i = 1; i <= PartitionProcessor.MAX_MESSAGES_IN_FLIGHT + 2; i++) {
            ConsumerRecord<String, byte[]> record = testRecordWithOffset(i);
            processor.enqueue(record);
        }


        while (processor.numberOfUnprocessedMessages() < PartitionProcessor.MAX_MESSAGES_IN_FLIGHT + 1) {
            shortSleep(); // allow to fill queue
        }

        assertEquals(PartitionProcessor.MAX_MESSAGES_IN_FLIGHT + 1, processor.numberOfUnprocessedMessages()); // 1 message currently in handler
        assertTrue(processor.isPaused()); // should throttle
        assertFalse(processor.shouldResume());

        (getTestHandler(processor)).onMessageCalled.await();
        (getTestHandler(processor)).blockReturnFromOnMessage.countDown();

        shortSleep(); // allow handler to continue

        assertTrue(processor.hasUncommittedMessages());
        assertEquals(PartitionProcessor.MAX_MESSAGES_IN_FLIGHT + 3, processor.getCommitOffsetAndClear());

        assertFalse(processor.isPaused()); // after emptying the queue, processor should resume
        assertTrue(processor.shouldResume());

        processor.waitForHandlersToTerminate(50);
    }


    @Test
    public void shutDownEvenIfHandlerIsStillActive() throws InterruptedException {
        PartitionProcessor processor = givenAPartionProcessor();

        ConsumerRecord<String, byte[]> record = testRecordWithOffset(999);
        processor.enqueue(record);
        (getTestHandler(processor)).onMessageCalled.await();

        processor.stopProcessing();
        assertFalse(processor.isTerminated());

        processor.waitForHandlersToTerminate(10); // should log a warn about still running handlers
        assertTrue(processor.isTerminated());
        assertFalse(processor.hasUncommittedMessages());


        // If handler finishes, it will mark message as committable, but at this time, the processor is probably already removed.
        (getTestHandler(processor)).blockReturnFromOnMessage.countDown();
        shortSleep(); // allow handler to continue
        assertTrue(processor.hasUncommittedMessages());
    }


    @Test
    public void afterProcesserIsStoppedMessagesToBeEnqueuedWillBeIgnored() throws InterruptedException {
        PartitionProcessor processor = givenAPartionProcessor();
        processor.stopProcessing();

        ConsumerRecord<String, byte[]> record = testRecordWithOffset(999);
        processor.enqueue(record); // should log a message that messages are ignored (and never committed to Kafka)

        // assert handler not called
        assertFalse((getTestHandler(processor)).onMessageCalled.await(100, TimeUnit.MILLISECONDS));

        processor.waitForHandlersToTerminate(1);
    }


    @Test
    public void requestResponseCylce() throws InterruptedException {
        PartitionProcessor processor = givenAPartionProcessor();

        EmptyMessage payload = EmptyMessage.getDefaultInstance();
        OrangeContext context = new OrangeContext();


        ConsumerRecord<String, byte[]> aRecord;

        Message sentRequest = Messages.requestFor(Topic.defaultServiceInbox("com.sixt.service.cruft"), Topic.serviceInbox("com.sixt.service.cruft", "trashcan"), "requestKey", payload, context);
        aRecord = simulateKafkaInTheLoop(sentRequest, 10);
        processor.enqueue(aRecord);

        getTestHandler(processor).onMessageCalled.await();
        Message receivedRequest = getTestHandler(processor).lastMessage;
        OrangeContext receivedContext = getTestHandler(processor).lastContext;

        Message sentReply = Messages.replyTo(receivedRequest, payload, receivedContext);
        aRecord = simulateKafkaInTheLoop(sentReply, 20);
        processor.enqueue(aRecord);

        getTestHandler(processor).blockReturnFromOnMessage.countDown();
        shortSleep(); // for the reply handling

        Message receivedReply = getTestHandler(processor).lastMessage;
        assertEquals(2, getTestHandler(processor).handledMessage.size());


        assertEquals(sentRequest.getMetadata().getReplyTo(), receivedReply.getMetadata().getTopic());
        assertEquals(sentRequest.getMetadata().getMessageId(), receivedReply.getMetadata().getRequestCorrelationId());
        assertEquals(context.getCorrelationId(), receivedReply.getMetadata().getCorrelationId());

        processor.waitForHandlersToTerminate(1);
    }


    @Test
    public void exceptionInHandlerShouldConsumeMessage() throws InterruptedException {
        PartitionProcessor processor = givenAPartionProcessor();
        processor.enqueue(testRecordWithOffset(42));

        getTestHandler(processor).exceptionToBeThrown = new RuntimeException("BOOM");
        getTestHandler(processor).onMessageCalled.await();
        shortSleep();

        assertTrue(processor.hasUncommittedMessages());
        assertEquals(43, processor.getCommitOffsetAndClear());

        processor.waitForHandlersToTerminate(1);
    }


    @Test
    public void retryFailedMessages() throws InterruptedException {
        TopicPartition topicKey = new TopicPartition(TOPIC, PARTITION);
        TypeDictionary typeDictionary = new TestTypeDictionary();
        FailedMessageProcessor failedMessageProcessor = new DelayAndRetryOnRecoverableErrors(new DiscardFailedMessages(), new SimpleRetryDelayer(10, 100));
        PartitionProcessor processor = new PartitionProcessor(topicKey, typeDictionary, failedMessageProcessor, null, null);

        processor.enqueue(testRecordWithOffset(42));
        getTestHandler(processor).exceptionToBeThrown = new RuntimeException("BOOM");
        getTestHandler(processor).onMessageCalled.await();
        shortSleep();
        shortSleep();

        assertTrue(processor.hasUncommittedMessages());
        assertEquals(43, processor.getCommitOffsetAndClear());
        processor.waitForHandlersToTerminate(1);
    }

}

