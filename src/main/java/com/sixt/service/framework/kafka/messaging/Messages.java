package com.sixt.service.framework.kafka.messaging;

import com.sixt.service.framework.protobuf.MessagingEnvelope;

public class Messages {

    private Messages() {
        // Prevent instantiation.
    }

    public static Builder replyTo(Message request) {
        return new Builder(request);
    }

    public static Builder requestFor(Topic target) {
        return new Builder(target);
    }

    static Message fromKafka(com.google.protobuf.Message payload, MessagingEnvelope envelope) {
        // FIXME
        return null;
    }

    public static class Builder {
        private Message referencedMessage;
        private Topic target;

        Builder(Topic target) {
            this.target = target;
        }

        Builder(Message referencedMessage) {
            this.referencedMessage = referencedMessage;
        }

        public Message with(com.google.protobuf.Message payload) {
          Metadata metadata = new Metadata();
         return new Message(payload, metadata);
        }
    }


}
