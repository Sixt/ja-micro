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

import com.google.inject.Inject;
import com.sixt.service.framework.jetty.JsonHandler;
import com.sixt.service.framework.jetty.ProtobufHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.sixt.service.framework.jetty.RpcServlet.TYPE_OCTET;
import static com.sixt.service.framework.jetty.RpcServlet.TYPE_PROTO;

public class MessageHandler implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(MessageHandler.class);

    protected ServerSocket serverSocket;
    protected Thread thread;
    protected JsonHandler jsonRpcHandler;
    protected ProtobufHandler protobufHandler;
    protected Executor threadPool;
    protected String serviceName;
    protected AtomicBoolean shutdownNow = new AtomicBoolean(false);

    @Inject
    public MessageHandler(ServerSocket socket, JsonHandler jsonRpcHandler,
                          ProtobufHandler protobufHandler) {
        this.serverSocket = socket;
        this.jsonRpcHandler = jsonRpcHandler;
        this.protobufHandler = protobufHandler;
        threadPool = Executors.newCachedThreadPool();
    }

    public void start() {
        thread = new Thread(this);
        thread.start();
    }

    public void shutdown() {
        shutdownNow.set(true);
        try {
            serverSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        logger.info("Starting message handler for {} on port {}", serviceName, serverSocket.getLocalPort());
        while (true) {
            try {
                Socket socket = serverSocket.accept();
                threadPool.execute(() -> {
                    try {
                        processRequest(socket);
                        socket.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });
            } catch (SocketException sockEx) {
                if (shutdownNow.get()) {
                    break;
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void processRequest(Socket socket) throws IOException {
        MockHttpServletRequest request = new MockHttpServletRequest(socket);
        MockHttpServletResponse response = new MockHttpServletResponse(socket);
        try {
            String contentType = request.getContentType();
            if (isProtobuf(contentType)) {
                protobufHandler.doPost(request, response);
            } else {
                jsonRpcHandler.doPost(request, response);
            }
            response.complete();
        } catch (SocketException sockEx) {
            logger.warn("Caught SocketException processing request");
        } catch (Exception ex) {
            logger.error("Uncaught exception handling POST", ex);
        }
    }

    private boolean isProtobuf(String ctype) {
        if (ctype == null) {
            logger.warn("Content-type was null");
            return false;
        }
        return ctype.startsWith(TYPE_PROTO) || ctype.startsWith(TYPE_OCTET);
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }
}
