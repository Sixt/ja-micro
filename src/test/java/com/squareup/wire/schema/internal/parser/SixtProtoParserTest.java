package com.squareup.wire.schema.internal.parser;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;

import java.io.File;

public class SixtProtoParserTest {

    private PackageMatcherTestParser testParser;

    @Test
    public void positive_case() {
        testParser = new PackageMatcherTestParser("com.sixt.service.payment");
        testParser.should_match("com.sixt.service.payment");
        testParser.should_match("com.sixt.service.payment.API");

        testParser = new PackageMatcherTestParser("com.sixt.service.rating");
        testParser.should_match("com.sixt.service.rating");
        testParser.should_match("com.sixt.service.rating.api");

        testParser = new PackageMatcherTestParser("com.sixt.service.accounting");
        testParser.should_match("com.sixt.service.accounting.api");
        testParser.should_match("com.sixt.service.accounting");
    }

    @Test
    public void negative_case() {
        testParser = new PackageMatcherTestParser("com.sixt.service.payment");
        testParser.should_not_match("com.sixt.service.payment_authorization");

        testParser = new PackageMatcherTestParser("com.sixt.service.rating");
        testParser.should_not_match("org.sixt.service.rating_payment");

        testParser = new PackageMatcherTestParser("com.sixt.service.accounting");
        testParser.should_not_match("com.test.service.accounting_payment");
    }

    class PackageMatcherTestParser
        extends SixtProtoParser {

        PackageMatcherTestParser(final String service) {
            super("test.txt", new File("test.txt"), service);
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