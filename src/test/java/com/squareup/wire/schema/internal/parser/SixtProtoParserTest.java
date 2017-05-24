package com.squareup.wire.schema.internal.parser;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;

import java.io.File;

public class SixtProtoParserTest {

    private final PackageMatcherTestParser testParser = new PackageMatcherTestParser("com.sixt.service.payment");

    @Test
    public void positive_case() {
        testParser.should_match("com.sixt.service.payment.com");
        testParser.should_match("com.sixt.service.payment.proto");
        testParser.should_match("com.sixt.service.payment.API.proto");
    }

    @Test
    public void negative_case() {
        testParser.should_not_match("com.sixt.service.payment_authorization");
        testParser.should_not_match("org.sixt.service.payment");
        testParser.should_not_match("com.test.service.payment");
    }

    class PackageMatcherTestParser
        extends SixtProtoParser {

        PackageMatcherTestParser(final String service) {
            super(new File("test.txt"), service);
        }

        void should_match(String pack) {
            assertThat(matchesServiceName(pack)).isTrue()
                .as(String.format("%s should match with %s", serviceName, pack));
        }

        void should_not_match(String pack) {
            assertThat(matchesServiceName(pack)).isFalse()
                .as(String.format("%s should not match with %s", serviceName, pack));
        }
    }
}