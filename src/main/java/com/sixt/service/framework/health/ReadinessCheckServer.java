package com.sixt.service.framework.health;

import com.sixt.service.framework.FeatureFlags;
import com.sixt.service.framework.ServiceProperties;
import fi.iki.elonen.NanoHTTPD;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

@Singleton
public class ReadinessCheckServer {

    private static final Logger logger = LoggerFactory.getLogger(ReadinessCheckServer.class);

    private final AtomicBoolean isReady = new AtomicBoolean(false);
    private final ServiceProperties serviceProps;
    private NanoReadinessServer server;

    @Inject
    public ReadinessCheckServer(ServiceProperties serviceProperties) {
        this.serviceProps = serviceProperties;
    }

    public synchronized void serveRequests() {
        boolean wasStarted = isReady.getAndSet(true);
        if (! wasStarted) {
            int port = FeatureFlags.getReadinessCheckPort(serviceProps);
            if (port > 0) {
                server = new NanoReadinessServer(port);
                try {
                    server.start(NanoHTTPD.SOCKET_READ_TIMEOUT, true);
                } catch (IOException e) {
                    logger.warn("Error starting readiness check server", e);
                }
            }
        }
    }

    private class NanoReadinessServer extends NanoHTTPD {

        public NanoReadinessServer(int port) {
            super(port);
        }

        @Override
        public Response serve(IHTTPSession session) {
            Response.Status statusCode = ReadinessCheckServer.this.isReady.get() ?
                    Response.Status.OK : Response.Status.SERVICE_UNAVAILABLE;
            return newFixedLengthResponse(statusCode, "text/plain", "");
        }

    }
}
