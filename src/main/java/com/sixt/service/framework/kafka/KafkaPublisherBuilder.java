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

package com.sixt.service.framework.kafka;

import java.util.Map;

public class KafkaPublisherBuilder {

    private final Map<String, String> properties;

    private KafkaPublisherFactory parentFactory;

    protected String topic;

    KafkaPublisherBuilder(KafkaPublisherFactory factory, String topic, Map<String, String> properties) {
        this.parentFactory = factory;
        this.topic = topic;
        this.properties = properties;
    }

    public KafkaPublisher build() {
        KafkaPublisher retval = new KafkaPublisher(topic, properties);
        parentFactory.builtPublisher(retval);
        return retval;
    }

}
