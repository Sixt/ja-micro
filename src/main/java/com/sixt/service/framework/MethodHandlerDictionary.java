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

package com.sixt.service.framework;

import com.google.protobuf.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class MethodHandlerDictionary  {

    private static final Logger logger = LoggerFactory.getLogger(MethodHandlerDictionary.class);

    public static final String GLOBAL = "*";

    private Map<String, ServiceMethodHandler<? extends Message, ? extends Message>> methodHandlers;
    private List<ServiceMethodPreHook<? extends Message>> globalPreHooks;
    private List<ServiceMethodPostHook<? extends Message>> globalPostHooks;
    private Map<String, List<ServiceMethodPreHook<? extends Message>>> methodPreHooks;
    private Map<String, List<ServiceMethodPostHook<? extends Message>>> methodPostHooks;

    public MethodHandlerDictionary() {
        this.methodHandlers = new HashMap<>();
        this.globalPreHooks = new ArrayList<>();
        this.globalPostHooks = new ArrayList<>();
        this.methodPreHooks = new HashMap<>();
        this.methodPostHooks = new HashMap<>();
    }

    /**
     * Add a hook to inspect or modify incoming requests.  Invoked in the same order as configured at runtime
     * @param endpoint Use MethodHandlerDictionary.GLOBAL to apply to all handlers, or the RPC method full-name
     * @param handlerClass ServiceMethodPreHook instance
     */
    public void addPreHook(String endpoint, ServiceMethodPreHook<? extends Message> handlerClass) {
        if (GLOBAL.equals(endpoint)) {
            globalPreHooks.add(handlerClass);
        } else {
            List<ServiceMethodPreHook<? extends Message>> hooks = methodPreHooks.get(endpoint);
            if (hooks == null) {
                hooks = new ArrayList<>();
                methodPreHooks.put(endpoint, hooks);
            }
            hooks.add(handlerClass);
        }
    }

    /**
     * Add a hook to inspect or modify outgoing responses.  Invoked in the same order as configured at runtime
     * @param endpoint Use MethodHandlerDictionary.GLOBAL to apply to all handlers, or the RPC method full-name
     * @param handlerClass ServiceMethodPostHook instance
     */
    public void addPostHook(String endpoint, ServiceMethodPostHook<? extends Message> handlerClass) {
        if (GLOBAL.equals(endpoint)) {
            globalPostHooks.add(handlerClass);
        } else {
            List<ServiceMethodPostHook<? extends Message>> hooks = methodPostHooks.get(endpoint);
            if (hooks == null) {
                hooks = new ArrayList<>();
                methodPostHooks.put(endpoint, hooks);
            }
            hooks.add(handlerClass);
        }
    }

    public void put(String endpoint, ServiceMethodHandler<? extends Message, ? extends Message> instance) {
        ServiceMethodHandler existing = methodHandlers.get(endpoint);
        if (existing != null) {
            logger.warn("Overwriting ServiceMethodHandler registration for {}", endpoint);
        }
        methodHandlers.put(endpoint, instance);
    }

    public Map<String, ServiceMethodHandler<? extends Message, ? extends Message>> getMethodHandlers() {
        return methodHandlers;
    }

    public ServiceMethodHandler<? extends Message, ? extends Message> getMethodHandler(String endpoint) {
        return methodHandlers.get(endpoint);
    }

    /**
     * For backward-compatibility
     */
    @Deprecated
    public ServiceMethodHandler<? extends Message, ? extends Message> get(String endpoint) {
        return getMethodHandler(endpoint);
    }

    public List<ServiceMethodPreHook<? extends Message>> getPreHooksFor(String methodName) {
        List<ServiceMethodPreHook<? extends Message>> retval = new ArrayList<>(globalPreHooks);
        List<ServiceMethodPreHook<? extends Message>> methodHooks = methodPreHooks.get(methodName);
        if (methodHooks != null) {
            retval.addAll(methodHooks);
        }
        return Collections.unmodifiableList(retval);
    }

    public List<ServiceMethodPostHook<? extends Message>> getPostHooksFor(String methodName) {
        List<ServiceMethodPostHook<? extends Message>> hooks = methodPostHooks.get(methodName);
        List<ServiceMethodPostHook<? extends Message>> retval = new ArrayList<>();
        if (hooks != null) {
            retval.addAll(hooks);
        }
        if (! globalPostHooks.isEmpty()) {
            retval.addAll(globalPostHooks);
        }
        return Collections.unmodifiableList(retval);
    }

    public boolean hasMethodHandler(String method) {
        return methodHandlers.containsKey(method);
    }
}
