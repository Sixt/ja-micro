package com.sixt.service.framework.servicetest.mockservice;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ImpersonatedPortDictionaryTest {

    private ImpersonatedPortDictionary dictionary = new ImpersonatedPortDictionary();

    @Test
    public void testDictionary() {
        assertThat(dictionary.internalPortForImpersonated("a")).isEqualTo(42000);
        assertThat(dictionary.internalPortForImpersonated("b")).isEqualTo(42001);
        assertThat(dictionary.internalPortForImpersonated("a")).isEqualTo(42000);
        assertThat(dictionary.internalPortForImpersonated("c")).isEqualTo(42002);
    }

}
