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

package com.sixt.service.framework.jetty;

import com.google.gson.JsonObject;
import org.apache.commons.io.IOUtils;

import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServletRequest;
import java.util.Base64;
import java.util.Enumeration;

public class RpcReadException extends Exception {
    private ServletInputStream in;
    private byte[] incomplete;

    public RpcReadException(byte[] incomplete, ServletInputStream in, String message) {
        super(message);
        this.in = in;
        this.incomplete = incomplete;
    }

    public String toJSON(HttpServletRequest req) {
        JsonObject obj = new JsonObject();

        Enumeration<String> h = req.getHeaderNames();
        while (h.hasMoreElements()) {
            String hKey = h.nextElement();
            String hValue = req.getHeader(hKey);
            obj.addProperty("request_header_"+hKey, hValue);
        }

        obj.addProperty("exception_message", this.getMessage());
        obj.addProperty("request_query_string",req.getQueryString());
        obj.addProperty("request_uri",req.getRequestURI());
        obj.addProperty("request_remote_addr",req.getRemoteAddr());
        obj.addProperty("request_remote_port",req.getRemotePort());
        obj.addProperty("request_remote_host",req.getRemoteHost());
        obj.addProperty("request_remote_user" ,req.getRemoteUser());

        // read the whole remaining body and put the joined base64 encoded message into the json object
        try {
            byte[] ba = IOUtils.toByteArray(this.in);
            byte[] combined = new byte[ba.length + this.incomplete.length];
            System.arraycopy(incomplete, 0, combined,0, this.incomplete.length);
            System.arraycopy(ba, 0, combined, this.incomplete.length, ba.length);
            obj.addProperty("request_body", Base64.getEncoder().encodeToString(combined));
        } catch (Exception ex){
            obj.addProperty("read_body", "failed");
        } finally {
            obj.addProperty("read_body", "success");
        }

        return obj.toString();
    }
}
