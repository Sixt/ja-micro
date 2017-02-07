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

package com.sixt.service.framework.registry.consul;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

public class ConsulHealthEntry {

    enum Status {
        Passing, Warning, Critical;

        public static Status fromString(String status) {
            switch (status) {
                case "passing":
                    return Passing;
                case "warning":
                    return Warning;
                case "critical":
                    return Critical;
                default:
                    return Passing;
            }
        }
    }

    private String id;
    private Status status;
    String address;
    int port;

    public ConsulHealthEntry(String id, Status status, String address, int port) {
        this.id = id;
        this.status = status;
        this.address = address;
        this.port = port;
    }

    public String getId() {
        return id;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public String getAddressAndPort() {
        return address + ":" + port;
    }

    //TODO: implement when we do avail zones
    public String getAvailZone() {
        return "";
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder().append(id).append(status).append(address).append(port).build();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) { return false; }
        if (obj == this) { return true; }
        if (obj.getClass() != getClass()) {
            return false;
        }
        ConsulHealthEntry rhs = (ConsulHealthEntry) obj;
        return new EqualsBuilder().append(id, rhs.id).
                append(status, rhs.status).
                append(address, rhs.address).
                append(port, rhs.port).build();
    }

}
