package com.squareup.wire.schema.internal.parser;

import org.junit.Test;

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

}
