package com.squareup.wire.schema.internal.parser;

import com.sixt.service.testrpcclasses.TestService1;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class RpcMethodScannerTest {

    private RpcMethodScanner scanner = new RpcMethodScanner(null);

    @Test
    public void verifyProtoFilePackageMatches() {
        String[] packageTokens = { "com", "sixt", "service", "foo" };
        assertThat(scanner.protoFilePackageMatches("com.sixt.service.api.foo.Blah", packageTokens)).isTrue();
        assertThat(scanner.protoFilePackageMatches("com.sixt.service.foo.api.Blah", packageTokens)).isTrue();
        assertThat(scanner.protoFilePackageMatches("com.sixt.service.bar.api.Blah", packageTokens)).isFalse();
    }

    @Test
    public void findProtobufClass() throws Exception {

        List<String> protoClasses = Arrays.asList(
                "com.sixt.service.testrpcclasses.TestService1$TestServiceRequest"
        );

        RpcMethodDefinition def = new RpcMethodDefinition("MyService",
                "TestServiceRequest", "TestServiceResponse",
                "com.sixt.service.testrpcclasses", "test-service.proto");

        Class<?> clazz = scanner.findProtobufClass(protoClasses, def, "TestServiceRequest");
        assertThat(clazz).isEqualTo(TestService1.TestServiceRequest.class);
    }

    @Test
    public void findProtobufClass_ambiguityResolvedViaPackageName() throws Exception {

        List<String> protoClasses = Arrays.asList(
                "com.sixt.service.testrpcclasses.package1.TestService$TestServiceRequest",
                "com.sixt.service.testrpcclasses.package2.TestService$TestServiceRequest"
        );

        RpcMethodDefinition def = new RpcMethodDefinition("MyService",
                "TestServiceRequest", "TestServiceResponse",
                "com.sixt.service.testrpcclasses.package2", "test-service.proto");

        Class<?> clazz = scanner.findProtobufClass(protoClasses, def, "TestServiceRequest");
        assertThat(clazz).isEqualTo(com.sixt.service.testrpcclasses.package2.TestService.TestServiceRequest.class);

    }

    @Test
    public void findProtobufClass_ambiguityResolvedViaFileName() throws Exception {

        List<String> protoClasses = Arrays.asList(
                "com.sixt.service.testrpcclasses.TestService1$TestServiceRequest",
                "com.sixt.service.testrpcclasses.TestService2$TestServiceRequest"
        );

        RpcMethodDefinition def = new RpcMethodDefinition("MyService",
                "TestServiceRequest", "TestServiceResponse",
                "com.sixt.service.testrpcclasses", "test-service-2.proto");

        Class<?> clazz;

        clazz = scanner.findProtobufClass(protoClasses, def, "TestServiceRequest");
        assertThat(clazz).isEqualTo(com.sixt.service.testrpcclasses.TestService2.TestServiceRequest.class);

    }

    @Test(expected = RpcMethodScanner.AmbiguousTypeException.class)
    public void findProtobufClass_ambiguityNotResolvable() throws Exception {

        List<String> protoClasses = Arrays.asList(
                "com.sixt.service.testrpcclasses.TestService1$TestServiceRequest",
                "com.sixt.service.testrpcclasses.TestService2$TestServiceRequest"
        );

        RpcMethodDefinition def = new RpcMethodDefinition("MyService",
                "TestServiceRequest", "TestServiceResponse",
                "com.sixt.service.testrpcclasses", "services.proto");

        scanner.findProtobufClass(protoClasses, def, "TestServiceRequest");
    }

    @Test
    public void verifyGetGeneratedProtoClasses() {
        List<String> protoClasses = scanner.getGeneratedProtoClasses("com.sixt.service.configuration");
        assertThat(protoClasses.size()).isGreaterThan(6);
    }

}
