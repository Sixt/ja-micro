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

package com.sixt.service.framework.rpc;

import org.eclipse.jetty.client.api.ContentResponse;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;

public interface RpcCallExceptionDecoder {

    RpcCallException decodeException(ContentResponse response) throws RpcCallException;

    static String exceptionToString(Throwable ex) {
        if (ex == null) {
            return "null";
        }
        StringWriter str = new StringWriter();
        str.append("Exception: ").append(ex.getClass().getSimpleName());
        str.append(" Message: ").append(ex.getMessage());
        str.append(" Stacktrace: ");
        Throwable cause = ex.getCause();
        if (cause != null) {
            str.append("\nCause: ").append(exceptionToString(cause));
        }
        PrintWriter writer = new PrintWriter(str);
        try {
            ex.printStackTrace(writer);
            return str.getBuffer().toString();
        } finally {
            try {
                str.close();
                writer.close();
            } catch (IOException e) {}
        }
    }

}
