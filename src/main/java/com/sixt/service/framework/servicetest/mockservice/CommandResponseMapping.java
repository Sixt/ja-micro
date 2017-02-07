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

package com.sixt.service.framework.servicetest.mockservice;

import com.google.protobuf.Message;

public class CommandResponseMapping {

    private String command;
    private Message response;

    private CommandResponseMapping(MappingBuilder builder) {
        this.command = builder.getCommand();
        this.response = builder.getResponse();
    }

    public static MappingBuilder newBuilder() {
        return new MappingBuilder();
    }

    public String getCommand() {
        return command;
    }

    public Message getResponse() {
        return response;
    }

    public static class MappingBuilder {
        private String command;
        private Message response;

        public MappingBuilder setCommand(String command) {
            this.command = command;
            return this;
        }

        public MappingBuilder setResponse(Message response) {
            this.response = response;
            return this;
        }

        public CommandResponseMapping build() {
            return new CommandResponseMapping(this);
        }

        public String getCommand() {
            return command;
        }

        public Message getResponse() {
            return response;
        }
    }
}
