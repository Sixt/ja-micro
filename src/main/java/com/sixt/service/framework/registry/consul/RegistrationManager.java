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

import com.google.gson.*;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.protobuf.Descriptors;
import com.google.protobuf.Message;
import com.sixt.service.framework.ServiceMethodHandler;
import com.sixt.service.framework.ServiceProperties;
import com.sixt.service.framework.util.Sleeper;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.util.StringContentProvider;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpMethod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Constructor;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.net.SocketException;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.Deflater;

import static com.sixt.service.framework.FeatureFlags.DEFAULT_HEALTH_CHECK_POLL_INTERVAL;
import static com.sixt.service.framework.util.ReflectionUtil.findSubClassParameterType;

//TODO: refactor this class so it can be shared across registry plugins

//TODO: make sure state change information is correctly logged

@Singleton
public class RegistrationManager implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(RegistrationManager.class);

    private HttpClient httpClient;
    private ServiceProperties serviceProps;
    private Map<String, ServiceMethodHandler<? extends Message, ? extends Message>> registeredHandlers;
    private AtomicBoolean isRegistered = new AtomicBoolean(false);
    private AtomicBoolean isShutdownHookRegistered = new AtomicBoolean(false);
    private String serviceId;
    private String serviceName;
    private String ipAddress;
    private String unregisterString;
    private JsonObject registrationJsonObject = null;
    private ScheduledExecutorService executorService;
    private Sleeper sleeper = new Sleeper();

    @Inject
    public RegistrationManager(ServiceProperties serviceProps,
                               HttpClient httpClient) {
        this.serviceProps = serviceProps;
        this.httpClient = httpClient;
        serviceId = serviceProps.getServiceInstanceId();
        serviceName = serviceProps.getServiceName();
    }

    public void setRegisteredHandlers(Map<String, ServiceMethodHandler<? extends Message,
            ? extends Message>> registeredHandlers) {
        this.registeredHandlers = registeredHandlers;
    }

    public void register() {
        executorService = Executors.newScheduledThreadPool(1);
        executorService.scheduleAtFixedRate(this, 0, DEFAULT_HEALTH_CHECK_POLL_INTERVAL, TimeUnit.SECONDS);
    }

    public boolean isRegistered() {
        isRegistered.set(verifyRegistrationInConsul());
        return isRegistered.get();
    }

    @Override
    public void run() {
        if (isRegistered()) {
            return;
        }

        long sleepDuration = 1000;
        while (! isRegistered.get()) {
            try {
                attemptRegistration();
                if (isRegistered.get()) {
                    break;
                }
                sleeper.sleepNoException(sleepDuration);
                sleepDuration = (long) (sleepDuration * 1.5);
            } catch (Exception ex) {
                logger.warn("Caught exception attempting service registration", ex);
            } catch (Throwable t) {
                logger.error("Caught throwable attempting service registration", t);
                throw t;
            }
        }
    }

    public void shutdown() {
        logger.info("Shutting down {}", serviceName);
        executorService.shutdown();
        try {
            executorService.awaitTermination(1, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            logger.warn("Timed out waiting for worker thread termination");
        }
        unregisterService();
    }

    private void attemptRegistration() {
        updateIpAddress();
        JsonObject request = null;
        try {
            request = buildJsonRequest();
        } catch (IOException e) {
            logger.warn("Error building registration json", e);
        }
        logger.info("Attempting service registration of {}", serviceName);
        boolean success = sendRegistration(request);
        isRegistered.set(success);
        if (success) {
            logger.info("Registration of {} successful", serviceName);
            registerShutdownHook();
        }
    }

    private synchronized void registerShutdownHook() {
        if (! isShutdownHookRegistered.get()) {
            logger.info("Registering shutdown hook for {}", serviceName);
            //low-level http to make fast as possible
            unregisterString = "GET /v1/agent/service/deregister/" + serviceId + " HTTP/1.0\r\n\r\n";
            Runtime.getRuntime().addShutdownHook(new Thread(this::unregisterService));
        }
        isShutdownHookRegistered.set(true);
    }

    private boolean verifyRegistrationInConsul() {
        String registryServer = serviceProps.getRegistryServer();
        if (StringUtils.isBlank(registryServer)) {
            return false;
        }
        String url = "http://" + registryServer + "/v1/catalog/service/" +
                serviceProps.getServiceName();
        try {
            ContentResponse httpResponse = httpClient.newRequest(url).
                    method(HttpMethod.GET).header(HttpHeader.CONTENT_TYPE, "application/json").send();
            if (httpResponse.getStatus() != 200) {
                return false;
            }
            JsonArray pods = new JsonParser().parse(httpResponse.getContentAsString()).getAsJsonArray();
            Iterator<JsonElement> iter = pods.iterator();
            while (iter.hasNext()) {
                JsonElement pod = iter.next();
                String serviceId = pod.getAsJsonObject().get("ServiceID").getAsString();
                if (serviceProps.getServiceInstanceId().equals(serviceId)) {
                    return true;
                }
            }
        } catch (Exception ex) {
            logger.warn("Caught exception verifying registration", ex);
        }
        return false;
    }

    protected void unregisterService() {
        if (! isRegistered.get()) {
            return;
        }
        try {
            logger.info("Unregistering {}", serviceName);
            String registryServer = serviceProps.getRegistryServer();
            int colon = registryServer.indexOf(':');
            String hostname = registryServer.substring(0, colon);
            int port = Integer.parseInt(registryServer.substring(colon + 1));
            Socket sock = new Socket(hostname, port);
            OutputStream out = sock.getOutputStream();
            out.write(unregisterString.getBytes());
            out.flush();
            sock.close();

            if (isShutdownHookRegistered.get()) {
                isShutdownHookRegistered.set(false);
            }
        } catch (Exception ex) {
            logger.error("Error unregistering from consul", ex);
        }
    }

    private boolean sendRegistration(JsonObject request) {
        try {
            ContentResponse httpResponse = httpClient.newRequest(getRegistrationUri()).
                    content(new StringContentProvider(request.toString())).
                    method(HttpMethod.PUT).header(HttpHeader.CONTENT_TYPE, "application/json").send();
            if (httpResponse.getStatus() == 200) {
                return true;
            }
        } catch (Exception ex) {
            logger.warn("Caught exception sending registration {}", request.toString(), ex);
        }
        return false;
    }

    private String getRegistrationUri() {
        return "http://" + serviceProps.getRegistryServer() + "/v1/agent/service/register";
    }

    protected JsonObject buildJsonRequest() throws IOException {
        if (registrationJsonObject == null) {
            JsonObject json = new JsonObject();
            json.addProperty("ID", serviceId);
            json.addProperty("Name", serviceName);
            json.add("Tags", getRegistrationTags());
            json.addProperty("Address", ipAddress);
            json.addProperty("Port", serviceProps.getServicePort());
            JsonObject ttlElement = new JsonObject();
            ttlElement.addProperty("TTL", (DEFAULT_HEALTH_CHECK_POLL_INTERVAL + 2) + "s");
            ttlElement.addProperty("DeregisterCriticalServiceAfter", "5m");
            json.add("Check", ttlElement);

            registrationJsonObject = json;
        }
        return registrationJsonObject;
    }

    private void updateIpAddress() {
        try {
            Enumeration<NetworkInterface> b = NetworkInterface.getNetworkInterfaces();
            ipAddress = null;
            while( b.hasMoreElements()){
                NetworkInterface iface = b.nextElement();
                if (iface.getName().startsWith("dock")) {
                    continue;
                }
                for ( InterfaceAddress f : iface.getInterfaceAddresses()) {
                    if (f.getAddress().isSiteLocalAddress()) {
                        ipAddress = f.getAddress().getHostAddress();
                    }
                }
            }
        } catch (SocketException e) {
            e.printStackTrace();
        }
    }

    private JsonArray getRegistrationTags() throws IOException {
        JsonArray retval = new JsonArray();
        String tag = "{\"type\":\"" + getShortServiceName() + "\"}";
        retval.add(new JsonPrimitive("t-" + binaryEncode(tag)));
        tag = "{\"transport\":\"http\"}";
        retval.add(new JsonPrimitive("t-" + binaryEncode(tag)));
        tag = "{\"broker\":\"http\"}";
        retval.add(new JsonPrimitive("t-" + binaryEncode(tag)));
        tag = "{\"server\":\"rpc\"}";
        retval.add(new JsonPrimitive("t-" + binaryEncode(tag)));
        tag = "{\"registry\":\"consul\"}";
        retval.add(new JsonPrimitive("t-" + binaryEncode(tag)));
        addEndpointsTags(retval);
        tag = getServiceVersion();
        retval.add(new JsonPrimitive("v-" + binaryEncode(tag)));
        return retval;
    }

    private String binaryEncode(String tag) {
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            Deflater deflater = new Deflater();
            deflater.setInput(tag.getBytes());
            deflater.finish();
            byte[] buffer = new byte[1024];
            while (!deflater.finished()) {
                int count = deflater.deflate(buffer);
                outputStream.write(buffer, 0, count);
            }
            outputStream.close();
            byte compressed[] = outputStream.toByteArray();
            return bytesToHex(compressed);
        } catch (Exception ex) {
            logger.warn("Caught exception", ex);
            return "";
        }
    }

    final protected static char[] hexArray = "0123456789abcdef".toCharArray();
    public static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for ( int j = 0; j < bytes.length; j++ ) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }

    @SuppressWarnings("unchecked")
    private void addEndpointsTags(JsonArray tagsArray) throws IOException {
        if (registeredHandlers == null || registeredHandlers.isEmpty()) {
            return;
        }
        TreeSet<String> handlerNamesSorted = new TreeSet<>(registeredHandlers.keySet());
        handlerNamesSorted.forEach(key -> {
            StringBuilder sb = new StringBuilder();
            sb.append("{\"name\":\"");
            sb.append(key);
            sb.append("\",\"request\":{\"name\":\"");
            try {
                ServiceMethodHandler handler = registeredHandlers.get(key);
                Class<? extends Message> requestClass = (Class<? extends Message>)
                        findSubClassParameterType(handler, 0);
                Class<? extends Message> responseClass = (Class<? extends Message>)
                        findSubClassParameterType(handler, 1);
                sb.append(requestClass.getSimpleName());
                sb.append("\",\"type\":\"");
                sb.append(requestClass.getSimpleName());
                sb.append("\",\"values\":[");
                sb.append(getProtobufClassFieldDescriptions(requestClass, new HashSet<>()));
                sb.append("]},\"response\":{\"name\":\"");
                sb.append(responseClass.getSimpleName());
                sb.append("\",\"type\":\"");
                sb.append(responseClass.getSimpleName());
                sb.append("\",\"values\":[");
                sb.append(getProtobufClassFieldDescriptions(responseClass, new HashSet<>()));
                sb.append("]},\"metadata\":{\"stream\":\"false\"}}");
            } catch (Exception e) {
                logger.error("Error inspecting handlers", e);
                return;
            }
            String tag = sb.toString();
            tagsArray.add(new JsonPrimitive("e-" + binaryEncode(tag)));
        });
    }

    protected String getProtobufClassFieldDescriptions(Class<? extends Message> messageClass, Set<Class<? extends Message>> visited)
            throws Exception {

        if (visited.contains(messageClass)) {
            return "";
        }
        visited.add(messageClass);

        StringBuilder sb = new StringBuilder();
        Constructor<?> constructor = null;
        try {
            constructor = messageClass.getDeclaredConstructor();
        } catch (NoSuchMethodException nsmex) {
            //Issue #35
            logger.info("Unsupported protobuf field: {}", messageClass.getName());
            return sb.toString();
        }
        constructor.setAccessible(true);
        Object instance = constructor.newInstance();
        Message.Builder builder = ((Message)instance).newBuilderForType();
        Message message = builder.build();

        Descriptors.Descriptor requestDesc = message.getDescriptorForType();
        List<Descriptors.FieldDescriptor> requestFields = requestDesc.getFields();
        Iterator<Descriptors.FieldDescriptor> iter = requestFields.iterator();
        while (iter.hasNext()) {
            Descriptors.FieldDescriptor fd = iter.next();
            //TODO: deal with repeated fields
            sb.append("{\"name\":\"");
            sb.append(fd.getName());
            sb.append("\",\"type\":\"");
            if (fd.getType().toString().equalsIgnoreCase("message")) {
                sb.append(getLastComponent(fd.getMessageType().getFullName()));
                sb.append("\",\"values\":[");
                Descriptors.FieldDescriptor childDescriptor = requestDesc.findFieldByName(fd.getName());
                Message.Builder subMessageBuilder = builder.newBuilderForField(childDescriptor);
                Message subMessage = subMessageBuilder.build();
                sb.append(getProtobufClassFieldDescriptions(subMessage.getClass(), visited));
                sb.append("]}");
            } else {
                sb.append(fd.getType().toString().toLowerCase());
                sb.append("\",\"values\":null}");
            }
            if (iter.hasNext()) {
                sb.append(",");
            }
        }
        return sb.toString();
    }

    private String getLastComponent(String fullName) {
        if (! StringUtils.contains(fullName, ".")) {
            return fullName;
        }
        int index = fullName.lastIndexOf('.');
        return fullName.substring(index + 1);
    }

    private String getShortServiceName() {
        String name = serviceName;
        if (StringUtils.isBlank(name)) {
            return "unknown";
        }
        if (name.contains(".")) {
            name = name.substring(name.lastIndexOf('.') + 1);
        }
        return name;
    }

    private String getServiceVersion() {
        String version = serviceProps.getServiceVersion();
        if (StringUtils.isBlank(version)) {
            return "unknown";
        }
        return version;
    }

}
