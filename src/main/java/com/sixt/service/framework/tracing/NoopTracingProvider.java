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

package com.sixt.service.framework.tracing;

import com.sixt.service.framework.annotation.TracingPlugin;
import io.opentracing.Tracer;
import io.opentracing.noop.NoopTracerFactory;

/**
 * This is the default tracing plugin which just uses the OpenTracing Noop implementation.
 * That way, tracing can be effectively disabled.
 */
@TracingPlugin(name = "noop")
public class NoopTracingProvider implements TracingProvider {

    @Override
    public Tracer getTracer() {
        return NoopTracerFactory.create();
    }

}
