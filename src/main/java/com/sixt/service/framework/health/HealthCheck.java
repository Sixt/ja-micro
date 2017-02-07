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

package com.sixt.service.framework.health;

/**
 * HealthCheck information is polled from configured sources and merged together.
 * If all are of PASS status, the result is PASS.  Any WARN or FAIL statuses will
 * set the overall status to that, and contributed messages will be available.
 */
public class HealthCheck {

    public enum Status {
        PASS, WARN, FAIL;

        public boolean moreSevereThan(Status other) {
            if (this.equals(WARN) && other.equals(PASS)) {
                return true;
            } else if (this.equals(FAIL) && (other.equals(WARN) || other.equals(PASS))) {
                return true;
            } else {
                return false;
            }
        }

        public String getConsulStatus() {
            return name().toLowerCase();
        }
    }

    private String name = "not_set";
    private Status status = Status.PASS;
    private String message = "";

    public HealthCheck() {
    }

    public HealthCheck(String name, Status status, String message) {
        this.name = name;
        this.status = status;
        this.message = message;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public Status getStatus() {
        return status;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }

    public boolean isStatus(Status s) {
        return status.equals(s);
    }

}
