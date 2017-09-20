package com.sixt.service.framework.util;

import static org.assertj.core.api.Assertions.assertThat;

import com.sixt.service.framework.OrangeContext;
import com.sixt.service.framework.ServiceMethodHandler;
import com.sixt.service.framework.protobuf.RpcEnvelope;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.matcher.Matchers;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.junit.Test;

public class TestHandlerInterception {

    private static boolean beforeExecuted;
    private static boolean afterExecuted;
    private static boolean methodExecuted;

    @Test
    public void test_interceptor() throws ClassNotFoundException {
        Injector injector = Guice.createInjector((Module) binder -> binder.bindInterceptor(
            Matchers.identicalTo(TestedRpcHandler.class),
            Matchers.any(),
            new RpcHandlerMethodInterceptor()
        ));

        TestedRpcHandler methodHandler
            = injector.getInstance(TestedRpcHandler.class);
        methodHandler.handleRequest(RpcEnvelope.Request.newBuilder().build(), new OrangeContext());

        SameGuiceModuleAssertionOnInterceptorInvokation sameGuiceModuleAssertionOnInterceptorInvokation
            = injector.getInstance(SameGuiceModuleAssertionOnInterceptorInvokation.class);
        sameGuiceModuleAssertionOnInterceptorInvokation.test_that_intercepted_rpc_handler_still_verified();

        assertThat(ReflectionUtil.findSubClassParameterType(methodHandler, 0)).
            isEqualTo(RpcEnvelope.Request.class);
        assertThat(ReflectionUtil.findSubClassParameterType(methodHandler, 1)).
            isEqualTo(RpcEnvelope.Response.class);
    }

    public class RpcHandlerMethodInterceptor
        implements MethodInterceptor {

        @Override
        public Object invoke(final MethodInvocation invocation) throws Throwable {
            beforeExecuted = true;
            Object result = invocation.proceed();
            afterExecuted = true;

            return result;
        }
    }

    static class TestedRpcHandler
        implements ServiceMethodHandler<RpcEnvelope.Request, RpcEnvelope.Response> {

        @Override
        public RpcEnvelope.Response handleRequest(RpcEnvelope.Request request, OrangeContext ctx) {
            methodExecuted = true;
            return RpcEnvelope.Response.getDefaultInstance();
        }
    }

    static class SameGuiceModuleAssertionOnInterceptorInvokation {

        void test_that_intercepted_rpc_handler_still_verified() {
            assertThat(beforeExecuted).isTrue().as("Interceptor should be invoked before method.");
            assertThat(methodExecuted).isTrue().as("Intercepted method should be executed.");
            assertThat(afterExecuted).isTrue().as("Interceptor should be invoked after method.");
        }
    }
}
