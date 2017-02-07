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

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

@Singleton
public class RpcServlet extends HttpServlet {

    private static final Logger logger = LoggerFactory.getLogger(RpcServlet.class);

    public final static String CONTENT_TYPE = "Content-Type";
    public final static String TYPE_JSON = "application/json";
    public final static String TYPE_PROTO = "application/proto";
    public final static String TYPE_OCTET = "application/octet-stream";

    protected JsonHandler jsonRpcHandler;
    protected ProtobufHandler protobufHandler;
    protected AtomicBoolean canServeRequests = new AtomicBoolean(false);

    @Inject
    public RpcServlet(JsonHandler jsonHandler, ProtobufHandler protobufHandler) {
        this.jsonRpcHandler = jsonHandler;
        this.protobufHandler = protobufHandler;
    }

    public void serveRequests() {
        canServeRequests.set(true);
    }

    @Override
    protected void doHead(HttpServletRequest req,
                          HttpServletResponse resp) throws ServletException, IOException {
        //return a 200, we are alive
        //HEAD should do the same as GET, set all headers, but not provide the content
    }

    @Override
    protected void doGet(HttpServletRequest req,
                         HttpServletResponse resp) throws ServletException, IOException {
        //What to do with GETs?
    }

    @Override
    protected void doPost(HttpServletRequest req,
                          HttpServletResponse resp) throws ServletException, IOException {
        if (! canServeRequests.get()) {
            logger.info("Not processing request, service not ready yet");
            resp.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            return;
        }
        try {
            //TODO: refactor and clean up.  we should use codecs to abstract the encoding/decoding so that
            //      there is one handler class without a bunch of duplication
            String contentType = req.getHeader(CONTENT_TYPE);
            logger.debug("Request content-type: {}", contentType);
            if (isProtobuf(contentType)) {
                protobufHandler.doPost(req, resp);
            } else {
                jsonRpcHandler.doPost(req, resp);
            }
        } catch (Exception ex) {
            logger.error("Uncaught exception handling POST", ex);
        }
    }

    private boolean isProtobuf(String ctype) {
        if (ctype == null) {
            return false;
        }
        return ctype.startsWith(TYPE_PROTO) || ctype.startsWith(TYPE_OCTET);
    }

}
