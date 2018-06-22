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

package com.squareup.wire.schema.internal.parser;

import java.io.File;

public class RpcMethodDefinition {

    protected String methodName;
    protected String requestType;
    protected String responseType;
    protected String packageName;
    protected String sourceFileName;

    public RpcMethodDefinition(String methodName, String requestType, String responseType, String packageName, String sourceFileName) {
        this.methodName = methodName;
        this.requestType = requestType;
        this.responseType = responseType;
        this.packageName = packageName;
        this.sourceFileName = sourceFileName;
    }

    public String getMethodName() {
        return methodName;
    }

    public String getRequestType() {
        return requestType;
    }

    public String getResponseType() {
        return responseType;
    }

    public String getPackageName() {
        return packageName;
    }

    public String getSourceFileName() {
        return sourceFileName;
    }
}
